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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * 카메라 모드 전체 화면: 셀카 배경 + (중립 자세 보정 → 게임).
 *
 * 카메라([FaceCamera])는 보정 화면과 게임 화면이 "같이" 쓴다.
 * 보정이 끝나고 게임으로 넘어갈 때 카메라를 껐다 켜면 0.5~1초 정도 화면이 깜빡이므로,
 * 카메라를 이 함수 맨 아래에 한 번만 깔고 그 위에 보정/게임 화면을 바꿔 끼운다.
 *
 * @param showTiltDebug 게임 중 기울기 각도를 표시할지 (튜닝용)
 * @param onExit 메뉴로 돌아갈 때
 * @param onCameraError 카메라를 켤 수 없을 때 (메뉴로 돌아가서 안내)
 */
@Composable
fun CameraGameScreen(
    showTiltDebug: Boolean,
    onExit: () -> Unit,
    onCameraError: (String) -> Unit,
) {
    val faceInput = remember { FaceDodgeInput() }
    val calibrator = remember { TiltCalibrator() }

    /** true = 보정 중, false = 게임 중 */
    var calibrating by remember { mutableStateOf(true) }
    var calibrationProgress by remember { mutableFloatStateOf(0f) }
    var faceVisible by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        // 맨 아래: 셀카 화면. 얼굴 정보가 들어올 때마다 보정 중이면 보정기에, 게임 중이면 입력에 넘긴다.
        // (보정 중에는 입력에 넘기지 않는다: 보정 전 기준값으로 판정이 돌면 엉뚱한 잠김 상태가 남을 수 있어서)
        FaceCamera(
            modifier = Modifier.fillMaxSize(),
            onFace = { sample, timeNanos ->
                faceVisible = sample != null
                if (calibrating) {
                    val now = timeNanos / 1_000_000_000.0
                    val baseline = calibrator.onAngle(sample?.angleDeg, now)
                    calibrationProgress = calibrator.progress(now)
                    if (baseline != null) {
                        faceInput.setBaseline(baseline)
                        calibrating = false
                    }
                } else {
                    faceInput.onFace(sample, timeNanos)
                }
            },
            onError = onCameraError,
        )

        if (calibrating) {
            CalibrationOverlay(progress = calibrationProgress, faceVisible = faceVisible, onExit = onExit)
        } else {
            GameScreen(
                input = faceInput,
                onExit = onExit,
                dodgeWindowBonus = GameConfig.FACE_DODGE_WINDOW_BONUS,
                cameraBackground = true,
                face = { faceInput.face },
                debugText = if (showTiltDebug) ({ faceInput.debugText }) else null,
            )
        }
    }
}

/**
 * 중립 자세 보정 화면 (셀카 위에 겹쳐 그림).
 * 얼굴을 안내 원 안에 두고 2초간 정면을 보면 끝난다.
 */
@Composable
private fun CalibrationOverlay(progress: Float, faceVisible: Boolean, onExit: () -> Unit) {
    // 뒤로가기 → 메뉴
    BackHandler(onBack = onExit)

    Box(Modifier.fillMaxSize()) {
        // 얼굴 위치 안내 원: 게임 화면과 같은 비율(Layout)을 써서 보정 때와 게임 때 위치가 같게 한다.
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Palette.Background.copy(alpha = Layout.CAMERA_SCRIM_ALPHA))
            drawCircle(
                color = if (faceVisible) Palette.Accent else Palette.Ink.copy(alpha = 0.6f),
                radius = size.width * Layout.PLAYER_HEAD_R_RATIO,
                center = Offset(size.width / 2f, size.height * Layout.PLAYER_HEAD_Y_RATIO),
                style = Stroke(width = size.width * 0.01f),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
        ) {
            Spacer(Modifier.height(48.dp))
            Column(
                modifier = Modifier
                    .background(Palette.Background.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = if (faceVisible) "그대로 정면을 봐 주세요" else "얼굴을 아래 원 안에 맞춰 주세요",
                    color = Palette.Ink,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "평소 자세를 기준으로 기울기를 재요 (2초)",
                    color = Palette.Ink.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(16.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = Palette.Accent,
                    trackColor = Palette.Ink.copy(alpha = 0.2f),
                )
                TextButton(onClick = onExit) { Text("메뉴로", color = Palette.Ink) }
            }
        }
    }
}
