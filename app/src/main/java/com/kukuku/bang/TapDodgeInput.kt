package com.kukuku.bang

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * 탭으로 회피 방향을 입력하는 [DodgeInput] 구현체 (임시 입력 방식).
 *
 * - 화면 왼쪽 절반 탭 → 왼쪽으로 회피
 * - 화면 오른쪽 절반 탭 → 오른쪽으로 회피
 */
class TapDodgeInput : DodgeInput {

    /** 하단 안내 문구 (SPEC 7장 문구에 방향 규칙을 더함) */
    override val hint: String = "총알이 가까워지면 반대쪽을 탭!"

    /** 등록된 입력 수신 함수. 화면이 없을 때는 null. */
    private var listener: ((DodgeEvent) -> Unit)? = null

    /** 수신 함수를 등록/해제한다. */
    override fun setListener(listener: ((DodgeEvent) -> Unit)?) {
        this.listener = listener
    }

    /**
     * 화면 전체의 터치를 받는 Modifier.
     *
     * onPress를 쓰는 이유: 손가락을 "뗄 때"(onTap)가 아니라 "닿는 순간" 반응해야
     * 타이밍 게임이 정확하다. 떼는 시점은 사람마다 들쭉날쭉하기 때문이다.
     *
     * pointerInput(this): 키 값으로 이 객체를 넘겨, 객체가 바뀌면 터치 처리도 새로 만들어지게 한다.
     */
    override val modifier: Modifier = Modifier.pointerInput(this) {
        detectTapGestures(onPress = { offset ->
            // 시각은 터치 위치 계산보다 먼저, 감지 즉시 찍는다.
            val now = System.nanoTime()
            // size.width: 터치를 받는 영역(= 게임 화면)의 너비(픽셀)
            listener?.invoke(DodgeEvent(directionFor(offset.x, size.width.toFloat()), now))
        })
    }

    /**
     * 터치한 가로 위치로 방향을 정한다. 정확히 가운데는 오른쪽으로 친다.
     * (화면과 무관한 계산이라 따로 떼 두면 읽기 쉽다.)
     */
    private fun directionFor(x: Float, width: Float): DodgeDirection =
        if (x < width / 2f) DodgeDirection.LEFT else DodgeDirection.RIGHT
}
