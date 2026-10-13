package com.kukuku.bang

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.random.Random

/**
 * [GameEngine]의 판정/난이도 로직 단위 테스트 (SPEC 10장).
 *
 * 엔진이 시간을 인자로 받기 때문에, 실제로 기다리지 않고
 * "정확히 몇 초에 어느 방향으로 입력했다"를 바로 재현할 수 있다.
 */
class GameEngineTest {

    /** 소수 계산 오차 허용 범위. 0.1 + 0.2 != 0.3 같은 부동소수점 오차 때문에 필요. */
    private val eps = 1e-9

    private lateinit var engine: GameEngine

    /** 각 테스트 전에 시드를 고정한 엔진을 만든다 → 랜덤 대기 시간/방향이 매번 같다. */
    @Before
    fun setUp() {
        engine = GameEngine(Random(42))
    }

    // ─────────────── 테스트 도우미 ───────────────

    /** 대기·경고를 지나 총알이 발사된 상태까지 시간을 진행시킨다. */
    private fun advanceToFiring(state: GameState): GameState {
        val waiting = state.phase as Phase.Waiting
        val warning = engine.update(state, waiting.endTime)
        val w = warning.phase as Phase.Warning
        return engine.update(warning, w.endTime)
    }

    /** 새 판을 시작해서 첫 총알이 발사된 상태를 만든다. */
    private fun firstBullet(): GameState = advanceToFiring(engine.newGame(0.0))

    /**
     * 총알 방향에 대한 "정답" 회피 방향.
     * 엔진의 correctDirection을 쓰지 않고 테스트에 직접 적는다.
     * (엔진 함수를 그대로 쓰면, 엔진이 틀려도 테스트가 같이 틀려서 버그를 못 잡는다)
     */
    private fun right(f: Phase.Firing): DodgeDirection =
        if (f.side == Side.LEFT) DodgeDirection.RIGHT else DodgeDirection.LEFT

    /** 정답의 반대 방향 */
    private fun wrong(f: Phase.Firing): DodgeDirection =
        if (right(f) == DodgeDirection.LEFT) DodgeDirection.RIGHT else DodgeDirection.LEFT

    /** 발사 중인 상태에서 [time]에 정답 방향으로 피한다. */
    private fun dodgeCorrect(s: GameState, time: Double): GameState =
        engine.onDodge(s, right(s.phase as Phase.Firing), time)

    /** 상태가 GameOver이고 이유가 [expected]인지 확인한다. */
    private fun assertFailed(state: GameState, expected: FailReason) {
        val p = state.phase
        assertTrue("GameOver여야 하는데 $p", p is Phase.GameOver)
        assertEquals(expected, (p as Phase.GameOver).reason)
    }

    // ─────────────── 단계 진행 ───────────────

    @Test
    fun `대기 - 경고 - 발사 순서로 진행되고 시간이 SPEC과 일치한다`() {
        val start = engine.newGame(0.0)
        val waiting = start.phase as Phase.Waiting
        // 대기 시간은 0.8~2.0초 사이
        val waitLen = waiting.endTime - waiting.startTime
        assertTrue(waitLen in 0.8..2.0)

        val warningState = engine.update(start, waiting.endTime)
        val warning = warningState.phase as Phase.Warning
        assertEquals(0.30, warning.endTime - warning.startTime, eps)

        val firing = engine.update(warningState, warning.endTime).phase as Phase.Firing
        assertEquals(warning.endTime, firing.fireTime, eps)
        assertEquals(1.20, firing.hitTime - firing.fireTime, eps)
        assertEquals(0.30, firing.dodgeWindow, eps)
    }

    @Test
    fun `프레임이 크게 밀려도 한 번의 update로 여러 단계를 건너뛴다`() {
        // 시작 직후 100초 뒤로 한 번에 점프 → 대기/경고/발사를 지나 피격 실패
        val s = engine.update(engine.newGame(0.0), 100.0)
        assertFailed(s, FailReason.HIT)
    }

    // ─────────────── 총알 방향 ───────────────

    @Test
    fun `경고 때 정해진 방향이 발사 단계까지 그대로 유지된다`() {
        val start = engine.newGame(0.0)
        val warningState = engine.update(start, (start.phase as Phase.Waiting).endTime)
        val w = warningState.phase as Phase.Warning
        val f = engine.update(warningState, w.endTime).phase as Phase.Firing
        assertEquals(w.side, f.side)
    }

