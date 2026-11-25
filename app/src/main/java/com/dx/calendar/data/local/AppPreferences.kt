package com.dx.calendar.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dx.calendar.data.model.BrandStyle
import com.dx.calendar.data.model.DarkThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.MessageDigest

private val Context.dataStore by preferencesDataStore(name = "dx_calendar_prefs")

data class UserSettings(
    val brandStyle: BrandStyle = BrandStyle.VIVO,
    val darkThemeMode: DarkThemeMode = DarkThemeMode.DARK,
    val startWeekOnMonday: Boolean = false,
    val showLunar: Boolean = true,
    val showWeekNumber: Boolean = false,
    val showCompletedTasks: Boolean = true,
    val peekHiddenInMonth: Boolean = false,
    val routineDefaultHideFromMonth: Boolean = true,
    val customWallpaperUri: String = "",
    val wallpaperAlpha: Float = 0.28f,
    val showMonthGridLines: Boolean = true,
    val requireDualFingerprint: Boolean = true,
    val privacyPinHash: String = "",
    val googleAccountEmail: String = "",
    val hiddenSystemCalIds: Set<String> = emptySet(),
    val widgetDarkStyle: Boolean = true,
    val widgetOpacity: Float = 0.85f
)

class AppPreferences(private val context: Context) {

    companion object {
        private val KEY_BRAND_STYLE = stringPreferencesKey("brand_style")
        private val KEY_DARK_MODE = stringPreferencesKey("dark_mode")
        private val KEY_START_MONDAY = booleanPreferencesKey("start_monday")
        private val KEY_SHOW_LUNAR = booleanPreferencesKey("show_lunar")
        private val KEY_SHOW_WEEK_NUM = booleanPreferencesKey("show_week_num")
        private val KEY_SHOW_COMPLETED_TASKS = booleanPreferencesKey("show_completed_tasks")
        private val KEY_PEEK_HIDDEN = booleanPreferencesKey("peek_hidden_in_month")
        private val KEY_ROUTINE_DEFAULT_HIDE = booleanPreferencesKey("routine_default_hide")
        private val KEY_WALLPAPER_URI = stringPreferencesKey("wallpaper_uri")
        private val KEY_WALLPAPER_ALPHA = floatPreferencesKey("wallpaper_alpha")
        private val KEY_SHOW_GRID_LINES = booleanPreferencesKey("show_grid_lines")
        private val KEY_DUAL_FINGERPRINT = booleanPreferencesKey("require_dual_fingerprint")
        private val KEY_PRIVACY_PIN_HASH = stringPreferencesKey("privacy_pin_hash")
        private val KEY_GOOGLE_EMAIL = stringPreferencesKey("google_account_email")
        private val KEY_HIDDEN_SYS_CALS = stringSetPreferencesKey("hidden_sys_cals")
        private val KEY_WIDGET_DARK = booleanPreferencesKey("widget_dark")
        private val KEY_WIDGET_OPACITY = floatPreferencesKey("widget_opacity")

        fun sha256(input: String): String {
            val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }

    val settingsFlow: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        val brand = runCatching {
            BrandStyle.valueOf(prefs[KEY_BRAND_STYLE] ?: BrandStyle.VIVO.name)
        }.getOrDefault(BrandStyle.VIVO)

        val dark = runCatching {
            DarkThemeMode.valueOf(prefs[KEY_DARK_MODE] ?: DarkThemeMode.DARK.name)
        }.getOrDefault(DarkThemeMode.DARK)

        UserSettings(
            brandStyle = brand,
            darkThemeMode = dark,
            startWeekOnMonday = prefs[KEY_START_MONDAY] ?: false,
            showLunar = prefs[KEY_SHOW_LUNAR] ?: true,
            showWeekNumber = prefs[KEY_SHOW_WEEK_NUM] ?: false,
            showCompletedTasks = prefs[KEY_SHOW_COMPLETED_TASKS] ?: true,
            peekHiddenInMonth = prefs[KEY_PEEK_HIDDEN] ?: false,
            routineDefaultHideFromMonth = prefs[KEY_ROUTINE_DEFAULT_HIDE] ?: true,
            customWallpaperUri = prefs[KEY_WALLPAPER_URI] ?: "",
            wallpaperAlpha = prefs[KEY_WALLPAPER_ALPHA] ?: 0.28f,
            showMonthGridLines = prefs[KEY_SHOW_GRID_LINES] ?: true,
            requireDualFingerprint = prefs[KEY_DUAL_FINGERPRINT] ?: true,
            privacyPinHash = prefs[KEY_PRIVACY_PIN_HASH] ?: "",
            googleAccountEmail = prefs[KEY_GOOGLE_EMAIL] ?: "",
            hiddenSystemCalIds = prefs[KEY_HIDDEN_SYS_CALS] ?: emptySet(),
            widgetDarkStyle = prefs[KEY_WIDGET_DARK] ?: true,
            widgetOpacity = prefs[KEY_WIDGET_OPACITY] ?: 0.85f
        )
    }

