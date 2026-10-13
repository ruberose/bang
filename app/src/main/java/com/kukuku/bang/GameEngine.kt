package com.kukuku.bang

import kotlin.math.max
import kotlin.math.pow
import kotlin.random.Random

/**
 * 게임 규칙을 계산하는 엔진. (화면 그리기와 완전히 분리)
 *
 * 설계 포인트:
 * - Android 코드를 전혀 쓰지 않는다 → 일반 JUnit으로 PC에서 바로 테스트할 수 있다.
 * - 현재 시각(now)을 엔진이 직접 읽지 않고 매번 "인자로 받는다".
 *   테스트에서 "1.234초에 탭했다"를 정확히 재현할 수 있게 하기 위해서다.
 * - 랜덤도 생성자로 받는다. 테스트에서 시드를 고정하면 결과가 매번 같아진다.
 * - 상태를 직접 바꾸지 않고, 항상 새 [GameState]를 돌려준다.
 *
 * @param random 대기 시간/방향 랜덤에 쓸 난수 생성기
 * @param dodgeWindowBonus 회피 구간에 더해 줄 여유 시간(초). 입력 지연이 큰 입력 방식(카메라)용.
 *   난이도 계산([dodgeWindow])과 따로 두는 이유: "난이도 규칙"과 "입력 장치 보정"은 별개의 개념이라서.
 */
