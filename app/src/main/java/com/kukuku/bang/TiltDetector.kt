package com.kukuku.bang

import kotlin.math.abs

/**
 * 고개 기울기 각도(도)를 회피 방향 입력으로 바꾸는 순수 로직.
 *
 * 왜 카메라 코드와 분리하나?
 * - "몇 도 넘으면 입력인가, 언제 다음 입력을 받나" 같은 판정 규칙은 카메라 없이도 테스트할 수 있어야 한다.
 * - 카메라/ML Kit 코드는 각도 숫자만 넘겨주고, 판단은 전부 여기서 한다.
 *
 * 동작:
 * 1. 측정한 각도에서 기준값([baseline], 중립 자세 각도)을 빼고 부호를 보정한다.
 * 2. 그 값이 +임계값 이상이면 RIGHT, -임계값 이하이면 LEFT 입력.
 * 3. 한 번 입력되면 "잠김" 상태가 되고, 고개가 중립 범위(±neutral) 안으로 돌아와야 다시 풀린다.
 *    → 고개를 기울인 채로 있어도 입력이 한 번만 들어간다.
 * 4. 얼굴이 안 보이면(null) 입력 없음. 잠김 상태는 그대로 유지한다
 *    (얼굴이 잠깐 가려졌다 나타났다고 입력이 다시 들어가면 안 되므로).
 *
 * @param thresholdDeg 입력으로 인정하는 기울기(도)
 * @param neutralDeg 다음 입력을 받기 위해 돌아와야 하는 중립 범위(도)
 * @param sign 부호 보정 (+1 또는 -1). 기기에 따라 ML Kit 각도 방향이 반대일 수 있어서.
 */
class TiltDetector(
    private val thresholdDeg: Float = GameConfig.FACE_TILT_THRESHOLD_DEG,
    private val neutralDeg: Float = GameConfig.FACE_TILT_NEUTRAL_DEG,
    private val sign: Float = GameConfig.FACE_TILT_SIGN,
) {
    /** 중립 자세의 원래 각도. 보정 화면에서 측정해서 넣는다. */
    var baseline: Float = 0f

    /** 입력을 받을 수 있는 상태인지. false면 중립으로 돌아오기를 기다리는 중. */
    var isArmed: Boolean = true
        private set

    /**
     * 원래 각도를 "기준 대비, 화면 기준 오른쪽이 +"인 값으로 바꾼다. 디버그 표시에도 쓴다.
     */
    fun relative(rawDeg: Float): Float = (rawDeg - baseline) * sign

    /**
     * 새 각도 측정값을 넣는다. 이번 측정으로 입력이 발생하면 그 방향을, 아니면 null을 돌려준다.
     * @param rawDeg ML Kit가 알려준 원래 기울기(도). 얼굴이 없으면 null.
     */
    fun onAngle(rawDeg: Float?): DodgeDirection? {
        if (rawDeg == null) return null
        val a = relative(rawDeg)

        // 잠김 상태: 중립으로 돌아왔는지만 확인한다.
        if (!isArmed) {
            if (abs(a) <= neutralDeg) isArmed = true
            return null
        }

        return when {
            a >= thresholdDeg -> DodgeDirection.RIGHT.also { isArmed = false }
            a <= -thresholdDeg -> DodgeDirection.LEFT.also { isArmed = false }
            else -> null
        }
    }
}

/**
 * 게임 시작 전 "중립 자세" 각도를 측정하는 순수 로직.
 *
 * 왜 측정하나? 사람마다, 폰을 드는 자세마다 "똑바로 있다"고 느끼는 각도가 0도가 아니다.
 * 고개가 원래 5도 기울어진 사람은 10도만 더 기울여도 15도 임계값을 넘어 버린다.
 * 그래서 평소 각도를 기준으로 삼고, 거기서 얼마나 더 기울였는지로 판정한다.
 *
 * 측정 규칙:
 * - 얼굴이 처음 보인 순간부터 [duration]초 동안 각도를 모은다.
 * - 도중에 얼굴이 [maxFaceGap]초 넘게 안 보이면 처음부터 다시 잰다.
 * - 시간이 다 됐고 측정값이 [minSamples]개 이상이면 "중앙값"을 기준으로 돌려준다.
 *   평균 대신 중앙값을 쓰는 이유: 순간적으로 튀는 값(인식 오류) 몇 개에 흔들리지 않기 때문.
 *
 * 시간은 인자로 받는다(초). 테스트에서 실제로 2초를 기다리지 않아도 되게 하기 위해서다.
 */
class TiltCalibrator(
    private val duration: Double = GameConfig.CALIBRATION_DURATION,
    private val minSamples: Int = GameConfig.CALIBRATION_MIN_SAMPLES,
    private val maxFaceGap: Double = GameConfig.CALIBRATION_MAX_FACE_GAP,
) {
    private val samples = mutableListOf<Float>()

    /** 이번 측정을 시작한 시각. 아직 얼굴을 못 봤으면 null. */
    private var startTime: Double? = null

    /** 마지막으로 얼굴을 본 시각 */
    private var lastFaceTime: Double = 0.0

    /**
     * 측정값을 넣는다. 측정이 끝나면 기준 각도를, 아직이면 null을 돌려준다.
     * @param rawDeg 원래 기울기(도). 얼굴이 없으면 null.
     * @param now 현재 시각(초)
     */
    fun onAngle(rawDeg: Float?, now: Double): Float? {
        if (rawDeg == null) {
            // 얼굴이 오래 안 보이면 지금까지 모은 값을 버린다
            if (startTime != null && now - lastFaceTime > maxFaceGap) reset()
            return null
        }
        if (startTime == null) startTime = now
        samples += rawDeg
        lastFaceTime = now

        val start = startTime ?: return null
        return if (now - start >= duration && samples.size >= minSamples) median() else null
    }

    /** 진행률 0~1 (화면의 진행 막대용). 얼굴을 아직 못 봤으면 0. */
    fun progress(now: Double): Float {
        val start = startTime ?: return 0f
        return ((now - start) / duration).toFloat().coerceIn(0f, 1f)
    }

    /** 측정을 처음부터 다시 한다. */
    fun reset() {
        samples.clear()
        startTime = null
    }

    /** 모은 값의 중앙값 (정렬했을 때 가운데 값) */
    private fun median(): Float {
        val sorted = samples.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2f
    }
}
