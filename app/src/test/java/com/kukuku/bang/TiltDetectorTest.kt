package com.kukuku.bang

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [TiltDetector] (기울기 → 방향 입력) 판정 규칙 테스트.
 * 수치를 직접 적어서(임계 15도, 중립 5도) 테스트가 GameConfig 변경에 흔들리지 않게 한다.
 */
class TiltDetectorTest {

    private fun detector(sign: Float = 1f) = TiltDetector(thresholdDeg = 15f, neutralDeg = 5f, sign = sign)

    @Test
    fun `임계값 이상 기울이면 그 방향 입력`() {
        assertEquals(DodgeDirection.RIGHT, detector().onAngle(15f))
        assertEquals(DodgeDirection.LEFT, detector().onAngle(-20f))
    }

    @Test
    fun `임계값 미만이면 입력 없음`() {
        val d = detector()
        assertNull(d.onAngle(14.9f))
        assertNull(d.onAngle(-14.9f))
        assertNull(d.onAngle(0f))
    }

    @Test
    fun `기울인 채로 있으면 입력은 한 번만 들어간다`() {
        val d = detector()
        assertEquals(DodgeDirection.RIGHT, d.onAngle(20f))
        assertNull(d.onAngle(25f))
        assertNull(d.onAngle(20f))
        assertFalse(d.isArmed)
    }

    @Test
    fun `중립 범위로 돌아와야 다음 입력을 받는다`() {
        val d = detector()
        d.onAngle(20f)
        assertNull(d.onAngle(8f))          // 아직 중립(±5) 밖 → 계속 잠김
        assertNull(d.onAngle(-20f))        // 반대로 확 기울여도 잠김 상태라 무시
        assertNull(d.onAngle(4f))          // 중립 안으로 돌아옴 → 이번 값으로는 입력 없음
        assertTrue(d.isArmed)
        assertEquals(DodgeDirection.LEFT, d.onAngle(-16f))
    }

    @Test
    fun `얼굴이 안 보이면 입력 없음 + 잠김 상태 유지`() {
        val d = detector()
        assertNull(d.onAngle(null))
        d.onAngle(20f)
        assertNull(d.onAngle(null))
        assertFalse("얼굴이 사라졌다고 잠김이 풀리면 안 된다", d.isArmed)
        assertNull(d.onAngle(20f))
    }

    @Test
    fun `기준 각도를 빼고 판정한다`() {
        val d = detector()
        d.baseline = 10f                    // 평소 자세가 오른쪽으로 10도
        assertNull(d.onAngle(20f))          // 기준 대비 +10 → 입력 아님
        assertEquals(DodgeDirection.RIGHT, d.onAngle(25f))   // 기준 대비 +15
    }

    @Test
    fun `부호 보정 -1이면 방향이 뒤집힌다`() {
        assertEquals(DodgeDirection.LEFT, detector(sign = -1f).onAngle(20f))
        assertEquals(DodgeDirection.RIGHT, detector(sign = -1f).onAngle(-20f))
    }
}

/**
 * [TiltCalibrator] (중립 자세 측정) 테스트.
 * 측정 시간 2초, 최소 10회, 얼굴 공백 허용 0.5초로 고정.
 */
class TiltCalibratorTest {

    private fun calibrator() = TiltCalibrator(duration = 2.0, minSamples = 10, maxFaceGap = 0.5)

    /** 0.1초 간격으로 [values]를 차례로 넣고, 마지막 결과를 돌려준다. */
    private fun TiltCalibrator.feed(values: List<Float?>, startAt: Double = 0.0): Float? {
        var result: Float? = null
        values.forEachIndexed { i, v -> result = onAngle(v, startAt + i * 0.1) ?: result }
        return result
    }

    @Test
    fun `2초 동안 측정하면 중앙값을 기준으로 돌려준다`() {
        // 0.0초 ~ 2.1초 (22개), 대부분 3도 + 튀는 값 하나 → 중앙값은 3도
        val values = List(22) { if (it == 5) 40f else 3f }
        assertEquals(3f, calibrator().feed(values)!!, 0.001f)
    }

    @Test
    fun `2초가 되기 전에는 결과가 없다`() {
        assertNull(calibrator().feed(List(15) { 3f }))   // 1.4초까지만
    }

    @Test
    fun `측정값이 너무 적으면 결과가 없다`() {
        val c = calibrator()
        // 인식이 느려서 0.4초 간격으로 6번만 측정된 상황 (공백 0.4초라 초기화는 안 됨)
        assertNull(c.onAngle(3f, 0.0))
        assertNull(c.onAngle(3f, 0.4))
        assertNull(c.onAngle(3f, 0.8))
        assertNull(c.onAngle(3f, 1.2))
        assertNull(c.onAngle(3f, 1.6))
        assertNull(c.onAngle(3f, 2.0))   // 시간은 됐지만 6개뿐
    }

    @Test
    fun `얼굴이 0점5초 넘게 사라지면 처음부터 다시 잰다`() {
        val c = calibrator()
        c.feed(List(15) { 3f })                         // 0.0~1.4초 측정
        c.onAngle(null, 1.5)
        c.onAngle(null, 2.0)                            // 마지막 얼굴(1.4초) 후 0.6초 → 초기화
        assertEquals(0f, c.progress(2.0), 0.001f)
        // 다시 2초를 채워야 결과가 나온다
        assertNull(c.feed(List(10) { 3f }, startAt = 2.1))
        // 3.1~4.2초 (2.1초부터 2초 이상). 4.1초 딱 맞춤은 소수 오차로 1.99999..가 될 수 있어 넉넉히.
        assertEquals(3f, c.feed(List(12) { 3f }, startAt = 3.1)!!, 0.001f)
    }

    @Test
    fun `진행률은 얼굴을 처음 본 순간부터 센다`() {
        val c = calibrator()
        c.onAngle(null, 0.0)
        assertEquals(0f, c.progress(0.5), 0.001f)
        c.onAngle(3f, 1.0)
        assertEquals(0.5f, c.progress(2.0), 0.001f)
    }
}
