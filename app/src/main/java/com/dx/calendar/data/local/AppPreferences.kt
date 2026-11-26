package com.dx.calendar.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dx.calendar.data.model.BrandStyle
import com.dx.calendar.data.model.DarkThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.security.MessageDigest

private val Context.dataStore by preferencesDataStore(name = "dx_calendar_prefs")

data class UserSettings(
    val brandStyle: BrandStyle = BrandStyle.GOLD_WHITE,
    val darkThemeMode: DarkThemeMode = DarkThemeMode.SYSTEM,
    val startWeekOnMonday: Boolean = false,
    val showLunar: Boolean = true,
    val showWeekNumber: Boolean = false,
    val showCompletedTasks: Boolean = true,
    val peekHiddenInMonth: Boolean = false,
    val routineMedicationFeatureEnabled: Boolean = false,
    val routineDefaultHideFromMonth: Boolean = true,
    val customWallpaperUri: String = "",
    val wallpaperAlpha: Float = 0.28f,
    val showMonthGridLines: Boolean = true,
    val privacyFeatureEnabled: Boolean = false,
    val requireDualFingerprint: Boolean = true,
    val privacyPinHash: String = "",
    val googleAccountEmail: String = "",
    val googleAccountEmails: Set<String> = emptySet(),
    val hiddenSystemCalIds: Set<String> = emptySet(),
    val widgetDarkStyle: Boolean = false,
    val widgetFollowSystemDark: Boolean = true,
    val widgetOpacity: Float = 0.88f,
    val autoCheckUpdate: Boolean = true,
    val dynamicDayIconEnabled: Boolean = true,
    val liveUpdateIslandEnabled: Boolean = true
)

class AppPreferences(context: Context) {

    private val appContext: Context = context.applicationContext ?: context

