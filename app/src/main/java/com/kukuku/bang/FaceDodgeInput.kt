package com.kukuku.bang

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * 고개 기울이기로 회피 방향을 입력하는 [DodgeInput] 구현체.
 *
 * 카메라 자체는 [FaceCamera]가 다루고, 이 클래스는 거기서 받은 각도를
 * [TiltDetector]에 넘겨 입력이 생기면 게임 화면에 알려주는 "연결 고리" 역할만 한다.
 * 화면 터치가 필요 없으므로 modifier는 기본값(빈 Modifier)을 그대로 쓴다.
 */
class FaceDodgeInput : DodgeInput {

    /** 하단 안내 문구 */
    override val hint: String = "총알이 가까워지면 반대쪽으로 고개를 기울여요!"

    /** 각도 → 방향 판정 규칙 (단위 테스트됨) */
    private val detector = TiltDetector()

    /** 등록된 입력 수신 함수 */
    private var listener: ((DodgeEvent) -> Unit)? = null

    /**
     * 디버그 표시용 문구 (현재 각도, 잠김 여부).
     * mutableStateOf라서 값이 바뀌면 이 값을 읽는 화면이 자동으로 다시 그려진다.
     */
    var debugText: String by mutableStateOf("얼굴을 찾는 중")
        private set

    /**
     * 지금 얼굴 위치 (흔들림 보정됨). 얼굴이 안 보이면 null.
     * 게임 화면이 얼굴 안내 원과 총잡이 조준점을 실제 얼굴 위치에 그릴 때 쓴다.
     */
    var face: FaceBox? by mutableStateOf(null)
        private set

    /** 등록/해제 */
    override fun setListener(listener: ((DodgeEvent) -> Unit)?) {
        this.listener = listener
    }

    /** 보정 화면에서 측정한 중립 각도를 기준값으로 설정한다. */
    fun setBaseline(baselineDeg: Float) {
        detector.baseline = baselineDeg
    }

    /**
     * 카메라에서 새 얼굴 정보가 들어올 때마다 호출된다 (메인 스레드).
     * @param sample 기울기 + 위치. 얼굴이 없으면 null.
     * @param timeNanos 그 프레임을 받은 순간의 System.nanoTime()
     */
    fun onFace(sample: FaceSample?, timeNanos: Long) {
        face = sample?.let { FaceMapping.smooth(face, it.box, FACE_SMOOTHING) }
        val rawDeg = sample?.angleDeg
        debugText = if (rawDeg == null) {
            "얼굴 없음"
        } else {
            // 기준 대비 각도(판정에 쓰는 값)를 크게, 원래 값과 상태를 작게 보여준다.
            String.format(
                Locale.US,
                "기울기 %+.1f°  (원값 %+.1f°)  %s",
                detector.relative(rawDeg), rawDeg,
                if (detector.isArmed) "입력 대기" else "중립으로 돌아오세요",
            )
        }
        detector.onAngle(rawDeg)?.let { dir -> listener?.invoke(DodgeEvent(dir, timeNanos)) }
    }

    private companion object {
        /**
         * 얼굴 위치 흔들림 보정 강도 (0~1). 화면 연출용 값이라 GameConfig가 아닌 여기에 둔다.
         * 0.5 = 매 프레임 새 위치 쪽으로 절반만 이동 → 떨림은 줄고, 고개를 움직이면 금방 따라온다.
         */
        const val FACE_SMOOTHING = 0.5f
    }
}
