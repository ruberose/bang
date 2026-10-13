package com.kukuku.bang

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * 시작 메뉴: 입력 방식(터치 / 카메라)을 고른다.
 *
 * 마지막으로 고른 방식의 버튼을 채워진 버튼으로 강조해서, 다음에 들어왔을 때 바로 누르기 쉽게 한다.
 *
 * @param lastMode 마지막으로 고른 입력 방식 (DataStore에 저장된 값)
 * @param showTiltDebug 카메라 모드 각도 표시 스위치 상태
 * @param notice 메뉴에 띄울 안내 (예: 카메라를 켤 수 없었다는 안내). 없으면 null.
 * @param onSelect 입력 방식을 골랐을 때
 * @param onToggleTiltDebug 각도 표시 스위치를 바꿨을 때
 */
@Composable
fun MenuScreen(
    lastMode: InputMode,
    showTiltDebug: Boolean,
    notice: String?,
    onSelect: (InputMode) -> Unit,
    onToggleTiltDebug: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Palette.Background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "총잡이 피하기",
            color = Palette.Ink,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "총알이 날아오는 반대쪽으로 피하세요",
            color = Palette.Ink.copy(alpha = 0.7f),
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(48.dp))

        ModeButton(
            title = "터치로 하기",
            description = "화면 왼쪽/오른쪽을 탭해서 피해요",
            highlighted = lastMode == InputMode.TAP,
            onClick = { onSelect(InputMode.TAP) },
        )
        Spacer(Modifier.height(16.dp))
        ModeButton(
            title = "카메라로 하기",
            description = "셀카 화면을 보며 고개를 기울여 피해요",
            highlighted = lastMode == InputMode.FACE,
            onClick = { onSelect(InputMode.FACE) },
        )

        if (notice != null) {
            Spacer(Modifier.height(16.dp))
            Text(notice, color = Palette.Accent, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }

        Spacer(Modifier.height(40.dp))
        // 튜닝용 스위치: 실제 기기에서 임계값을 맞출 때 각도를 보기 위한 것
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "카메라 모드 기울기 각도 표시",
                color = Palette.Ink.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.padding(horizontal = 8.dp))
            Switch(
                checked = showTiltDebug,
                onCheckedChange = onToggleTiltDebug,
                colors = SwitchDefaults.colors(checkedTrackColor = Palette.Accent),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "카메라 영상은 기기 안에서만 쓰이고 저장하거나 전송하지 않아요.",
            color = Palette.Ink.copy(alpha = 0.5f),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * 입력 방식 선택 버튼 하나. [highlighted]면 채워진 버튼, 아니면 테두리 버튼.
 * 두 버튼 크기를 같게 해서 어느 하나를 실수로 누르기 쉽게 만들지 않는다.
 */
@Composable
private fun ModeButton(title: String, description: String, highlighted: Boolean, onClick: () -> Unit) {
    val content: @Composable () -> Unit = {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(description, style = MaterialTheme.typography.bodySmall)
        }
    }
    val modifier = Modifier.fillMaxWidth()
    if (highlighted) {
        Button(
            onClick = onClick,
            modifier = modifier,
            colors = ButtonDefaults.buttonColors(containerColor = Palette.Accent, contentColor = Palette.Background),
        ) { content() }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Palette.Ink),
        ) { content() }
    }
}