    suspend fun getSettingsOnce(): UserSettings = settingsFlow.first()

    suspend fun setBrandStyle(style: BrandStyle) {
        context.dataStore.edit { it[KEY_BRAND_STYLE] = style.name }
    }

    suspend fun setDarkThemeMode(mode: DarkThemeMode) {
        context.dataStore.edit { it[KEY_DARK_MODE] = mode.name }
    }

    suspend fun setStartWeekOnMonday(monday: Boolean) {
        context.dataStore.edit { it[KEY_START_MONDAY] = monday }
    }

    suspend fun setShowLunar(show: Boolean) {
        context.dataStore.edit { it[KEY_SHOW_LUNAR] = show }
    }

    suspend fun setShowWeekNumber(show: Boolean) {
        context.dataStore.edit { it[KEY_SHOW_WEEK_NUM] = show }
    }

    suspend fun setShowCompletedTasks(show: Boolean) {
        context.dataStore.edit { it[KEY_SHOW_COMPLETED_TASKS] = show }
    }

    suspend fun setPeekHiddenInMonth(peek: Boolean) {
        context.dataStore.edit { it[KEY_PEEK_HIDDEN] = peek }
    }

    suspend fun setRoutineDefaultHideFromMonth(hide: Boolean) {
        context.dataStore.edit { it[KEY_ROUTINE_DEFAULT_HIDE] = hide }
    }

    suspend fun setWallpaper(uri: String, alpha: Float) {
        context.dataStore.edit {
            it[KEY_WALLPAPER_URI] = uri
            it[KEY_WALLPAPER_ALPHA] = alpha.coerceIn(0.05f, 0.75f)
        }
    }

    suspend fun setShowMonthGridLines(show: Boolean) {
        context.dataStore.edit { it[KEY_SHOW_GRID_LINES] = show }
    }

    suspend fun setRequireDualFingerprint(require: Boolean) {
        context.dataStore.edit { it[KEY_DUAL_FINGERPRINT] = require }
    }

    suspend fun setPrivacyPin(rawPin: String) {
        context.dataStore.edit {
            it[KEY_PRIVACY_PIN_HASH] = if (rawPin.isBlank()) "" else sha256(rawPin.trim())
        }
    }

    suspend fun setGoogleAccountEmail(email: String) {
        context.dataStore.edit { it[KEY_GOOGLE_EMAIL] = email }
    }

    suspend fun toggleSystemCalendarVisibility(calId: String, visible: Boolean) {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_HIDDEN_SYS_CALS]?.toMutableSet() ?: mutableSetOf()
            if (visible) current.remove(calId) else current.add(calId)
            prefs[KEY_HIDDEN_SYS_CALS] = current
        }
    }

    suspend fun setWidgetAppearance(dark: Boolean, opacity: Float) {
        context.dataStore.edit {
            it[KEY_WIDGET_DARK] = dark
            it[KEY_WIDGET_OPACITY] = opacity.coerceIn(0.2f, 1.0f)
        }
    }
}
