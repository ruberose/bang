package com.kukuku.bang

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * 색 팔레트 (SPEC 8장: 3~4색으로 제한).
 * 색을 한곳에 모아 두면 나중에 분위기를 바꿀 때 여기만 고치면 된다.
 * internal: 메뉴/보정 화면에서도 같은 색을 쓰도록 앱 모듈 안에서 공유한다.
 */
internal object Palette {
    /** 배경: 어두운 남색. 다음 단계에서 셀카 카메라 화면으로 바뀔 자리. */
    val Background = Color(0xFF1B1B2F)

    /** 총잡이 실루엣, 글자: 크림색 */
    val Ink = Color(0xFFF2E9DC)

    /** 강조: 총구 섬광, 조준점, 총알 (주황) */
    val Accent = Color(0xFFF2A541)

    /** 위험: 피격 (빨강) */
    val Danger = Color(0xFFE4572E)
}

/**
 * 화면 배치 비율. 게임 규칙이 아니라 "그림 크기"라서 GameConfig가 아닌 여기에 둔다.
 *
 * 정면 시점 구성:
 * - 화면 위쪽 가운데: 멀리 서서 나를 향해 총을 겨누는 총잡이
 * - 화면 아래쪽: "나" (지금은 반투명 실루엣, 다음 단계에서 셀카 화면의 내 얼굴)
 * - 총알은 총구에서 출발해 내 머리 왼쪽 또는 오른쪽으로, 점점 커지며 다가온다 (원근감)
 *
 * 기기마다 화면 크기가 다르므로 픽셀 값을 쓰지 않고 화면 너비/높이의 비율을 쓴다.
 * 총잡이 크기는 "1유닛(u) = 화면 너비 × UNIT_RATIO"의 배수로 정한다.
 */
internal object Layout {
    /** 1유닛 = 화면 너비 × 이 값 (총잡이 크기 기준) */
    const val UNIT_RATIO = 0.06f

    /** 총잡이 발바닥 높이 (화면 높이 비율). 멀리 서 있는 느낌을 주려고 화면 위쪽에 둔다. */
    const val SHOOTER_FOOT_Y_RATIO = 0.47f

    // 총잡이 크기 (유닛 단위, 정면 모습)
    const val LEG_W = 0.55f
    const val LEG_GAP = 0.2f
    const val LEG_H = 2.2f
    const val BODY_W = 1.7f
    const val BODY_H = 2.4f
    const val HEAD_R = 0.62f
    const val ARM_W = 0.42f

    /** 내 머리 중심 위치 (화면 비율). 셀카를 들면 얼굴이 대략 이 근처에 온다. */
    const val PLAYER_HEAD_Y_RATIO = 0.74f
    const val PLAYER_HEAD_R_RATIO = 0.17f   // 화면 너비 비율

    /**
     * 총알이 노리는 점: 머리 중심에서 좌/우로 "머리 반지름 × 이 값"만큼.
     * 1보다 작아서 가만히 있으면 머리 안쪽(맞음), 반대로 기울이면 머리가 이 점에서 벗어난다.
     * 머리 크기 기준 비율이라 카메라 모드에서 얼굴이 크든 작든 같은 느낌이 된다.
     */
    const val TARGET_OFFSET_HEAD_RATIO = 0.65f

    /** 조준점(과녁) 반지름 = 머리 반지름 × 이 값 */
    const val RETICLE_HEAD_RATIO = 0.35f

    /**
     * 회피 동작: 내 실루엣을 화면 맨 아래 가운데를 축으로 젖히는 최대 각도(도).
     * 28도면 머리가 화면 너비의 약 1/4 옆으로 이동해 총알이 노린 점을 벗어난다.
     */
    const val LEAN_MAX_DEG = 28f

    /** 총알 반지름 (유닛): 출발할 때(멀리) → 도착할 때(코앞). 다가올수록 커져야 원근감이 난다. */
    const val BULLET_START_R = 0.12f
    const val BULLET_END_R = 1.6f

    /** 회피 후 총알이 내 옆을 지나 이 진행률까지 날아가다 사라진다 (1 = 도착 지점) */
    const val BULLET_OVERSHOOT = 1.4f

    /** 피격 시 화면 전체가 붉게 번쩍이는 시간(초) */
    const val HIT_FLASH_DURATION = 0.35

