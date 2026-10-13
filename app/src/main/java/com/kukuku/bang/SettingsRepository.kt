package com.kukuku.bang

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** 회피 입력 방식 */
enum class InputMode {
    /** 화면 왼쪽/오른쪽 절반 탭 */
    TAP,

    /** 전면 카메라로 고개 기울이기 */
    FACE,
}

/**
 * 앱 전체에서 하나만 쓰는 설정 저장소(DataStore 파일 "settings").
 * `by preferencesDataStore`는 같은 파일을 여러 번 열지 않도록 최상위에 한 번만 선언해야 한다.
 */
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * 설정값 저장/불러오기 (CLAUDE.md: 저장은 DataStore).
 *
 * DataStore는 값을 Flow로 돌려준다. 값이 바뀌면 Flow가 새 값을 다시 보내 주므로,
 * 화면은 collectAsState로 구독만 하면 저장 직후 자동으로 갱신된다.
 *
 * 개인정보는 저장하지 않는다. 입력 방식과 디버그 표시 여부만 기기 안에 저장한다.
 */
class SettingsRepository(private val context: Context) {

    /** 저장 키 모음. 키 이름을 바꾸면 기존 저장값을 못 읽으니 바꾸지 말 것. */
    private object Keys {
        val INPUT_MODE = stringPreferencesKey("input_mode")
        val SHOW_TILT_DEBUG = booleanPreferencesKey("show_tilt_debug")
    }

    /** 마지막으로 고른 입력 방식. 저장된 값이 없거나 이상하면 터치. */
    val inputMode: Flow<InputMode> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.INPUT_MODE]?.let { name -> InputMode.entries.firstOrNull { it.name == name } }
            ?: InputMode.TAP
    }

    /** 카메라 모드에서 기울기 각도를 화면에 표시할지 (튜닝용) */
    val showTiltDebug: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.SHOW_TILT_DEBUG] ?: false
    }

    /** 입력 방식을 저장한다. suspend: 파일 쓰기라서 화면을 멈추지 않게 코루틴에서 실행. */
    suspend fun setInputMode(mode: InputMode) {
        context.settingsDataStore.edit { it[Keys.INPUT_MODE] = mode.name }
    }

    /** 디버그 각도 표시 여부를 저장한다. */
    suspend fun setShowTiltDebug(show: Boolean) {
        context.settingsDataStore.edit { it[Keys.SHOW_TILT_DEBUG] = show }
    }
}
