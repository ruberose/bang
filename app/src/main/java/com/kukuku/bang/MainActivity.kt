package com.kukuku.bang

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme

/**
 * 앱의 시작점이 되는 화면(Activity).
 *
 * Compose를 쓰면 화면 하나에 Activity 하나만 두고,
 * 그 안의 내용은 전부 @Composable 함수로 그린다.
 * 실제 게임 화면은 [GameScreen]에 있다.
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
        setContent {
            MaterialTheme {
                GameScreen()
            }
        }
    }
}
