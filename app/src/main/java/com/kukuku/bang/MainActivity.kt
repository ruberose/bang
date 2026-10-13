package com.kukuku.bang

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch

/**
 * 앱의 시작점이 되는 화면(Activity).
 *
 * Compose를 쓰면 화면 하나에 Activity 하나만 두고,
 * 그 안의 내용은 전부 @Composable 함수로 그린다.
 * 화면 전환(메뉴 ↔ 게임)은 [BangApp]이 담당한다.
 */
class MainActivity : ComponentActivity() {

    /**
     * Activity가 처음 만들어질 때 호출된다.
     * 여기서 setContent { } 로 Compose 화면을 연결한다.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 상태바/내비게이션바 뒤까지 화면을 꽉 채운다 (게임은 전체 화면이 자연스럽기 때문).
        // SystemBarStyle.dark: 게임 배경이 항상 어두우므로, 폰이 라이트 모드여도
        // 상태바 아이콘(시계, 배터리)을 밝은 색으로 고정해서 잘 보이게 한다.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        val settings = SettingsRepository(applicationContext)
        setContent {
            MaterialTheme {
                BangApp(settings)
            }
        }
    }
}

/** 지금 보여줄 화면 */
private enum class Screen { MENU, TAP_GAME, CAMERA_GAME }

/**
 * 앱 전체 화면 흐름: 메뉴 → (터치 게임 | 카메라 게임) → 메뉴.
 *
 * 카메라 권한 흐름 (SPEC: 카메라 모드를 고르는 순간에만 요청):
 * - 이미 허용됨 → 바로 카메라 게임
 * - 아직 없음 → 권한 요청 창 → 허용하면 카메라 게임 / 거절하면 터치 게임 + 안내 문구
 */
@Composable
private fun BangApp(settings: SettingsRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 저장된 설정을 구독. initial: 저장소를 읽기 전 잠깐 쓸 기본값.
    val lastMode by settings.inputMode.collectAsState(initial = InputMode.TAP)
    val showTiltDebug by settings.showTiltDebug.collectAsState(initial = false)

    var screen by remember { mutableStateOf(Screen.MENU) }
    /** 터치 게임 상단에 띄울 안내 (권한 거절 시) */
    var tapNotice by remember { mutableStateOf<String?>(null) }
    /** 메뉴에 띄울 안내 (카메라 오류 시) */
    var menuNotice by remember { mutableStateOf<String?>(null) }

    // 권한 요청 창을 띄우고 결과를 받는 런처
    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            screen = Screen.CAMERA_GAME
        } else {
            // 거절: 게임은 터치 모드로 계속 진행한다 (불안 유도 없이 담백하게 안내)
            tapNotice = "카메라 권한이 없어서 터치 모드로 진행해요"
            screen = Screen.TAP_GAME
        }
    }

    /** 메뉴에서 입력 방식을 골랐을 때 */
    fun select(mode: InputMode) {
        menuNotice = null
        scope.launch { settings.setInputMode(mode) }   // 다음 실행 때 강조 표시용으로 저장
        when (mode) {
            InputMode.TAP -> {
                tapNotice = null
                screen = Screen.TAP_GAME
            }
            InputMode.FACE -> {
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
                if (granted) screen = Screen.CAMERA_GAME
                else cameraPermission.launch(Manifest.permission.CAMERA)
            }
        }
    }

    val backToMenu = { screen = Screen.MENU }

    when (screen) {
        Screen.MENU -> MenuScreen(
            lastMode = lastMode,
            showTiltDebug = showTiltDebug,
            notice = menuNotice,
            onSelect = ::select,
            onToggleTiltDebug = { show -> scope.launch { settings.setShowTiltDebug(show) } },
        )

        Screen.TAP_GAME -> GameScreen(
            input = remember { TapDodgeInput() },
            onExit = backToMenu,
            notice = tapNotice,
        )

        Screen.CAMERA_GAME -> CameraGameScreen(
            showTiltDebug = showTiltDebug,
            onExit = backToMenu,
            onCameraError = { message ->
                menuNotice = "$message. 터치 모드로 플레이해 주세요."
                screen = Screen.MENU
            },
        )
    }
}