    @Test
    fun `총알은 왼쪽과 오른쪽 양쪽에서 모두 날아온다`() {
        // 랜덤이므로 여러 판을 돌려 양쪽이 다 나오는지 본다 (50:50이라 40판이면 사실상 확실)
        val sides = (1..40).map { i ->
            (advanceToFiring(engine.newGame(i * 100.0)).phase as Phase.Firing).side
        }.toSet()
        assertEquals(setOf(Side.LEFT, Side.RIGHT), sides)
    }

    @Test
    fun `정답 방향은 항상 총알의 반대쪽이다`() {
        assertEquals(DodgeDirection.RIGHT, engine.correctDirection(Side.LEFT))
        assertEquals(DodgeDirection.LEFT, engine.correctDirection(Side.RIGHT))
    }

    // ─────────────── 방향 + 회피 구간 판정 ───────────────

    @Test
    fun `올바른 방향 + 회피 구간 시작 시각 = 성공`() {
        val s = firstBullet()
        val f = s.phase as Phase.Firing
        val after = dodgeCorrect(s, f.windowStart)
        assertEquals(1, after.streak)
        assertTrue(after.phase is Phase.Waiting)
    }

    @Test
    fun `올바른 방향 + 회피 구간 한가운데 = 성공`() {
        val s = firstBullet()
        val f = s.phase as Phase.Firing
        assertEquals(1, dodgeCorrect(s, f.windowStart + f.dodgeWindow / 2).streak)
    }

    @Test
    fun `올바른 방향 + 총알 도달 정각(hitTime) = 성공 - 구간 끝 포함`() {
        val s = firstBullet()
        val f = s.phase as Phase.Firing
        assertEquals(1, dodgeCorrect(s, f.hitTime).streak)
    }

    @Test
    fun `왼쪽 총알과 오른쪽 총알 모두에서 반대 방향 회피가 성공한다`() {
        // 양쪽 방향을 각각 최소 한 번씩 실제로 판정해 본다
        val tested = mutableSetOf<Side>()
        var t = 0.0
        while (tested.size < 2) {
            val s = advanceToFiring(engine.newGame(t))
            val f = s.phase as Phase.Firing
            val after = dodgeCorrect(s, f.hitTime - f.dodgeWindow / 2)
            assertEquals("${f.side}에서 온 총알 회피 실패", 1, after.streak)
            tested += f.side
            t += 100.0
        }
    }

    @Test
    fun `회피 구간 안이라도 반대 방향(총알 쪽)으로 피하면 실패`() {
        val s = firstBullet()
        val f = s.phase as Phase.Firing
        // 구간 시작, 가운데, 끝 모두에서 틀린 방향은 실패
        for (time in listOf(f.windowStart, f.windowStart + f.dodgeWindow / 2, f.hitTime)) {
            val after = engine.onDodge(s, wrong(f), time)
            assertFailed(after, FailReason.WRONG_DIRECTION)
            assertEquals(0, after.streak)
        }
    }

    @Test
    fun `올바른 방향이라도 회피 구간 바로 앞이면 실패`() {
        val s = firstBullet()
        val f = s.phase as Phase.Firing
        val after = dodgeCorrect(s, f.windowStart - 0.001)
        assertFailed(after, FailReason.EARLY_DODGE)
        assertEquals(0, after.streak)
    }

    @Test
    fun `구간 앞에서 틀린 방향이면 타이밍 실패로 알려준다`() {
        // 시간을 방향보다 먼저 판정하므로 이유는 EARLY_DODGE
        val s = firstBullet()
        val f = s.phase as Phase.Firing
        assertFailed(engine.onDodge(s, wrong(f), f.windowStart - 0.001), FailReason.EARLY_DODGE)
    }

    @Test
    fun `올바른 방향이라도 회피 구간 뒤(hitTime 이후)면 피격 실패`() {
        val s = firstBullet()
        val f = s.phase as Phase.Firing
        assertFailed(dodgeCorrect(s, f.hitTime + 0.001), FailReason.HIT)
    }

