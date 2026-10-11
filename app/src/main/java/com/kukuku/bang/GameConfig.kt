package com.kukuku.bang

/**
 * 게임의 모든 튜닝 수치를 모아 둔 곳.
 *
 * 왜 한 곳에 모으나?
 * - 난이도를 조절할 때 이 파일만 고치면 되므로, 코드 여기저기를 뒤질 필요가 없다.
 * - 숫자가 코드 중간에 박혀 있으면(매직 넘버) 그 숫자가 무슨 뜻인지 알기 어렵다.
 *
 * 시간 단위는 모두 "초(Double)"로 통일한다. (광고 간격만 실제 시계 기준이라 밀리초 Long)
 * 값은 SPEC.md 3장/4장/6장을 그대로 옮긴 것이다.
 */
object GameConfig {

    // ───────────── 총알 비행 시간 (SPEC 3장) ─────────────

    /** 총알이 총잡이에서 플레이어까지 날아가는 시작 시간(초). 클수록 쉽다. */
    const val BULLET_FLIGHT_TIME_START = 1.20

    /** 성공 1회마다 비행 시간에 곱하는 비율. 0.97 = 3% 감소. */
    const val BULLET_FLIGHT_TIME_DECAY = 0.97

    /** 비행 시간의 하한(초). 이보다 빨라지면 사람이 반응하기 어렵다. */
    const val BULLET_FLIGHT_TIME_MIN = 0.45

    // ───────────── 회피 허용 구간 (SPEC 3장) ─────────────

    /** 총알 도달 직전, 탭이 성공으로 인정되는 시작 구간 길이(초). */
    const val DODGE_WINDOW_START = 0.30

    /** 이 횟수만큼 성공할 때마다 회피 구간이 줄어든다. */
    const val DODGE_WINDOW_STEP_EVERY = 5

    /** 한 번 줄어들 때 감소량(초). */
    const val DODGE_WINDOW_STEP_AMOUNT = 0.01

    /** 회피 구간의 하한(초). */
    const val DODGE_WINDOW_MIN = 0.15

    /** 회피 구간의 마지막 이 비율 안에서 피하면 "아슬아슬!" (SPEC 4장: 마지막 20%) */
    const val CLOSE_CALL_RATIO = 0.20

    // ───────────── 발사 전 대기 / 경고 (SPEC 2·3장) ─────────────

    /** 초반 대기 시간 범위(초). 랜덤이라 플레이어가 리듬만 외워서 넘길 수 없다. */
    const val WAIT_MIN_EARLY = 0.8
    const val WAIT_MAX_EARLY = 2.0

    /** 이 연속 성공 횟수부터 대기 시간이 짧아진다. */
    const val WAIT_FAST_FROM_STREAK = 20

    /** 후반 대기 시간 범위(초). */
    const val WAIT_MIN_LATE = 0.6
    const val WAIT_MAX_LATE = 1.5

    /** 경고(총구 번쩍임) 시간(초). 고정값. */
    const val WARNING_DURATION = 0.30

    // ───────────── 난이도 단계 (SPEC 3장, 연출용) ─────────────

    /** 각 단계가 시작되는 연속 성공 횟수. 0~9 초보, 10~24 숙련, 25~49 베테랑, 50+ 전설 */
    const val TIER_SKILLED_FROM = 10
    const val TIER_VETERAN_FROM = 25
    const val TIER_LEGEND_FROM = 50

    // ───────────── 화면 연출 시간 ─────────────

    /** 회피 성공 시 플레이어 점프 동작이 이어지는 시간(초). 다음 경고(최소 0.6초 뒤)보다 짧아야 한다. */
    const val DODGE_ANIM_DURATION = 0.45

    /**
     * 실패 직후 이 시간(초) 동안은 탭해도 재시작하지 않는다.
     * 회피하려고 연타하던 손가락이 실수로 바로 새 판을 시작하지 않게 하기 위해서.
     */
    const val RESTART_DELAY = 0.6

    // ───────────── 전면 광고 조건 (SPEC 6장) ─────────────

    /** 설치 후 이 판 수까지는 전면 광고를 절대 띄우지 않는다. */
    const val INTERSTITIAL_FREE_GAMES = 3

    /** 실패(다시 하기) 이 횟수마다 1회 노출. */
    const val INTERSTITIAL_EVERY_FAILS = 3

    /** 직전 전면 광고 후 최소 경과 시간(밀리초). 60초. */
    const val INTERSTITIAL_MIN_INTERVAL_MS = 60_000L
}
