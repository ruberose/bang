package com.kukuku.bang

import androidx.compose.ui.Modifier

/**
 * 회피 입력 한 건.
 *
 * @property direction 피하려는 방향
 * @property timeNanos 입력이 일어난 순간의 System.nanoTime() 값.
 *   게임 루프의 프레임 시각과 같은 시계 기준이라, 화면 쪽에서 그대로 게임 시각(초)으로 바꿀 수 있다.
 *   입력 구현체가 "감지한 바로 그 순간"을 찍어서 넘겨야 판정이 정확하다.
 */
data class DodgeEvent(val direction: DodgeDirection, val timeNanos: Long)

/**
 * 회피 입력 방식의 공통 인터페이스.
 *
 * 왜 인터페이스로 분리하나?
 * - 지금은 탭([TapDodgeInput])이지만, 나중에 얼굴 기울이기 같은 다른 입력을 "같은 자리"에 끼우기 위해서다.
 * - 게임 화면은 "어떤 방향으로, 언제 입력됐는지"만 받으면 되고, 그 입력이 어떻게 만들어졌는지는 몰라도 된다.
 * - 엔진([GameEngine])은 이 인터페이스조차 모른다. 화면이 [DodgeEvent]를 받아서
 *   엔진의 onDodge(방향, 시각)를 부를 뿐이므로, 엔진은 계속 Android와 무관하게 유지된다.
 */
interface DodgeInput {

    /**
     * 게임 화면에 붙일 Modifier.
     * 탭처럼 "화면 터치 이벤트"가 필요한 입력은 여기에 터치 처리를 담아 돌려준다.
     * 카메라처럼 화면 터치가 필요 없는 입력은 기본값(빈 Modifier)을 그대로 쓴다.
     */
    val modifier: Modifier get() = Modifier

    /** 화면 하단에 보여줄 조작 안내 문구. 입력 방식마다 조작법이 다르므로 구현체가 정한다. */
    val hint: String

    /**
     * 입력이 생길 때마다 호출될 함수를 등록한다. null을 넣으면 등록 해제.
     * 화면이 사라질 때 해제해야, 사라진 화면으로 입력이 전달되는 일이 없다.
     */
    fun setListener(listener: ((DodgeEvent) -> Unit)?)
}