class GameEngine(
    private val random: Random = Random.Default,
    private val dodgeWindowBonus: Double = 0.0,
) {

    // ─────────────────────────── 판 시작 / 이어하기 ───────────────────────────

    /**
     * 새 판을 시작한다. 연속 기록 0, 이어하기 미사용 상태로 대기 단계부터 시작.
     * @param now 현재 시각(초)
     */
    fun newGame(now: Double): GameState =
        GameState(phase = newWaiting(now, streak = 0))

    /**
     * 이번 판에서 이어하기가 가능한지.
     * 판이 끝난 상태이고, 아직 이어하기를 쓰지 않았을 때만 가능하다 (SPEC 5장: 한 판당 1회).
     */
    fun canContinue(state: GameState): Boolean =
        state.phase is Phase.GameOver && !state.continueUsed

    /**
     * 광고를 본 뒤 같은 연속 기록에서 이어간다.
     * 이어하기가 불가능한 상태면 아무것도 바꾸지 않고 그대로 돌려준다.
     * (예외를 던지지 않는 이유: 버튼 연타 같은 상황에서 앱이 죽지 않게 하려고)
     *
     * @param now 현재 시각(초)
     */
    fun continueGame(state: GameState, now: Double): GameState {
        if (!canContinue(state)) return state
        return state.copy(
            phase = newWaiting(now, state.streak),
            continueUsed = true,
            lastEvent = null,
        )
    }

    // ─────────────────────────── 시간 흐름 ───────────────────────────

    /**
     * 시간이 흐름에 따라 단계를 진행시킨다. 화면이 매 프레임 호출한다.
     *
     * 대기 → 경고 → 발사 → (탭 없이 hitTime을 넘기면) 피격 실패
     *
     * while 반복을 쓰는 이유: 앱이 잠깐 멈췄다가 돌아오면 한 프레임에
     * 여러 단계를 건너뛰어야 할 수 있기 때문이다.
     * 다음 단계의 시작 시각은 now가 아니라 "이전 단계의 종료 시각"으로 정한다.
     * 그래야 프레임 간격과 상관없이 타이밍이 정확하다.
     *
     * @param now 현재 시각(초)
     */
    fun update(state: GameState, now: Double): GameState {
        var s = state
        while (true) {
            val p = s.phase
            s = when {
                // 경고가 시작될 때 총알 방향을 랜덤으로 정한다 (50:50)
                p is Phase.Waiting && now >= p.endTime ->
                    s.copy(
                        phase = Phase.Warning(
                            startTime = p.endTime,
                            endTime = p.endTime + GameConfig.WARNING_DURATION,
                            side = if (random.nextBoolean()) Side.LEFT else Side.RIGHT,
                        )
                    )

                // 경고 때 정한 방향 그대로 발사
                p is Phase.Warning && now >= p.endTime ->
                    s.copy(phase = newFiring(p.endTime, s.streak, p.side))

                // hitTime "정각"은 아직 성공 구간이므로 > 로 비교한다 (구간 끝 포함).
                p is Phase.Firing && now > p.hitTime ->
                    fail(s, FailReason.HIT, p.hitTime)

                else -> return s
            }
        }
    }

    // ─────────────────────────── 회피 입력 / 판정 ───────────────────────────

    /**
     * 총알이 [side]에서 올 때 성공하는 회피 방향. 항상 총알의 반대쪽이다.
     * (왼쪽에서 오면 오른쪽으로, 오른쪽에서 오면 왼쪽으로)
     */
    fun correctDirection(side: Side): DodgeDirection = when (side) {
        Side.LEFT -> DodgeDirection.RIGHT
        Side.RIGHT -> DodgeDirection.LEFT
    }

    /**
     * 플레이어의 회피 입력을 판정한다.
     * 입력이 탭이든 얼굴이든 상관없이, 방향과 시각만 받는다 (입력 방식과 규칙의 분리).
     *
     * 판정 전에 먼저 [update]로 시간을 맞추는 이유:
     * 마지막 프레임 이후 시간이 흘러 이미 다음 단계여야 할 수도 있기 때문이다.
     *
     * 판정 순서 (위에서부터 먼저 걸리는 것):
     * 1. 대기/경고 중 입력 → 너무 빠름 실패 (방향 무관)
     *    (대기 중 입력을 허용하면 연타로 쉽게 넘길 수 있다.)
     * 2. 발사 중이지만 회피 구간 전 → 이른 회피 실패 (방향 무관)
     * 3. 회피 구간 안인데 방향이 틀림 → 방향 오류 실패
     * 4. 회피 구간(hitTime - dodgeWindow ~ hitTime, 양 끝 포함) + 올바른 방향 → 성공
     * - 이미 종료된 판 → 무시
     *
     * 시간을 방향보다 먼저 보는 이유: 구간 밖 입력은 방향이 맞아도 어차피 실패이므로,
     * 실패 이유를 "타이밍"으로 알려주는 편이 플레이어에게 더 도움이 된다.
     *
     * @param direction 입력된 회피 방향
     * @param now 입력한 시각(초)
     */
    fun onDodge(state: GameState, direction: DodgeDirection, now: Double): GameState {
        val s = update(state, now)
        return when (val p = s.phase) {
            is Phase.Waiting, is Phase.Warning -> fail(s, FailReason.TOO_EARLY, now)
            is Phase.Firing -> when {
                now < p.windowStart -> fail(s, FailReason.EARLY_DODGE, now)
                direction != correctDirection(p.side) -> fail(s, FailReason.WRONG_DIRECTION, now)
                else -> dodge(s, p, direction, now)
            }
            is Phase.GameOver -> s
        }
    }

    // ─────────────────────────── 난이도 계산 ───────────────────────────
    // 연속 성공 횟수(streak)만으로 계산한다. 값을 누적해서 저장하지 않으므로
    // 이어하기를 해도 같은 난이도로 정확히 복원된다.

    /**
     * 연속 성공 횟수에 따른 총알 비행 시간(초).
     * 시작값 × 0.97^streak, 단 최소값 아래로 내려가지 않는다.
     */
    fun bulletFlightTime(streak: Int): Double =
        max(
            GameConfig.BULLET_FLIGHT_TIME_MIN,
            GameConfig.BULLET_FLIGHT_TIME_START * GameConfig.BULLET_FLIGHT_TIME_DECAY.pow(streak),
        )

    /**
     * 연속 성공 횟수에 따른 회피 허용 구간(초).
     * 5회 성공마다 0.01초씩 줄고, 최소값 아래로 내려가지 않는다.
     * (streak / 5 는 정수 나눗셈이라 0~4 → 0, 5~9 → 1 ...)
     */
    fun dodgeWindow(streak: Int): Double {
        val steps = streak / GameConfig.DODGE_WINDOW_STEP_EVERY
        return max(
            GameConfig.DODGE_WINDOW_MIN,
            GameConfig.DODGE_WINDOW_START - steps * GameConfig.DODGE_WINDOW_STEP_AMOUNT,
        )
    }

    /**
     * 연속 성공 횟수에 따른 발사 전 대기 시간 범위(초).
     * 20회 이상 성공하면 더 짧은 범위를 쓴다.
     */
    fun waitRange(streak: Int): ClosedFloatingPointRange<Double> =
        if (streak >= GameConfig.WAIT_FAST_FROM_STREAK) GameConfig.WAIT_MIN_LATE..GameConfig.WAIT_MAX_LATE
        else GameConfig.WAIT_MIN_EARLY..GameConfig.WAIT_MAX_EARLY

    /** 연속 성공 횟수에 따른 난이도 단계 이름 (연출용). */
    fun tier(streak: Int): DifficultyTier = when {
        streak >= GameConfig.TIER_LEGEND_FROM -> DifficultyTier.LEGEND
        streak >= GameConfig.TIER_VETERAN_FROM -> DifficultyTier.VETERAN
        streak >= GameConfig.TIER_SKILLED_FROM -> DifficultyTier.SKILLED
        else -> DifficultyTier.BEGINNER
    }

    // ─────────────────────────── 내부 도우미 ───────────────────────────

    /** 랜덤 대기 시간을 뽑아 대기 단계를 만든다. */
    private fun newWaiting(start: Double, streak: Int): Phase.Waiting {
        val range = waitRange(streak)
        // nextDouble()은 0 이상 1 미만 → 범위 안의 임의 값으로 변환
        val duration = range.start + random.nextDouble() * (range.endInclusive - range.start)
        return Phase.Waiting(start, start + duration)
    }

    /** 현재 연속 기록의 난이도로 발사 단계를 만든다. */
    private fun newFiring(fireTime: Double, streak: Int, side: Side): Phase.Firing =
        Phase.Firing(
            fireTime = fireTime,
            hitTime = fireTime + bulletFlightTime(streak),
            // 입력 방식 보정은 여기서만 더한다 (난이도 함수 자체는 SPEC 수치 그대로)
            dodgeWindow = dodgeWindow(streak) + dodgeWindowBonus,
            side = side,
        )

    /** 회피 성공 처리: 연속 기록 +1, "아슬아슬" 여부 계산, 곧바로 다음 라운드 대기. */
    private fun dodge(s: GameState, p: Phase.Firing, direction: DodgeDirection, now: Double): GameState {
        val newStreak = s.streak + 1
        // 회피 구간의 마지막 20% = hitTime 직전 (dodgeWindow × 0.2)초
        val closeCall = now >= p.hitTime - p.dodgeWindow * GameConfig.CLOSE_CALL_RATIO
        return s.copy(
            phase = newWaiting(now, newStreak),
            streak = newStreak,
            lastEvent = GameEvent.Dodged(now, newStreak, closeCall, direction),
        )
    }

    /** 실패 처리: 판 종료. 연속 기록은 그대로 남겨 둔다 (실패 화면 표시 + 이어하기용). */
    private fun fail(s: GameState, reason: FailReason, time: Double): GameState =
        s.copy(
            phase = Phase.GameOver(reason, time),
            lastEvent = GameEvent.Failed(time, reason),
        )
}
