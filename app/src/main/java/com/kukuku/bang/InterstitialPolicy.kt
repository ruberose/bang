package com.kukuku.bang

/**
 * 전면 광고를 "띄워도 되는지"만 판단하는 순수 로직 (SPEC 6장).
 *
 * 왜 AdManager와 따로 두나?
 * - AdManager(7단계)는 AdMob SDK를 쓰므로 Android에 의존해서 단위 테스트가 어렵다.
 * - 노출 조건은 정책 위반과 직결되는 중요한 규칙이라, 따로 떼서 테스트로 확실히 검증한다.
 *
 * 이 함수는 "다시 하기"를 누른 시점에만 호출한다.
 * (게임 플레이 중에는 아예 호출하지 않으므로 플레이 중 광고가 뜰 수 없다.)
 */
object InterstitialPolicy {

    /**
     * 전면 광고를 지금 보여도 되는지 판단한다. 아래 조건을 "모두" 만족해야 true.
     * 1. 설치 후 총 플레이 판 수가 3판을 넘었다 (첫 3판은 노출 안 함)
     * 2. 직전 전면 광고 이후 실패가 3회 이상 쌓였다 (실패 3회마다 1회)
     * 3. 직전 전면 광고로부터 60초 이상 지났다 (처음이면 통과)
     *
     * @param totalGamesPlayed 설치 후 끝난 판 수 (방금 끝난 판 포함)
     * @param failsSinceLastAd 직전 전면 광고 이후 실패 횟수 (방금 실패 포함)
     * @param lastShownAtMs 직전 전면 광고를 띄운 시각(밀리초). 한 번도 없으면 null
     * @param nowMs 현재 시각(밀리초)
     */
    fun shouldShow(
        totalGamesPlayed: Int,
        failsSinceLastAd: Int,
        lastShownAtMs: Long?,
        nowMs: Long,
    ): Boolean {
        val pastFreeGames = totalGamesPlayed > GameConfig.INTERSTITIAL_FREE_GAMES
        val enoughFails = failsSinceLastAd >= GameConfig.INTERSTITIAL_EVERY_FAILS
        val enoughTime = lastShownAtMs == null ||
            nowMs - lastShownAtMs >= GameConfig.INTERSTITIAL_MIN_INTERVAL_MS
        return pastFreeGames && enoughFails && enoughTime
    }
}