    @Test
    fun `아예 입력하지 않으면 hitTime이 지난 뒤 피격 실패`() {
        val s = firstBullet()
        val f = s.phase as Phase.Firing
        // hitTime 정각까지는 아직 발사 중
        assertTrue(engine.update(s, f.hitTime).phase is Phase.Firing)
        // 그 직후 피격
        assertFailed(engine.update(s, f.hitTime + 0.001), FailReason.HIT)
    }

    @Test
    fun `경고 중 입력은 방향과 상관없이 너무 빠름 실패`() {
        val start = engine.newGame(0.0)
        val warningState = engine.update(start, (start.phase as Phase.Waiting).endTime)
        val w = warningState.phase as Phase.Warning
        for (dir in DodgeDirection.entries) {
            assertFailed(engine.onDodge(warningState, dir, w.startTime + 0.1), FailReason.TOO_EARLY)
        }
    }

    @Test
    fun `대기 중 입력도 방향과 상관없이 너무 빠름 실패`() {
        val start = engine.newGame(0.0)
        for (dir in DodgeDirection.entries) {
            assertFailed(engine.onDodge(start, dir, 0.1), FailReason.TOO_EARLY)
        }
    }

    @Test
    fun `종료된 판에서 입력하면 아무 변화 없음`() {
        val over = engine.onDodge(engine.newGame(0.0), DodgeDirection.LEFT, 0.1)
        assertEquals(over, engine.onDodge(over, DodgeDirection.RIGHT, 5.0))
    }

    @Test
    fun `회피 성공 이벤트에 피한 방향이 기록된다`() {
        val s = firstBullet()
        val f = s.phase as Phase.Firing
        val event = dodgeCorrect(s, f.hitTime).lastEvent as GameEvent.Dodged
        assertEquals(right(f), event.direction)
    }

    @Test
    fun `회피 구간 마지막 20퍼센트에서 피하면 아슬아슬`() {
        val s = firstBullet()
        val f = s.phase as Phase.Firing
        val closeStart = f.hitTime - f.dodgeWindow * 0.2

        val close = dodgeCorrect(s, closeStart + 0.001).lastEvent as GameEvent.Dodged
        assertTrue(close.closeCall)

        val normal = dodgeCorrect(s, closeStart - 0.001).lastEvent as GameEvent.Dodged
        assertFalse(normal.closeCall)
    }

    @Test
    fun `성공하면 바로 다음 라운드 대기가 시작되고 다음 총알은 더 빠르다`() {
        val s = firstBullet()
        val f1 = s.phase as Phase.Firing
        val afterDodge = dodgeCorrect(s, f1.hitTime)
        val f2 = advanceToFiring(afterDodge).phase as Phase.Firing
        assertEquals(1.20 * 0.97, f2.hitTime - f2.fireTime, eps)
    }

    // ─────────────── 난이도 수치 ───────────────

    @Test
    fun `성공할 때마다 비행 시간이 3퍼센트씩 줄어든다`() {
        assertEquals(1.20, engine.bulletFlightTime(0), eps)
        assertEquals(1.20 * 0.97, engine.bulletFlightTime(1), eps)
        assertEquals(1.20 * 0.97 * 0.97, engine.bulletFlightTime(2), eps)
        // 연속으로 줄어드는지 (최소값에 닿기 전까지)
        for (n in 1..30) {
            assertTrue(engine.bulletFlightTime(n) < engine.bulletFlightTime(n - 1))
        }
    }

    @Test
    fun `비행 시간은 최소값 0점45초 아래로 내려가지 않는다`() {
        // 1.20 × 0.97^n < 0.45 가 되는 n은 약 33 → 그 이후는 전부 0.45
        assertEquals(0.45, engine.bulletFlightTime(40), eps)
        assertEquals(0.45, engine.bulletFlightTime(1000), eps)
        for (n in 0..200) {
            assertTrue(engine.bulletFlightTime(n) >= 0.45 - eps)
        }
    }

    @Test
    fun `회피 구간은 5회 성공마다 0점01초 줄어든다`() {
        assertEquals(0.30, engine.dodgeWindow(0), eps)
        assertEquals(0.30, engine.dodgeWindow(4), eps)   // 아직 5회 미만
        assertEquals(0.29, engine.dodgeWindow(5), eps)
        assertEquals(0.29, engine.dodgeWindow(9), eps)
        assertEquals(0.28, engine.dodgeWindow(10), eps)
    }

