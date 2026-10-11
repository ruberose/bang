package com.kukuku.bang

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [InterstitialPolicy] 전면 광고 노출 조건 테스트 (SPEC 6장 / 10장).
 * 광고 정책 위반을 막는 핵심 규칙이라 경계값(3판, 3회, 60초)을 꼼꼼히 확인한다.
 */
class InterstitialPolicyTest {

    /** 테스트용 기준 시각(밀리초). 값 자체는 의미 없고 차이만 중요하다. */
    private val now = 1_000_000L

    @Test
    fun `설치 후 첫 3판은 다른 조건을 만족해도 노출하지 않는다`() {
        for (games in 1..3) {
            assertFalse(InterstitialPolicy.shouldShow(games, failsSinceLastAd = 3, lastShownAtMs = null, nowMs = now))
        }
        // 4판째부터 가능
        assertTrue(InterstitialPolicy.shouldShow(4, failsSinceLastAd = 3, lastShownAtMs = null, nowMs = now))
    }

    @Test
    fun `실패가 3회 쌓이기 전에는 노출하지 않는다`() {
        assertFalse(InterstitialPolicy.shouldShow(10, failsSinceLastAd = 1, lastShownAtMs = null, nowMs = now))
        assertFalse(InterstitialPolicy.shouldShow(10, failsSinceLastAd = 2, lastShownAtMs = null, nowMs = now))
        assertTrue(InterstitialPolicy.shouldShow(10, failsSinceLastAd = 3, lastShownAtMs = null, nowMs = now))
    }

    @Test
    fun `직전 광고 후 60초가 지나지 않았으면 노출하지 않는다`() {
        assertFalse(InterstitialPolicy.shouldShow(10, 3, lastShownAtMs = now - 59_999, nowMs = now))
        assertTrue(InterstitialPolicy.shouldShow(10, 3, lastShownAtMs = now - 60_000, nowMs = now))
    }

    @Test
    fun `세 조건 중 하나라도 어기면 노출하지 않는다`() {
        val recent = now - 10_000
        val old = now - 120_000
        assertFalse(InterstitialPolicy.shouldShow(3, 3, old, now))     // 첫 3판
        assertFalse(InterstitialPolicy.shouldShow(10, 2, old, now))    // 실패 부족
        assertFalse(InterstitialPolicy.shouldShow(10, 3, recent, now)) // 60초 미만
        assertTrue(InterstitialPolicy.shouldShow(10, 3, old, now))     // 모두 만족
    }
}
