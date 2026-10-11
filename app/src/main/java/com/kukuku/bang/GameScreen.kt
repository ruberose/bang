package com.kukuku.bang

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * 색 팔레트 (SPEC 8장: 3~4색으로 제한).
 * 색을 한곳에 모아 두면 나중에 분위기를 바꿀 때 여기만 고치면 된다.
 */
private object Palette {
    /** 배경: 어두운 남색 (밤의 황야 느낌) */
    val Background = Color(0xFF1B1B2F)

    /** 인물 실루엣, 글자: 크림색 */
    val Ink = Color(0xFFF2E9DC)

    /** 강조: 총구 섬광, 총알 (주황) */
    val Accent = Color(0xFFF2A541)

    /** 위험: 피격 (빨강) */
    val Danger = Color(0xFFE4572E)
}

/**
 * 화면 배치 비율. 게임 규칙이 아니라 "그림 크기"라서 GameConfig가 아닌 여기에 둔다.
 *
 * 기기마다 화면 크기가 다르므로 픽셀 값을 쓰지 않고,
 * 화면 너비의 일정 비율을 "1유닛(u)"으로 정해 모든 크기를 u의 배수로 그린다.
 */
private object Layout {
    /** 1유닛 = 화면 너비 × 이 값 */
    const val UNIT_RATIO = 0.075f

    /** 땅(인물이 서 있는 선)의 세로 위치 = 화면 높이 × 이 값 */
    const val GROUND_Y_RATIO = 0.62f

    /** 총잡이가 왼쪽/오른쪽에 설 때의 가로 위치 (화면 너비 비율) */
    const val SHOOTER_LEFT_X_RATIO = 0.13f
    const val SHOOTER_RIGHT_X_RATIO = 0.87f

    /** 플레이어 가로 위치: 가운데 (양쪽 어디서든 총알이 올 수 있으므로) */
    const val PLAYER_X_RATIO = 0.5f

    // 인물 크기 (유닛 단위)
    const val LEG_W = 0.7f
    const val LEG_H = 1.6f
    const val BODY_W = 0.95f
    const val BODY_H = 1.8f
    const val HEAD_R = 0.45f

    /** 총을 든 높이: 몸통 아래에서 이 비율만큼 위 (총알이 플레이어 가슴 높이로 날아가게) */
    const val SHOULDER_RATIO = 0.8f

    /**
     * 회피 동작: 발을 축으로 몸을 젖히는 최대 각도(도).
     * 65도쯤 젖히면 머리 높이가 총알 높이보다 낮아져서 총알이 몸 위로 지나가는 것처럼 보인다.
     */
    const val LEAN_MAX_DEG = 65f

    /** 총잡이가 새 위치에 나타날 때 위에서 떨어지듯 내려오는 높이(유닛)와 시간(초) */
    const val ARRIVE_DROP = 1.2f
    const val ARRIVE_DURATION = 0.12

    /** 총알 크기 (유닛) */
    const val BULLET_W = 0.55f
    const val BULLET_H = 0.22f
}

/**
 * 게임 화면 전체. Canvas로 장면을 그리고, [input]에서 오는 회피 입력을 엔진에 전달한다.
 *
 * 역할 분담:
 * - 규칙 계산(판정, 단계 진행, 난이도, 총알 방향)은 전부 [GameEngine]이 한다.
 * - 입력을 "방향 + 시각"으로 만드는 일은 [DodgeInput] 구현체가 한다.
 * - 이 함수는 시간을 재서 엔진에 넘기고, 돌려받은 상태를 그리기만 한다.
 *
 * 시간 기준:
 * - 화면이 처음 만들어진 순간의 System.nanoTime()을 0초로 삼는다.
 * - 매 프레임 시각(withFrameNanos)과 입력 시각(DodgeEvent.timeNanos)이
 *   같은 시계(System.nanoTime) 기준이라 두 값을 그대로 비교할 수 있다.
 *
 * @param input 회피 입력 방식. 기본은 탭. 나중에 얼굴 입력 등으로 바꿔 끼울 수 있다.
 */