    @Test
    fun `회피 구간은 최소값 0점15초 아래로 내려가지 않는다`() {
        assertEquals(0.15, engine.dodgeWindow(75), eps)  // 15단계 감소 = 정확히 0.15
        assertEquals(0.15, engine.dodgeWindow(500), eps)
        for (n in 0..500) {
            assertTrue(engine.dodgeWindow(n) >= 0.15 - eps)
        }
    }

    @Test
    fun `성공 20회부터 대기 시간 범위가 짧아진다`() {
        assertEquals(0.8..2.0, engine.waitRange(19))
        assertEquals(0.6..1.5, engine.waitRange(20))
        // 실제로 뽑힌 대기 시간도 범위 안인지 여러 번 확인
        repeat(100) {
            val p = engine.continueGame(
                GameState(Phase.GameOver(FailReason.HIT, 0.0), streak = 20), 0.0
            ).phase as Phase.Waiting
            assertTrue(p.endTime - p.startTime in 0.6..1.5)
        }
    }

    @Test
    fun `난이도 단계 이름이 연속 기록 구간과 일치한다`() {
        assertEquals("초보", engine.tier(0).label)
        assertEquals("초보", engine.tier(9).label)
        assertEquals("숙련", engine.tier(10).label)
        assertEquals("숙련", engine.tier(24).label)
        assertEquals("베테랑", engine.tier(25).label)
        assertEquals("베테랑", engine.tier(49).label)
        assertEquals("전설", engine.tier(50).label)
    }

    // ─────────────── 입력 방식 보정 (카메라 모드) ───────────────

    @Test
    fun `회피 구간 보너스를 주면 구간이 그만큼 넓어진다`() {
        val faceEngine = GameEngine(Random(42), dodgeWindowBonus = 0.10)
        val start = faceEngine.newGame(0.0)
        val warning = faceEngine.update(start, (start.phase as Phase.Waiting).endTime)
        val s = faceEngine.update(warning, (warning.phase as Phase.Warning).endTime)
        val f = s.phase as Phase.Firing
        assertEquals(0.40, f.dodgeWindow, eps)

        // 보너스가 없으면 실패했을 시각(hitTime - 0.35)에 피해도 성공
        val after = faceEngine.onDodge(s, right(f), f.hitTime - 0.35)
        assertEquals(1, after.streak)
    }

    @Test
    fun `보너스는 난이도 함수 자체를 바꾸지 않는다`() {
        val faceEngine = GameEngine(Random(42), dodgeWindowBonus = 0.10)
        assertEquals(0.30, faceEngine.dodgeWindow(0), eps)
        assertEquals(0.15, faceEngine.dodgeWindow(500), eps)
    }

    // ─────────────── 이어하기 ───────────────

    @Test
    fun `이어하기는 한 판에 1회만 가능하고 연속 기록이 유지된다`() {
        // 3회 성공 후 실패하는 판을 만든다
        var s = engine.newGame(0.0)
        repeat(3) {
            s = advanceToFiring(s)
            s = dodgeCorrect(s, (s.phase as Phase.Firing).hitTime)
        }
        s = engine.update(advanceToFiring(s), 1000.0)
        assertFailed(s, FailReason.HIT)
        assertTrue(engine.canContinue(s))

        // 1회차 이어하기: 같은 연속 기록(3)에서 계속
        val continued = engine.continueGame(s, 1000.0)
        assertEquals(3, continued.streak)
        assertTrue(continued.phase is Phase.Waiting)
        assertTrue(continued.continueUsed)

        // 이어한 뒤 다시 실패 → 이번엔 이어하기 불가
        val failedAgain = engine.onDodge(continued, DodgeDirection.LEFT, 1000.01)
        assertFailed(failedAgain, FailReason.TOO_EARLY)
        assertFalse(engine.canContinue(failedAgain))
        // 억지로 호출해도 상태가 바뀌지 않는다
        assertEquals(failedAgain, engine.continueGame(failedAgain, 1001.0))
    }

    @Test
    fun `게임 중에는 이어하기를 할 수 없다`() {
        val s = engine.newGame(0.0)
        assertFalse(engine.canContinue(s))
        assertEquals(s, engine.continueGame(s, 0.5))
    }

    @Test
    fun `새 판을 시작하면 이어하기 기회가 다시 생긴다`() {
        val s = engine.newGame(0.0)
        assertEquals(0, s.streak)
        assertFalse(s.continueUsed)
    }
}