    companion object {
        private val KEY_BRAND_STYLE = stringPreferencesKey("brand_style")
        private val KEY_DARK_MODE = stringPreferencesKey("dark_mode")
        private val KEY_START_MONDAY = booleanPreferencesKey("start_monday")
        private val KEY_SHOW_LUNAR = booleanPreferencesKey("show_lunar")
        private val KEY_SHOW_WEEK_NUM = booleanPreferencesKey("show_week_num")
        private val KEY_SHOW_COMPLETED_TASKS = booleanPreferencesKey("show_completed_tasks")
        private val KEY_PEEK_HIDDEN = booleanPreferencesKey("peek_hidden_in_month")
        private val KEY_ROUTINE_MED_FEATURE_ENABLED = booleanPreferencesKey("routine_med_feature_enabled")
        private val KEY_LEGACY_MED_DEMO_CLEANED = booleanPreferencesKey("legacy_med_demo_cleaned_v1")
        private val KEY_GOLD_SYSTEM_AUTO_APPLIED_V2 = booleanPreferencesKey("gold_system_auto_applied_v2")
        private val KEY_ROUTINE_DEFAULT_HIDE = booleanPreferencesKey("routine_default_hide")
        private val KEY_WALLPAPER_URI = stringPreferencesKey("wallpaper_uri")
        private val KEY_WALLPAPER_ALPHA = floatPreferencesKey("wallpaper_alpha")
        private val KEY_SHOW_GRID_LINES = booleanPreferencesKey("show_grid_lines")
        private val KEY_PRIVACY_FEATURE_ENABLED = booleanPreferencesKey("privacy_feature_enabled")
        private val KEY_DUAL_FINGERPRINT = booleanPreferencesKey("require_dual_fingerprint")
        private val KEY_PRIVACY_PIN_HASH = stringPreferencesKey("privacy_pin_hash")
        private val KEY_GOOGLE_EMAIL = stringPreferencesKey("google_account_email")
        private val KEY_GOOGLE_EMAILS = stringSetPreferencesKey("google_account_emails")
        private val KEY_HIDDEN_SYS_CALS = stringSetPreferencesKey("hidden_sys_cals")
        private val KEY_WIDGET_DARK = booleanPreferencesKey("widget_dark")
        private val KEY_WIDGET_OPACITY = floatPreferencesKey("widget_opacity")
        private val KEY_AUTO_CHECK_UPDATE = booleanPreferencesKey("auto_check_update")
        private val KEY_DYNAMIC_DAY_ICON = booleanPreferencesKey("dynamic_day_icon_enabled")
        private val KEY_LIVE_UPDATE_ISLAND = booleanPreferencesKey("live_update_island_enabled")

        fun sha256(input: String): String {
            val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }

    val settingsFlow: Flow<UserSettings> = appContext.dataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences()) else throw e
        }
        .map { prefs ->
            val brand = runCatching {
                BrandStyle.valueOf(prefs[KEY_BRAND_STYLE] ?: BrandStyle.GOLD_WHITE.name)
            }.getOrDefault(BrandStyle.GOLD_WHITE)

            val dark = runCatching {
                DarkThemeMode.valueOf(prefs[KEY_DARK_MODE] ?: DarkThemeMode.SYSTEM.name)
            }.getOrDefault(DarkThemeMode.SYSTEM)

            UserSettings(
                brandStyle = brand,
                darkThemeMode = dark,
                startWeekOnMonday = prefs[KEY_START_MONDAY] ?: false,
                showLunar = prefs[KEY_SHOW_LUNAR] ?: true,
                showWeekNumber = prefs[KEY_SHOW_WEEK_NUM] ?: false,
                showCompletedTasks = prefs[KEY_SHOW_COMPLETED_TASKS] ?: true,
                peekHiddenInMonth = prefs[KEY_PEEK_HIDDEN] ?: false,
                routineMedicationFeatureEnabled = prefs[KEY_ROUTINE_MED_FEATURE_ENABLED] ?: false,
                routineDefaultHideFromMonth = prefs[KEY_ROUTINE_DEFAULT_HIDE] ?: true,
                customWallpaperUri = prefs[KEY_WALLPAPER_URI] ?: "",
                wallpaperAlpha = prefs[KEY_WALLPAPER_ALPHA] ?: 0.28f,
                showMonthGridLines = prefs[KEY_SHOW_GRID_LINES] ?: true,
                privacyFeatureEnabled = prefs[KEY_PRIVACY_FEATURE_ENABLED] ?: false,
                requireDualFingerprint = prefs[KEY_DUAL_FINGERPRINT] ?: true,
                privacyPinHash = prefs[KEY_PRIVACY_PIN_HASH] ?: "",
                googleAccountEmail = prefs[KEY_GOOGLE_EMAIL] ?: "",
                googleAccountEmails = buildSet {
                    prefs[KEY_GOOGLE_EMAILS]?.filter { it.isNotBlank() }?.let { addAll(it) }
                    prefs[KEY_GOOGLE_EMAIL]?.takeIf { it.isNotBlank() }?.let { add(it) }
                },
                hiddenSystemCalIds = prefs[KEY_HIDDEN_SYS_CALS] ?: emptySet(),
                widgetDarkStyle = prefs[KEY_WIDGET_DARK] ?: false,
                widgetFollowSystemDark = prefs[KEY_WIDGET_DARK] == null,
                widgetOpacity = prefs[KEY_WIDGET_OPACITY] ?: 0.88f,
                autoCheckUpdate = prefs[KEY_AUTO_CHECK_UPDATE] ?: true,
                dynamicDayIconEnabled = prefs[KEY_DYNAMIC_DAY_ICON] ?: true,
                liveUpdateIslandEnabled = prefs[KEY_LIVE_UPDATE_ISLAND] ?: true
            )
        }

    suspend fun getSettingsOnce(): UserSettings = settingsFlow.first()

    suspend fun setBrandStyle(style: BrandStyle) {
        appContext.dataStore.edit { it[KEY_BRAND_STYLE] = style.name }
    }

    suspend fun setDarkThemeMode(mode: DarkThemeMode) {
        appContext.dataStore.edit { it[KEY_DARK_MODE] = mode.name }
    }

    suspend fun setStartWeekOnMonday(monday: Boolean) {
        appContext.dataStore.edit { it[KEY_START_MONDAY] = monday }
    }

    suspend fun setShowLunar(show: Boolean) {
        appContext.dataStore.edit { it[KEY_SHOW_LUNAR] = show }
    }

    suspend fun setShowWeekNumber(show: Boolean) {
        appContext.dataStore.edit { it[KEY_SHOW_WEEK_NUM] = show }
    }

    suspend fun setShowCompletedTasks(show: Boolean) {
        appContext.dataStore.edit { it[KEY_SHOW_COMPLETED_TASKS] = show }
    }

    suspend fun setPeekHiddenInMonth(peek: Boolean) {
        appContext.dataStore.edit { it[KEY_PEEK_HIDDEN] = peek }
    }

    suspend fun setRoutineMedicationFeatureEnabled(enabled: Boolean) {
        appContext.dataStore.edit { it[KEY_ROUTINE_MED_FEATURE_ENABLED] = enabled }
    }

    suspend fun setPrivacyFeatureEnabled(enabled: Boolean) {
        appContext.dataStore.edit { it[KEY_PRIVACY_FEATURE_ENABLED] = enabled }
    }

    suspend fun isLegacyMedicationDemoCleaned(): Boolean {
        return appContext.dataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .map { it[KEY_LEGACY_MED_DEMO_CLEANED] ?: false }
            .first()
    }

    suspend fun markLegacyMedicationDemoCleaned() {
        appContext.dataStore.edit { it[KEY_LEGACY_MED_DEMO_CLEANED] = true }
    }

    /**
     * 升級至新版「明亮=金白 / 暗色=金色灰黑底・預設跟隨手機主題移動」時執行一次性預設套用
     */
    suspend fun applyGoldWhiteDefaultMigrationOnce() {
        appContext.dataStore.edit { prefs ->
            if (prefs[KEY_GOLD_SYSTEM_AUTO_APPLIED_V2] != true) {
                if (prefs[KEY_BRAND_STYLE] == null || prefs[KEY_BRAND_STYLE] == BrandStyle.VIVO.name || prefs[KEY_BRAND_STYLE] == BrandStyle.GOLD_WHITE.name) {
                    prefs[KEY_BRAND_STYLE] = BrandStyle.GOLD_WHITE.name
                }
                prefs[KEY_DARK_MODE] = DarkThemeMode.SYSTEM.name
                prefs[KEY_GOLD_SYSTEM_AUTO_APPLIED_V2] = true
            }
        }
    }

    suspend fun setRoutineDefaultHideFromMonth(hide: Boolean) {
        appContext.dataStore.edit { it[KEY_ROUTINE_DEFAULT_HIDE] = hide }
    }

    suspend fun setWallpaper(uri: String, alpha: Float) {
        appContext.dataStore.edit {
            it[KEY_WALLPAPER_URI] = uri
            it[KEY_WALLPAPER_ALPHA] = alpha.coerceIn(0.05f, 0.75f)
        }
    }

    suspend fun setShowMonthGridLines(show: Boolean) {
        appContext.dataStore.edit { it[KEY_SHOW_GRID_LINES] = show }
    }

    suspend fun setRequireDualFingerprint(require: Boolean) {
        appContext.dataStore.edit { it[KEY_DUAL_FINGERPRINT] = require }
    }

    suspend fun setPrivacyPin(rawPin: String) {
        appContext.dataStore.edit {
            it[KEY_PRIVACY_PIN_HASH] = if (rawPin.isBlank()) "" else sha256(rawPin.trim())
        }
    }

    suspend fun setGoogleAccountEmail(email: String) {
        val clean = email.trim()
        appContext.dataStore.edit { prefs ->
            prefs[KEY_GOOGLE_EMAIL] = clean
            if (clean.isNotBlank()) {
                val cur = prefs[KEY_GOOGLE_EMAILS]?.toMutableSet() ?: mutableSetOf()
                cur.add(clean)
                prefs[KEY_GOOGLE_EMAILS] = cur
            }
        }
    }

    suspend fun removeGoogleAccountEmail(email: String) {
        val clean = email.trim()
        appContext.dataStore.edit { prefs ->
            val cur = prefs[KEY_GOOGLE_EMAILS]?.toMutableSet() ?: mutableSetOf()
            cur.remove(clean)
            prefs[KEY_GOOGLE_EMAILS] = cur
            if (prefs[KEY_GOOGLE_EMAIL] == clean) {
                prefs[KEY_GOOGLE_EMAIL] = cur.firstOrNull() ?: ""
            }
        }
    }

    suspend fun toggleSystemCalendarVisibility(calId: String, visible: Boolean) {
        appContext.dataStore.edit { prefs ->
            val current = prefs[KEY_HIDDEN_SYS_CALS]?.toMutableSet() ?: mutableSetOf()
            if (visible) current.remove(calId) else current.add(calId)
            prefs[KEY_HIDDEN_SYS_CALS] = current
        }
    }

    suspend fun setWidgetAppearance(dark: Boolean, opacity: Float) {
        appContext.dataStore.edit {
            it[KEY_WIDGET_DARK] = dark
            it[KEY_WIDGET_OPACITY] = opacity.coerceIn(0.0f, 1.0f)
        }
    }

    suspend fun setAutoCheckUpdate(enabled: Boolean) {
        appContext.dataStore.edit { it[KEY_AUTO_CHECK_UPDATE] = enabled }
    }

    suspend fun setDynamicDayIconEnabled(enabled: Boolean) {
        appContext.dataStore.edit { it[KEY_DYNAMIC_DAY_ICON] = enabled }
    }

    suspend fun setLiveUpdateIslandEnabled(enabled: Boolean) {
        appContext.dataStore.edit { it[KEY_LIVE_UPDATE_ISLAND] = enabled }
    }
}