    /** 셀카 화면 위에 덮는 어두운 막의 불투명도 (0 = 투명, 1 = 완전히 가림) */
    const val CAMERA_SCRIM_ALPHA = 0.35f
}

/**
 * 게임 화면 전체 (정면 시점). Canvas로 장면을 그리고, [input]에서 오는 회피 입력을 엔진에 전달한다.
 *
 * 역할 분담:
 * - 규칙 계산(판정, 단계 진행, 난이도, 총알 방향)은 전부 [GameEngine]이 한다.
 * - 입력을 "방향 + 시각"으로 만드는 일은 [DodgeInput] 구현체가 한다.
 * - 이 함수는 시간을 재서 엔진에 넘기고, 돌려받은 상태를 그리기만 한다.
 *   (그래서 옆모습 → 정면 시점으로 바꿀 때 엔진은 한 줄도 고치지 않았다.)
 *
 * 시간 기준:
 * - 화면이 처음 만들어진 순간의 System.nanoTime()을 0초로 삼는다.
 * - 매 프레임 시각(withFrameNanos)과 입력 시각(DodgeEvent.timeNanos)이
 *   같은 시계(System.nanoTime) 기준이라 두 값을 그대로 비교할 수 있다.
 *
 * @param input 회피 입력 방식 (탭 / 얼굴)
 * @param onExit 메뉴로 돌아갈 때 호출 (뒤로가기, 실패 화면의 "메뉴" 버튼)
 * @param dodgeWindowBonus 입력 지연 보정용 회피 구간 여유(초). 카메라 모드에서만 0보다 크다.
 * @param cameraBackground true면 배경을 칠하지 않는다 → 뒤에 깔린 셀카 화면이 보인다.
 * @param notice 상단에 작게 띄울 안내 (예: 카메라 권한 거절 안내). 없으면 null.
 * @param face 지금 내 얼굴 위치를 돌려주는 함수 (카메라 모드). 터치 모드면 null → 고정 위치 사용.
 * @param debugText 화면 구석에 띄울 디버그 문구를 돌려주는 함수. 표시하지 않으면 null.
 *   문자열이 아니라 "함수"로 받는 이유: 값이 매 프레임 바뀌는데, 함수로 넘기면
 *   그 글자를 그리는 부분만 다시 그려지고 화면 전체가 다시 구성되지 않는다.
 */