@Composable
fun GameScreen(
    modifier: Modifier = Modifier,
    input: DodgeInput = remember { TapDodgeInput() },
) {
    // remember: 화면이 다시 그려져도(리컴포지션) 값이 초기화되지 않게 보관한다.
    val engine = remember { GameEngine() }
    val startNanos = remember { System.nanoTime() }

    /** 현재 게임 상태 */
    var state by remember { mutableStateOf(engine.newGame(0.0)) }

    /** 현재 시각(초). 매 프레임 갱신 → Canvas가 이 값을 읽으므로 매 프레임 다시 그려진다. */
    var now by remember { mutableDoubleStateOf(0.0) }

    /**
     * 마지막으로 발사된 총알 정보.
     * 회피 성공 후에는 엔진 상태가 바로 "대기"로 바뀌지만,
     * 화면에서는 총알이 플레이어 뒤로 날아가는 모습을 계속 보여주고 싶어서 따로 기억해 둔다.
     */
    var lastFiring by remember { mutableStateOf<Phase.Firing?>(null) }

    /**
     * 총잡이가 지금 서 있는 쪽.
     * 엔진은 경고/발사 단계에만 방향을 갖고 있으므로, 대기 중에도 총잡이를 그리려면
     * 화면 쪽에서 "마지막 위치"를 기억해야 한다.
     */
    var shooterSide by remember { mutableStateOf(Side.LEFT) }

    /** 총잡이가 반대쪽으로 옮겨 나타난 시각. 착지 연출용. 옮기지 않았으면 null. */
    var shooterArrivedAt by remember { mutableStateOf<Double?>(null) }

    /** 새 상태를 반영하면서, 화면 연출에 필요한 정보(총알, 총잡이 위치)도 갱신한다. */
    fun applyState(newState: GameState) {
        val np = newState.phase
        // 경고 단계에 "새로 들어온" 순간: 총잡이를 그 방향으로 옮긴다.
        if (np is Phase.Warning && state.phase !is Phase.Warning) {
            shooterArrivedAt = if (np.side != shooterSide) np.startTime else null
            shooterSide = np.side
        }
        // 프레임이 크게 밀려 경고를 건너뛰고 바로 발사된 경우에도 위치는 맞춘다.
        if (np is Phase.Firing) {
            shooterSide = np.side
            lastFiring = np
        }
        state = newState
    }

    /** System.nanoTime()을 게임 시각(초)으로 변환 */
    fun toGameTime(nanos: Long): Double = (nanos - startNanos) / 1_000_000_000.0

    /**
     * 회피 입력 처리. 입력 구현체가 찍어 준 "입력 순간의 시각"을 그대로 쓴다.
     * 프레임 단위(약 16ms)로 반올림되면 회피 구간 끝자락 판정이 부정확해지기 때문이다.
     */
    fun handleDodge(event: DodgeEvent) {
        val t = toGameTime(event.timeNanos)
        val phase = state.phase
        if (phase is Phase.GameOver) {
            // 임시 재시작: 아무 입력이나 받으면 새 판 (6단계에서 정식 실패 화면으로 교체)
            if (t - phase.time >= GameConfig.RESTART_DELAY) {
                lastFiring = null
                applyState(engine.newGame(t))
            }
        } else {
            applyState(engine.onDodge(state, event.direction, t))
        }
    }

    // ── 입력 연결 ──
    // DisposableEffect: 화면이 나타날 때 입력 수신을 등록하고, 사라질 때(onDispose) 해제한다.
    // input이 다른 구현체로 바뀌면 이전 것은 해제되고 새 것으로 다시 등록된다.
    DisposableEffect(input) {
        input.setListener { event -> handleDodge(event) }
        onDispose { input.setListener(null) }
    }

    // ── 게임 루프 ──
    // LaunchedEffect(Unit): 화면이 처음 나타날 때 한 번 시작되고, 화면이 사라지면 자동으로 멈춘다.
    // withFrameNanos는 다음 화면 갱신(보통 1/60초) 때까지 기다렸다가 그 시각을 알려준다.
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { frameNanos ->
                now = toGameTime(frameNanos)
                applyState(engine.update(state, now))
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Palette.Background)
            // 입력 구현체가 필요한 터치 처리를 붙인다 (탭 입력이면 화면 전체 터치).
            .then(input.modifier)
    ) {
        // ── 장면 그리기 ──
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawScene(state, now, lastFiring, shooterSide, shooterArrivedAt)
        }

        // ── 글자 (상단 연속 기록 / 하단 안내 / 실패 문구) ──
        // safeDrawing: 상태바·카메라 구멍·내비게이션바에 글자가 가리지 않게 여백을 준다.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // 상단: 현재 연속 기록 (큰 숫자). 최고 기록은 4단계에서 추가.
            Text(
                text = state.streak.toString(),
                color = Palette.Ink,
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
            )

            // 중앙: 실패 시 문구
            val phase = state.phase
            if (phase is Phase.GameOver) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = failMessage(phase.reason),
                        color = Palette.Danger,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(12.dp))
                    // 재시작 대기 시간이 지난 뒤에만 안내를 보여준다.
                    if (now - phase.time >= GameConfig.RESTART_DELAY) {
                        Text(
                            text = "탭해서 다시 시작",
                            color = Palette.Ink,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            } else {
                // 실패 문구가 없을 때도 같은 자리를 차지하게 해서 위아래 글자가 흔들리지 않게 한다.
                Spacer(Modifier.height(1.dp))
            }

            // 하단: 조작 안내 (입력 방식마다 다르므로 입력 구현체가 정한 문구 사용)
            Text(
                text = input.hint,
                color = Palette.Ink.copy(alpha = 0.7f),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

/**
 * 실패 이유별 화면 문구.
 * (불안을 자극하지 않는 가벼운 문구만 사용 - CLAUDE.md 광고/문구 규칙)
 */
private fun failMessage(reason: FailReason): String = when (reason) {
    FailReason.TOO_EARLY -> "아직 안 쐈어요!"
    FailReason.EARLY_DODGE -> "너무 일찍 피했어요!"
    FailReason.WRONG_DIRECTION -> "반대로 피했어요!"
    FailReason.HIT -> "맞았다!"
}

/** 총잡이가 [side]에 설 때의 가로 위치(픽셀) */
private fun DrawScope.shooterX(side: Side): Float = size.width * when (side) {
    Side.LEFT -> Layout.SHOOTER_LEFT_X_RATIO
    Side.RIGHT -> Layout.SHOOTER_RIGHT_X_RATIO
}

/**
 * 장면 전체를 그린다: 땅 → 총잡이 → 경고 섬광 → 플레이어 → 총알.
 * DrawScope의 확장 함수라서 이 안에서 drawRect, drawCircle 등을 바로 쓸 수 있다.
 *
 * @param state 현재 게임 상태
 * @param now 현재 시각(초)
 * @param lastFiring 마지막으로 발사된 총알 (회피 후 날아가는 모습용)
 * @param shooterSide 총잡이가 서 있는 쪽
 * @param shooterArrivedAt 총잡이가 반대쪽으로 옮겨 온 시각 (착지 연출용)
 */
private fun DrawScope.drawScene(
    state: GameState,
    now: Double,
    lastFiring: Phase.Firing?,
    shooterSide: Side,
    shooterArrivedAt: Double?,
) {
    val u = size.width * Layout.UNIT_RATIO
    val groundY = size.height * Layout.GROUND_Y_RATIO
    val playerX = size.width * Layout.PLAYER_X_RATIO
    // 총을 든 높이 = 총알이 날아가는 높이
    val shoulderY = groundY - (Layout.LEG_H + Layout.BODY_H * Layout.SHOULDER_RATIO) * u
    val phase = state.phase

    // 땅
    drawLine(
        color = Palette.Ink.copy(alpha = 0.25f),
        start = Offset(0f, groundY),
        end = Offset(size.width, groundY),
        strokeWidth = u * 0.08f,
    )

    // 총잡이: 새 위치로 옮겨 온 직후에는 위에서 살짝 떨어지며 착지한다.
    val drop = shooterArrivedAt?.let { arrived ->
        val t = ((now - arrived) / Layout.ARRIVE_DURATION).coerceIn(0.0, 1.0).toFloat()
        (1f - t) * Layout.ARRIVE_DROP * u
    } ?: 0f
    val muzzle = drawShooter(shooterX(shooterSide), groundY - drop, shoulderY - drop, u, shooterSide)

    // 경고: 총구가 점점 커지며 번쩍인다 → 플레이어는 이 위치로 총알 방향을 안다
    if (phase is Phase.Warning) {
        val progress = ((now - phase.startTime) / (phase.endTime - phase.startTime))
            .coerceIn(0.0, 1.0).toFloat()
        drawCircle(Palette.Accent, radius = u * (0.25f + 0.45f * progress), center = muzzle)
    }

    // 플레이어
    val fallen = phase is Phase.GameOver &&
        (phase.reason == FailReason.HIT || phase.reason == FailReason.WRONG_DIRECTION)
    if (fallen) {
        // 피격: 총알에 밀려 총알 반대쪽으로 90도 쓰러진다.
        // (Compose의 rotate는 양수 = 시계 방향 = 위쪽이 오른쪽으로 넘어감)
        val deg = if (shooterSide == Side.LEFT) 90f else -90f
        rotate(degrees = deg, pivot = Offset(playerX, groundY)) {
            drawPerson(playerX, groundY, u, Palette.Danger)
        }
    } else {
        rotate(degrees = leanDegrees(state, now), pivot = Offset(playerX, groundY)) {
            drawPerson(playerX, groundY, u, Palette.Ink)
        }
    }

    // 총알
    val bulletX = bulletX(state, now, lastFiring, muzzle.x, playerX)
    if (bulletX != null && bulletX in 0f..size.width) {
        drawRoundRect(
            color = Palette.Accent,
            topLeft = Offset(bulletX - Layout.BULLET_W * u / 2, shoulderY - Layout.BULLET_H * u / 2),
            size = Size(Layout.BULLET_W * u, Layout.BULLET_H * u),
            cornerRadius = CornerRadius(Layout.BULLET_H * u / 2),
        )
    }
}

/**
 * 회피 동작으로 몸을 젖히는 각도(도). 오른쪽 회피 = 양수(시계 방향), 왼쪽 = 음수.
 * sin 곡선의 0~π 구간을 쓰면 "젖혔다가 돌아오는" 자연스러운 움직임이 된다.
 */
private fun leanDegrees(state: GameState, now: Double): Float {
    val event = state.lastEvent as? GameEvent.Dodged ?: return 0f
    val t = (now - event.time) / GameConfig.DODGE_ANIM_DURATION
    if (t !in 0.0..1.0) return 0f
    val sign = if (event.direction == DodgeDirection.RIGHT) 1f else -1f
    return (sin(PI * t) * Layout.LEAN_MAX_DEG).toFloat() * sign
}

/**
 * 총알의 현재 가로 위치(픽셀). 그리지 않아야 하면 null.
 *
 * - 발사 중: 총구 → 플레이어까지 시간 비율만큼 이동
 * - 회피 직후(대기 중): 같은 속도로 플레이어 뒤쪽으로 계속 날아감 (화면 밖에서 사라짐)
 * - 그 외(경고, 실패 등): 그리지 않음
 *
 * 총구가 오른쪽에 있으면 startX > targetX라서, 같은 공식으로 자동으로 왼쪽으로 날아간다.
 */
private fun bulletX(
    state: GameState,
    now: Double,
    lastFiring: Phase.Firing?,
    startX: Float,
    targetX: Float,
): Float? {
    val f = lastFiring ?: return null
    val flying = state.phase is Phase.Firing ||
        (state.phase is Phase.Waiting && state.lastEvent is GameEvent.Dodged)
    if (!flying) return null
    val progress = ((now - f.fireTime) / (f.hitTime - f.fireTime)).toFloat()
    if (progress < 0f) return null
    // 선형 보간(lerp): progress 0 → 총구, 1 → 플레이어, 1보다 크면 그 너머
    return startX + (targetX - startX) * progress
}

/**
 * 사람 실루엣(다리, 몸통, 머리)을 그린다. 도형만 사용 (CLAUDE.md: 기존 캐릭터 모방 금지).
 * @param x 인물 중심의 가로 위치
 * @param footY 발바닥의 세로 위치
 */
private fun DrawScope.drawPerson(x: Float, footY: Float, u: Float, color: Color) {
    // 다리
    drawRect(
        color = color,
        topLeft = Offset(x - Layout.LEG_W * u / 2, footY - Layout.LEG_H * u),
        size = Size(Layout.LEG_W * u, Layout.LEG_H * u),
    )
    // 몸통
    val bodyTop = footY - (Layout.LEG_H + Layout.BODY_H) * u
    drawRoundRect(
        color = color,
        topLeft = Offset(x - Layout.BODY_W * u / 2, bodyTop),
        size = Size(Layout.BODY_W * u, Layout.BODY_H * u),
        cornerRadius = CornerRadius(u * 0.2f),
    )
    // 머리 (몸통과 살짝 띄워서 목처럼 보이게)
    drawCircle(color, radius = Layout.HEAD_R * u, center = Offset(x, bodyTop - Layout.HEAD_R * u * 1.15f))
}

/**
 * 총잡이를 그린다: 기본 실루엣 + 모자 + 플레이어 쪽으로 뻗은 팔과 총.
 *
 * 그림은 "오른쪽을 보는 모습" 하나만 만들고, 오른쪽에 설 때는 좌우 반전(scaleX = -1)해서 쓴다.
 * 같은 그림을 두 벌 만들지 않아도 되므로 코드가 짧아지고 실수가 줄어든다.
 *
 * @param side 총잡이가 선 쪽. 왼쪽에 서면 오른쪽을, 오른쪽에 서면 왼쪽을 본다.
 * @return 총구 끝 위치 (경고 섬광과 총알이 여기서 시작)
 */
private fun DrawScope.drawShooter(x: Float, footY: Float, shoulderY: Float, u: Float, side: Side): Offset {
    val color = Palette.Ink
    val facing = if (side == Side.LEFT) 1f else -1f
    val armLen = 1.3f * u
    val barrelLen = 0.7f * u

    scale(scaleX = facing, scaleY = 1f, pivot = Offset(x, footY)) {
        drawPerson(x, footY, u, color)

        // 모자: 챙(넓고 얇은 사각형) + 윗부분
        val headCenterY = footY - (Layout.LEG_H + Layout.BODY_H) * u - Layout.HEAD_R * u * 1.15f
        val brimY = headCenterY - Layout.HEAD_R * u * 0.55f
        drawRect(color, topLeft = Offset(x - 0.8f * u, brimY), size = Size(1.6f * u, 0.15f * u))
        drawRoundRect(
            color,
            topLeft = Offset(x - 0.42f * u, brimY - 0.55f * u),
            size = Size(0.84f * u, 0.6f * u),
            cornerRadius = CornerRadius(0.12f * u),
        )

        // 팔: 어깨에서 앞쪽으로 뻗음
        val armH = 0.24f * u
        drawRect(color, topLeft = Offset(x, shoulderY - armH / 2), size = Size(armLen, armH))

        // 총: 총신 + 손잡이
        val barrelH = 0.22f * u
        val gunX = x + armLen
        drawRect(color, topLeft = Offset(gunX, shoulderY - barrelH / 2), size = Size(barrelLen, barrelH))
        drawRect(color, topLeft = Offset(gunX, shoulderY), size = Size(0.22f * u, 0.4f * u))
    }

    // 반전은 그리기에만 적용되므로, 총구 좌표는 방향을 곱해서 직접 계산한다.
    return Offset(x + facing * (armLen + barrelLen), shoulderY)
}
