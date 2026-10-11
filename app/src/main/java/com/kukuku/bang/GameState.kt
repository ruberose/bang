package com.kukuku.bang

/**
 * 게임 한 판의 현재 상태를 담는 불변(immutable) 데이터 클래스.
 *
 * 왜 불변인가?
 * - GameEngine은 "이전 상태 + 시간/입력 → 새 상태"를 돌려주기만 한다.
 *   상태를 직접 고치지 않으니 테스트가 쉽고, Compose가 상태 변화를 감지하기도 쉽다.
 *
 * @property phase 지금 어느 단계인지 (대기/경고/발사/종료)
 * @property streak 이번 판의 연속 성공 횟수. 난이도 계산의 기준이 된다.
 * @property continueUsed 이번 판에서 "광고 보고 이어하기"를 이미 썼는지 (한 판 1회 제한)
 * @property lastEvent 가장 최근에 일어난 성공/실패 이벤트. 화면 연출(팝업, 진동)에 사용.
 */
data class GameState(
    val phase: Phase,
    val streak: Int = 0,
    val continueUsed: Boolean = false,
    val lastEvent: GameEvent? = null,
)

/**
 * 한 라운드의 진행 단계.
 *
 * sealed interface를 쓰면 `when`으로 분기할 때 모든 경우를 빠짐없이 처리했는지
 * 컴파일러가 검사해 준다. 단계마다 필요한 시간 정보가 달라서 각자 데이터를 들고 있다.
 * 모든 시간은 게임 시작 기준의 "초"다.
 */
sealed interface Phase {

    /**
     * 총잡이가 서서 기다리는 단계. [endTime]이 되면 경고로 넘어간다.
     * @property startTime 대기 시작 시각
     * @property endTime 대기 종료(= 경고 시작) 시각. 랜덤으로 정해진다.
     */
    data class Waiting(val startTime: Double, val endTime: Double) : Phase

    /**
     * 총구가 번쩍이는 경고 단계. 이때 입력하면 "너무 빠름"으로 실패.
     *
     * 총알이 올 방향([side])은 이 단계가 시작될 때 정해진다.
     * 대기 중이 아니라 경고 때 공개하는 이유: 플레이어가 방향을 미리 알고 기다리면
     * 방향 판단이 너무 쉬워지므로, "경고를 보고 → 방향 판단 → 타이밍 맞추기"를 짧은 시간에 하게 만든다.
     *
     * @property startTime 경고 시작 시각
     * @property endTime 경고 종료(= 발사) 시각
     * @property side 총잡이가 선 쪽 = 총알이 날아오는 쪽
     */
    data class Warning(val startTime: Double, val endTime: Double, val side: Side) : Phase

    /**
     * 총알이 날아가는 단계.
     * 발사 순간의 난이도 값을 여기에 저장해 두는 이유: 날아가는 도중에 값이 바뀌면 안 되기 때문.
     *
     * @property fireTime 발사 시각
     * @property hitTime 총알이 플레이어에 도달하는 시각 (= fireTime + 비행 시간)
     * @property dodgeWindow 이번 총알의 회피 허용 구간 길이(초)
     * @property side 총알이 날아오는 쪽 (경고 단계에서 정해진 값을 그대로 이어받음)
     */
    data class Firing(
        val fireTime: Double,
        val hitTime: Double,
        val dodgeWindow: Double,
        val side: Side,
    ) : Phase {
        /** 회피 구간이 시작되는 시각. 이 시각부터 hitTime까지 탭하면 성공. */
        val windowStart: Double get() = hitTime - dodgeWindow
    }

    /**
     * 판이 끝난 상태. 실패 화면을 보여준다.
     * @property reason 실패 이유
     * @property time 실패한 시각
     */
    data class GameOver(val reason: FailReason, val time: Double) : Phase
}

/**
 * 실패 이유. 실패 화면 문구나 연출을 다르게 할 때 사용한다.
 */
enum class FailReason {
    /** 경고 전(대기 중)이나 경고 중에 탭함 */
    TOO_EARLY,

    /** 총알이 날아오는 중이지만 회피 구간 전에 입력함 (방향과 상관없이) */
    EARLY_DODGE,

    /** 회피 구간 안에서 입력했지만 방향이 틀림 (총알 쪽으로 피함) */
    WRONG_DIRECTION,

    /** 입력하지 않아서(또는 너무 늦게 입력해서) 총알에 맞음 */
    HIT,
}

/**
 * 총잡이가 서 있는 쪽 = 총알이 날아오는 쪽.
 *
 * [DodgeDirection]과 값이 같아 보이지만 일부러 다른 타입으로 둔다.
 * "총알이 오는 쪽"과 "피하는 방향"은 의미가 반대라서, 같은 타입이면
 * 실수로 둘을 비교해도 컴파일러가 잡아주지 못하기 때문이다.
 */
enum class Side { LEFT, RIGHT }

/**
 * 플레이어의 회피 방향 입력. 탭/얼굴 등 어떤 입력 방식이든 결국 이 값으로 바뀌어 엔진에 들어온다.
 */
enum class DodgeDirection { LEFT, RIGHT }

/**
 * 화면 연출을 위한 이벤트. 엔진은 "무슨 일이 있었는지"만 알려주고,
 * 효과음/진동/팝업은 화면 쪽(5단계)에서 이 값을 보고 처리한다.
 */
sealed interface GameEvent {
    /** 이벤트가 일어난 시각(초) */
    val time: Double

    /**
     * 회피 성공.
     * @property streak 성공 직후의 연속 성공 횟수 (팝업에 표시할 숫자)
     * @property closeCall 회피 구간 마지막 20% 안에서 피했는지 ("아슬아슬!")
     * @property direction 피한 방향 (회피 동작 연출에 사용)
     */
    data class Dodged(
        override val time: Double,
        val streak: Int,
        val closeCall: Boolean,
        val direction: DodgeDirection,
    ) : GameEvent

    /** 실패. */
    data class Failed(override val time: Double, val reason: FailReason) : GameEvent
}

/**
 * 난이도 단계 이름 (연출용, SPEC 3장).
 * @property label 화면에 표시할 이름
 */
enum class DifficultyTier(val label: String) {
    BEGINNER("초보"),
    SKILLED("숙련"),
    VETERAN("베테랑"),
    LEGEND("전설"),
}