@Composable
fun GameScreen(
    input: DodgeInput,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    dodgeWindowBonus: Double = 0.0,
    cameraBackground: Boolean = false,
    notice: String? = null,
    face: (() -> FaceBox?)? = null,
    debugText: (() -> String)? = null,
) {
    // remember: 화면이 다시 그려져도(리컴포지션) 값이 초기화되지 않게 보관한다.
    val engine = remember { GameEngine(dodgeWindowBonus = dodgeWindowBonus) }
    val startNanos = remember { System.nanoTime() }

    /** 현재 게임 상태 */
    var state by remember { mutableStateOf(engine.newGame(0.0)) }

    /** 현재 시각(초). 매 프레임 갱신 → Canvas가 이 값을 읽으므로 매 프레임 다시 그려진다. */
    var now by remember { mutableDoubleStateOf(0.0) }

    /**
     * 마지막으로 발사된 총알 정보.
     * 회피 성공 후에는 엔진 상태가 바로 "대기"로 바뀌지만,
     * 화면에서는 총알이 내 옆을 스쳐 지나가는 모습을 계속 보여주고 싶어서 따로 기억해 둔다.
     */
    var lastFiring by remember { mutableStateOf<Phase.Firing?>(null) }

    /**
     * 총잡이가 조준한 순간의 얼굴 위치 (카메라 모드).
     *
     * 경고가 시작되는 순간 한 번만 정하고 그 뒤로는 바꾸지 않는다.
     * 조준점이 얼굴을 계속 따라다니면, 고개를 기울여 피해도 총알이 따라와 맞는 것처럼 보이기 때문이다.
     * "총잡이는 그 순간 내 머리가 있던 곳을 쐈고, 나는 거기서 머리를 뺐다"가 되어야 한다.
     */
    var aimFace by remember { mutableStateOf<FaceBox?>(null) }

    /** 새 상태를 반영하면서, 화면 연출용 정보(총알, 조준 위치)도 기억한다. */
    fun applyState(newState: GameState) {
        val np = newState.phase
        // 경고 단계에 "새로 들어온" 순간에 조준 위치를 고정한다. 얼굴이 안 보이면 null → 기본 위치.
        if (np is Phase.Warning && state.phase !is Phase.Warning) aimFace = face?.invoke()
        (np as? Phase.Firing)?.let { lastFiring = it }
        state = newState
    }

    /** System.nanoTime()을 게임 시각(초)으로 변환 */
    fun toGameTime(nanos: Long): Double = (nanos - startNanos) / 1_000_000_000.0

    /**
     * 회피 입력 처리. 입력 구현체가 찍어 준 "입력 순간의 시각"을 그대로 쓴다.
     * 프레임 단위(약 16ms)로 반올림되면 회피 구간 끝자락 판정이 부정확해지기 때문이다.
     */
    fun handleDodge(event: DodgeEvent) {
        // 판이 끝난 뒤의 회피 입력은 무시한다. 재시작은 "다시 시작" 버튼으로만 한다.
        // (고개 기울이기 모드에서 실패 직후 고개를 움직이다가 의도치 않게 새 판이 시작되지 않게)
        if (state.phase is Phase.GameOver) return
        applyState(engine.onDodge(state, event.direction, toGameTime(event.timeNanos)))
    }

    /** 새 판 시작 (임시 실패 화면의 "다시 시작" 버튼. 6단계에서 정식 실패 화면으로 교체) */
    fun restart() {
        lastFiring = null
        applyState(engine.newGame(toGameTime(System.nanoTime())))
    }

    // 뒤로가기 버튼 → 메뉴로
    BackHandler(onBack = onExit)

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
            // 카메라 모드에서는 배경을 칠하지 않아야 뒤의 셀카 화면이 보인다.
            .then(if (cameraBackground) Modifier else Modifier.background(Palette.Background))
            // 입력 구현체가 필요한 터치 처리를 붙인다 (탭 입력이면 화면 전체 터치).
            .then(input.modifier)
    ) {
        // ── 장면 그리기 ──
        Canvas(modifier = Modifier.fillMaxSize()) {
            // face?.invoke()를 그리기 안에서 읽으므로, 얼굴이 움직이면 그림만 다시 그려진다.
            drawScene(state, now, lastFiring, cameraBackground, liveFace = face?.invoke(), aimFace = aimFace)
        }

        // ── 글자 (상단 연속 기록 / 실패 문구 / 하단 안내) ──
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
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = state.streak.toString(),
                    color = Palette.Ink,
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                )
                if (notice != null) {
                    Text(
                        text = notice,
                        color = Palette.Ink.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            // 중앙: 실패 시 문구.
            // 총잡이 그림과 겹치므로 반투명 배경을 깔아 글자가 잘 읽히게 한다.
            // (다음 단계에서 셀카 배경이 깔리면 이 배경이 더 중요해진다)
            val phase = state.phase
            if (phase is Phase.GameOver) {
                Column(
                    modifier = Modifier
                        .background(Palette.Background.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = failMessage(phase.reason),
                        color = Palette.Danger,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(16.dp))
                    // 버튼은 실패 직후 잠깐 비활성 → 회피하려던 손가락에 실수로 눌리지 않게 한다.
                    val ready = now - phase.time >= GameConfig.RESTART_DELAY
                    Button(
                        onClick = { restart() },
                        enabled = ready,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Palette.Accent,
                            contentColor = Palette.Background,
                        ),
                    ) {
                        Text("다시 시작", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = onExit, enabled = ready) {
                        Text("메뉴", color = Palette.Ink)
                    }
                }
            } else {
                // 실패 문구가 없을 때도 같은 자리를 차지하게 해서 위아래 글자가 흔들리지 않게 한다.
                Spacer(Modifier.height(1.dp))
            }

            // 하단: 조작 안내 (입력 방식마다 다르므로 입력 구현체가 정한 문구 사용)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // 디버그: 현재 기울기 (튜닝용, 메뉴에서 켰을 때만)
                if (debugText != null) {
                    Text(
                        text = debugText(),
                        color = Palette.Accent,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .background(Palette.Background.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Text(
                    text = input.hint,
                    color = Palette.Ink,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .background(Palette.Background.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
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

/** [side]를 화면 가로 부호로: 왼쪽 = -1, 오른쪽 = +1 */
private fun Side.sign(): Float = if (this == Side.LEFT) -1f else 1f

/**
 * 장면 전체를 그린다: 총잡이 → 경고(섬광, 조준점) → 나 → 총알 → 피격 섬광.
 * 그리는 순서가 곧 겹치는 순서다 (나중에 그린 것이 위에 보인다).
 *
 * @param state 현재 게임 상태
 * @param now 현재 시각(초)
 * @param lastFiring 마지막으로 발사된 총알 (회피 후 스쳐 지나가는 모습, 실패 후 조준 자세 유지용)
 * @param cameraBackground 뒤에 셀카 화면이 깔려 있는지.
 *   true면 "나" 실루엣 대신 실제 얼굴을 둘러싼 원만 그린다 (진짜 내 얼굴이 보이므로).
 * @param liveFace 지금 얼굴 위치 (카메라 모드, 안 보이면 null)
 * @param aimFace 총잡이가 조준한 순간의 얼굴 위치 (카메라 모드, 그때 안 보였으면 null)
 */
private fun DrawScope.drawScene(
    state: GameState,
    now: Double,
    lastFiring: Phase.Firing?,
    cameraBackground: Boolean,
    liveFace: FaceBox?,
    aimFace: FaceBox?,
) {
    val u = size.width * Layout.UNIT_RATIO
    val phase = state.phase

    // 기본 머리 위치: 터치 모드, 또는 카메라 모드에서 얼굴이 안 보일 때 쓰는 고정 위치
    val defaultHead = ScreenCircle(
        x = size.width / 2f,
        y = size.height * Layout.PLAYER_HEAD_Y_RATIO,
        r = size.width * Layout.PLAYER_HEAD_R_RATIO,
    )
    // 카메라 이미지 좌표 → 화면 좌표 (셀카 미리보기와 같은 FILL_CENTER 방식)
    fun FaceBox.onScreen() = FaceMapping.toScreen(this, size.width, size.height)
    // 총잡이가 조준한 머리: 조준 순간의 얼굴이 있으면 거기, 없으면 기본 위치
    val aimHead = aimFace?.onScreen() ?: defaultHead

    // 셀카 위에 반투명 어두운 막을 한 겹 깔아서 총잡이와 총알이 잘 보이게 한다.
    if (cameraBackground) drawRect(Palette.Background.copy(alpha = Layout.CAMERA_SCRIM_ALPHA))

    // 총잡이가 지금 어느 쪽을 겨누고 있는지. null이면 총을 내리고 서 있음(대기).
    val aimSide: Side? = when (phase) {
        is Phase.Warning -> phase.side
        is Phase.Firing -> phase.side
        is Phase.GameOver -> lastFiring?.side   // 실패 화면에서도 마지막 자세 유지
        is Phase.Waiting -> null
    }

    // 총잡이 (총구 위치를 돌려받아 섬광/총알 시작점으로 쓴다)
    val muzzle = drawShooter(u, aimSide)

    // 총알이 노리는 점: 조준한 머리의 왼쪽 또는 오른쪽 부분 (머리 크기에 비례)
    fun targetOf(side: Side) =
        Offset(aimHead.x + side.sign() * aimHead.r * Layout.TARGET_OFFSET_HEAD_RATIO, aimHead.y)

    // 경고: 총구 섬광 + 노리는 곳에 조준점 → 플레이어가 방향을 미리 알 수 있다
    if (phase is Phase.Warning && muzzle != null) {
        val progress = ((now - phase.startTime) / (phase.endTime - phase.startTime))
            .coerceIn(0.0, 1.0).toFloat()
        drawCircle(Palette.Accent, radius = u * (0.2f + 0.4f * progress), center = muzzle)
        drawReticle(
            targetOf(phase.side),
            radius = aimHead.r * Layout.RETICLE_HEAD_RATIO,
            alpha = 0.4f + 0.6f * progress,
        )
    }

    // 나. 피격이면 빨갛게.
    val hurt = phase is Phase.GameOver &&
        (phase.reason == FailReason.HIT || phase.reason == FailReason.WRONG_DIRECTION)
    if (cameraBackground) {
        // 셀카 모드: 진짜 얼굴이 보이므로 실제 얼굴을 둘러싼 원만 그린다 (인식이 잘 되는지 확인용).
        // 얼굴이 안 보이면 기본 위치에 흐린 원 → "여기에 얼굴을 맞춰 주세요" 안내 역할.
        // 실제 고개 움직임이 곧 회피 동작이라 젖히는 연출은 하지 않는다.
        val head = liveFace?.onScreen()
        drawCircle(
            color = when {
                hurt -> Palette.Danger
                head != null -> Palette.Ink.copy(alpha = 0.5f)
                else -> Palette.Ink.copy(alpha = 0.2f)
            },
            radius = (head ?: defaultHead).r,
            center = Offset((head ?: defaultHead).x, (head ?: defaultHead).y),
            style = Stroke(width = u * 0.12f),
        )
    } else {
        rotate(degrees = leanDegrees(state, now), pivot = Offset(size.width / 2f, size.height)) {
            drawPlayerPlaceholder(
                Offset(defaultHead.x, defaultHead.y),
                if (hurt) Palette.Danger.copy(alpha = 0.6f) else Palette.Ink.copy(alpha = 0.18f),
            )
        }
    }

    // 총알: 총구 → 노린 점으로, 다가올수록 커진다
    val f = lastFiring
    val p = bulletProgress(state, now, f)
    if (f != null && p != null && muzzle != null) {
        val target = targetOf(f.side)
        // 위치는 직선 보간, 크기는 진행률의 제곱으로 → 처음엔 천천히, 막판에 확 커지는 원근감
        val pos = Offset(muzzle.x + (target.x - muzzle.x) * p, muzzle.y + (target.y - muzzle.y) * p)
        val r = u * (Layout.BULLET_START_R + (Layout.BULLET_END_R - Layout.BULLET_START_R) * p * p)
        drawCircle(Palette.Accent, radius = r, center = pos)
    }

    // 피격 섬광: 화면 전체를 잠깐 붉게
    // (hurt가 true면 컴파일러가 phase를 GameOver로 이미 알고 있어서 phase.time을 바로 쓸 수 있다)
    if (hurt) {
        val t = ((now - phase.time) / Layout.HIT_FLASH_DURATION).toFloat()
        if (t in 0f..1f) drawRect(Palette.Danger.copy(alpha = 0.45f * (1f - t)))
    }
}

/**
 * 총알 진행률. 0 = 총구, 1 = 내 앞 도착. 그리지 않아야 하면 null.
 * - 발사 중: 시간 비율 그대로
 * - 회피 직후(대기 중): 1을 넘어 계속 진행 → 내 옆을 스쳐 지나가는 모습. 일정 이상이면 사라짐.
 * - 그 외(경고, 실패 등): 그리지 않음
 */
private fun bulletProgress(state: GameState, now: Double, f: Phase.Firing?): Float? {
    if (f == null) return null
    val flying = state.phase is Phase.Firing ||
        (state.phase is Phase.Waiting && state.lastEvent is GameEvent.Dodged)
    if (!flying) return null
    val p = ((now - f.fireTime) / (f.hitTime - f.fireTime)).toFloat()
    return if (p in 0f..Layout.BULLET_OVERSHOOT) p else null
}

/**
 * 회피 동작으로 몸을 젖히는 각도(도). 오른쪽 회피 = 양수(시계 방향 = 머리가 오른쪽으로), 왼쪽 = 음수.
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
 * 정면을 보고 선 총잡이를 그린다. 도형만 사용 (CLAUDE.md: 기존 캐릭터 모방 금지).
 *
 * @param aimSide 겨누는 쪽. null이면 양팔을 내리고 서 있다.
 * @return 총구 위치. 총을 내리고 있으면 null.
 */
private fun DrawScope.drawShooter(u: Float, aimSide: Side?): Offset? {
    val c = Palette.Ink
    val cx = size.width / 2f
    val footY = size.height * Layout.SHOOTER_FOOT_Y_RATIO
    val bodyBottom = footY - Layout.LEG_H * u
    val bodyTop = bodyBottom - Layout.BODY_H * u
    val headCenter = Offset(cx, bodyTop - Layout.HEAD_R * u * 1.1f)

    // 다리 두 개
    for (s in listOf(-1f, 1f)) {
        val legX = cx + s * (Layout.LEG_GAP / 2 + Layout.LEG_W / 2) * u
        drawRect(c, topLeft = Offset(legX - Layout.LEG_W * u / 2, bodyBottom), size = Size(Layout.LEG_W * u, Layout.LEG_H * u))
    }
    // 몸통
    drawRoundRect(
        c,
        topLeft = Offset(cx - Layout.BODY_W * u / 2, bodyTop),
        size = Size(Layout.BODY_W * u, Layout.BODY_H * u),
        cornerRadius = CornerRadius(0.3f * u),
    )
    // 머리 + 모자(넓은 챙 + 윗부분)
    drawCircle(c, radius = Layout.HEAD_R * u, center = headCenter)
    val brimY = headCenter.y - Layout.HEAD_R * u * 0.6f
    drawRoundRect(c, topLeft = Offset(cx - 1.25f * u, brimY), size = Size(2.5f * u, 0.22f * u), cornerRadius = CornerRadius(0.11f * u))
    drawRoundRect(c, topLeft = Offset(cx - 0.6f * u, brimY - 0.7f * u), size = Size(1.2f * u, 0.78f * u), cornerRadius = CornerRadius(0.2f * u))

    // 팔: 어깨 위치 (몸통 위쪽 양 끝)
    val shoulderY = bodyTop + 0.35f * u
    fun shoulder(s: Float) = Offset(cx + s * (Layout.BODY_W / 2 - 0.15f) * u, shoulderY)
    fun arm(from: Offset, to: Offset) =
        drawLine(c, from, to, strokeWidth = Layout.ARM_W * u, cap = StrokeCap.Round)

    if (aimSide == null) {
        // 대기: 양팔을 내리고 있다 (총은 아직 안 겨눔)
        for (s in listOf(-1f, 1f)) {
            val sh = shoulder(s)
            arm(sh, Offset(sh.x + s * 0.35f * u, sh.y + 2.0f * u))
        }
        return null
    }

    // 조준: 겨누는 쪽 팔을 앞으로 뻗어 화면 바깥쪽 아래(= 내 머리 옆)를 향한다.
    // 정면에서 보면 팔이 짧게 보이므로(원근) 길이를 짧게 그린다.
    val s = aimSide.sign()
    val other = shoulder(-s)
    arm(other, Offset(other.x - s * 0.35f * u, other.y + 2.0f * u))   // 반대 팔은 내림
    val sh = shoulder(s)
    val hand = Offset(sh.x + s * 1.3f * u, sh.y + 0.6f * u)
    arm(sh, hand)
    // 총: 손에 쥔 작은 사각형 (정면이라 총구 쪽 단면만 보인다)
    drawRoundRect(
        c,
        topLeft = Offset(hand.x - 0.32f * u, hand.y - 0.32f * u),
        size = Size(0.64f * u, 0.64f * u),
        cornerRadius = CornerRadius(0.12f * u),
    )
    return hand
}

/** 조준점(과녁 모양): 바깥 원 + 십자선. [alpha]로 점점 진해지게 한다. */
private fun DrawScope.drawReticle(center: Offset, radius: Float, alpha: Float) {
    val color = Palette.Accent.copy(alpha = alpha)
    val stroke = radius * 0.12f
    drawCircle(color, radius = radius, center = center, style = Stroke(width = stroke))
    drawLine(color, Offset(center.x - radius * 1.4f, center.y), Offset(center.x + radius * 1.4f, center.y), strokeWidth = stroke)
    drawLine(color, Offset(center.x, center.y - radius * 1.4f), Offset(center.x, center.y + radius * 1.4f), strokeWidth = stroke)
}

/**
 * "나"를 나타내는 반투명 실루엣 (머리 + 목 + 어깨).
 * 다음 단계에서 셀카 카메라 화면이 들어오면 실제 내 얼굴이 이 자리를 대신한다.
 */
private fun DrawScope.drawPlayerPlaceholder(headCenter: Offset, color: Color) {
    val headR = size.width * Layout.PLAYER_HEAD_R_RATIO
    // 어깨: 화면 아래로 이어지는 둥근 사각형
    val shoulderTop = headCenter.y + headR * 1.25f
    drawRoundRect(
        color,
        topLeft = Offset(size.width * 0.1f, shoulderTop),
        size = Size(size.width * 0.8f, size.height - shoulderTop + headR),
        cornerRadius = CornerRadius(headR * 0.9f),
    )
    // 목: 머리 아래 끝 ~ 어깨 위 끝 사이만 채운다.
    // (반투명 도형끼리 겹치면 겹친 부분만 진해 보이므로 겹치지 않게 한다)
    drawRect(color, topLeft = Offset(headCenter.x - headR * 0.4f, headCenter.y + headR), size = Size(headR * 0.8f, headR * 0.25f))
    // 머리
    drawCircle(color, radius = headR, center = headCenter)
}
