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
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.temporal.WeekFields
import java.util.Locale

private val Context.dataStore by preferencesDataStore(name = "dx_calendar_prefs")

enum class AlternateCalendarMode(val label: String) {
    AUTO_BY_REGION("依所在地自動選擇"),
    TAIWAN_LUNAR("台灣農曆"),
    CHINA_LUNAR("中國農曆"),
    HONG_KONG_LUNAR("香港農曆"),
    NONE("關閉");

    companion object {
        fun resolveRegionDefault(
            locale: Locale = Locale.getDefault(),
            zoneId: String = ZoneId.systemDefault().id
        ): AlternateCalendarMode {
            val country = locale.country.uppercase(Locale.ROOT)
            return when {
                country == "TW" || zoneId.equals("Asia/Taipei", ignoreCase = true) -> TAIWAN_LUNAR
                country == "HK" || country == "MO" ||
                    zoneId.equals("Asia/Hong_Kong", ignoreCase = true) ||
                    zoneId.equals("Asia/Macau", ignoreCase = true) -> HONG_KONG_LUNAR
                country == "CN" || country == "SG" ||
                    zoneId.equals("Asia/Shanghai", ignoreCase = true) ||
                    zoneId.equals("Asia/Chongqing", ignoreCase = true) ||
                    zoneId.equals("Asia/Singapore", ignoreCase = true) -> CHINA_LUNAR
                locale.language.equals("zh", ignoreCase = true) -> TAIWAN_LUNAR
                else -> NONE
            }
        }
    }
}

enum class WeekStartMode(val label: String) {
    REGION_DEFAULT("區域預設"),
    SUNDAY("週日"),
    MONDAY("週一")
}

enum class DateInfoDisplayMode(val label: String) {
    LUNAR_AND_HOLIDAY("農曆與節慶"),
    LUNAR_ONLY("僅顯示農曆"),
    HOLIDAY_ONLY("僅顯示節慶與節氣"),
    NONE("不顯示")
}

enum class MonthViewLayoutMode(val label: String) {
    FULL_MONTH("滿版月曆檢視"),
    SCHEDULE_SPLIT("時間表檢視")
}

data class UserSettings(
    val brandStyle: BrandStyle = BrandStyle.GOLD_WHITE,
    val darkThemeMode: DarkThemeMode = DarkThemeMode.SYSTEM,
    val startWeekOnMonday: Boolean = false,
    val weekStartMode: WeekStartMode = WeekStartMode.REGION_DEFAULT,
    val showLunar: Boolean = true,
    val alternateCalendarMode: AlternateCalendarMode = AlternateCalendarMode.AUTO_BY_REGION,
    val dateInfoDisplayMode: DateInfoDisplayMode = DateInfoDisplayMode.LUNAR_AND_HOLIDAY,
    val monthViewLayoutMode: MonthViewLayoutMode = MonthViewLayoutMode.FULL_MONTH,
    val timeZoneOverrideId: String = "",
    val showPublicHolidays: Boolean = true,
    val showTraditionalFestivals: Boolean = true,
    val showSolarTerms: Boolean = true,
    val defaultEventReminders: List<Int> = listOf(0, 5),
    val defaultTaskReminders: List<Int> = listOf(0),
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
    val dynamicDayIconEnabled: Boolean = false,
    val liveUpdateIslandEnabled: Boolean = true,
    val secondaryTimeZoneId: String = "",
    val backupDeviceName: String = android.os.Build.MODEL ?: "DX_Device"
) {
    val effectiveAlternateCalendar: AlternateCalendarMode
        get() = if (alternateCalendarMode == AlternateCalendarMode.AUTO_BY_REGION) {
            AlternateCalendarMode.resolveRegionDefault()
        } else {
            alternateCalendarMode
        }

    val effectiveZoneId: ZoneId
        get() = if (timeZoneOverrideId.isNotBlank()) {
            runCatching { ZoneId.of(timeZoneOverrideId) }.getOrDefault(ZoneId.systemDefault())
        } else {
            ZoneId.systemDefault()
        }
}

class AppPreferences(context: Context) {

    private val appContext: Context = context.applicationContext ?: context

    companion object {
        private val KEY_BRAND_STYLE = stringPreferencesKey("brand_style")
        private val KEY_DARK_MODE = stringPreferencesKey("dark_mode")
        private val KEY_START_MONDAY = booleanPreferencesKey("start_monday")
        private val KEY_WEEK_START_MODE = stringPreferencesKey("week_start_mode")
        private val KEY_SHOW_LUNAR = booleanPreferencesKey("show_lunar")
        private val KEY_ALT_CALENDAR_MODE = stringPreferencesKey("alt_calendar_mode")
        private val KEY_DATE_INFO_DISPLAY_MODE = stringPreferencesKey("date_info_display_mode")
        private val KEY_MONTH_VIEW_LAYOUT_MODE = stringPreferencesKey("month_view_layout_mode")
        private val KEY_TIMEZONE_OVERRIDE = stringPreferencesKey("timezone_override_id")
        private val KEY_SHOW_PUBLIC_HOLIDAYS = booleanPreferencesKey("show_public_holidays")
        private val KEY_SHOW_TRADITIONAL_FESTIVALS = booleanPreferencesKey("show_traditional_festivals")
        private val KEY_SHOW_SOLAR_TERMS = booleanPreferencesKey("show_solar_terms")
        private val KEY_DEFAULT_EVENT_REMINDERS = stringPreferencesKey("default_event_reminders")
        private val KEY_DEFAULT_TASK_REMINDERS = stringPreferencesKey("default_task_reminders")
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
        private val KEY_SECONDARY_TIMEZONE = stringPreferencesKey("secondary_timezone_id")
        private val KEY_BACKUP_DEVICE_NAME = stringPreferencesKey("backup_device_name")

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

            val weekStartMode = runCatching {
                prefs[KEY_WEEK_START_MODE]?.let { WeekStartMode.valueOf(it) }
            }.getOrNull() ?: when (prefs[KEY_START_MONDAY]) {
                true -> WeekStartMode.MONDAY
                false -> WeekStartMode.SUNDAY
                null -> WeekStartMode.REGION_DEFAULT
            }
            val effectiveStartMonday = when (weekStartMode) {
                WeekStartMode.MONDAY -> true
                WeekStartMode.SUNDAY -> false
                WeekStartMode.REGION_DEFAULT -> WeekFields.of(Locale.getDefault()).firstDayOfWeek == DayOfWeek.MONDAY
            }
            val altCalMode = runCatching {
                prefs[KEY_ALT_CALENDAR_MODE]?.let { AlternateCalendarMode.valueOf(it) }
            }.getOrNull() ?: if (prefs[KEY_SHOW_LUNAR] == false) AlternateCalendarMode.NONE else AlternateCalendarMode.AUTO_BY_REGION

            val dateInfoMode = runCatching {
                prefs[KEY_DATE_INFO_DISPLAY_MODE]?.let { DateInfoDisplayMode.valueOf(it) }
            }.getOrNull() ?: if (prefs[KEY_SHOW_LUNAR] == false) DateInfoDisplayMode.NONE else DateInfoDisplayMode.LUNAR_AND_HOLIDAY

            val monthLayoutMode = runCatching {
                prefs[KEY_MONTH_VIEW_LAYOUT_MODE]?.let { MonthViewLayoutMode.valueOf(it) }
            }.getOrNull() ?: MonthViewLayoutMode.FULL_MONTH

            val effectiveShowLunar = altCalMode != AlternateCalendarMode.NONE &&
                dateInfoMode != DateInfoDisplayMode.NONE

            val defEventRems = prefs[KEY_DEFAULT_EVENT_REMINDERS]?.let { raw ->
                if (raw.isBlank()) emptyList()
                else raw.split(",").mapNotNull { it.trim().toIntOrNull() }.distinct().sorted()
            } ?: listOf(0, 5)

            val defTaskRems = prefs[KEY_DEFAULT_TASK_REMINDERS]?.let { raw ->
                if (raw.isBlank()) emptyList()
                else raw.split(",").mapNotNull { it.trim().toIntOrNull() }.distinct().sorted()
            } ?: listOf(0)

            UserSettings(
                brandStyle = brand,
                darkThemeMode = dark,
                startWeekOnMonday = effectiveStartMonday,
                weekStartMode = weekStartMode,
                showLunar = effectiveShowLunar,
                alternateCalendarMode = altCalMode,
                dateInfoDisplayMode = dateInfoMode,
                monthViewLayoutMode = monthLayoutMode,
                timeZoneOverrideId = prefs[KEY_TIMEZONE_OVERRIDE] ?: "",
                showPublicHolidays = prefs[KEY_SHOW_PUBLIC_HOLIDAYS] ?: true,
                showTraditionalFestivals = prefs[KEY_SHOW_TRADITIONAL_FESTIVALS] ?: true,
                showSolarTerms = prefs[KEY_SHOW_SOLAR_TERMS] ?: true,
                defaultEventReminders = defEventRems,
                defaultTaskReminders = defTaskRems,
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
                dynamicDayIconEnabled = prefs[KEY_DYNAMIC_DAY_ICON] ?: false,
                liveUpdateIslandEnabled = prefs[KEY_LIVE_UPDATE_ISLAND] ?: true,
                secondaryTimeZoneId = prefs[KEY_SECONDARY_TIMEZONE] ?: "",
                backupDeviceName = prefs[KEY_BACKUP_DEVICE_NAME]?.takeIf { it.isNotBlank() }
                    ?: (android.os.Build.MODEL ?: "DX_Device")
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
        appContext.dataStore.edit {
            it[KEY_START_MONDAY] = monday
            it[KEY_WEEK_START_MODE] = if (monday) WeekStartMode.MONDAY.name else WeekStartMode.SUNDAY.name
        }
    }

    suspend fun setWeekStartMode(mode: WeekStartMode) {
        appContext.dataStore.edit {
            it[KEY_WEEK_START_MODE] = mode.name
            if (mode == WeekStartMode.MONDAY) it[KEY_START_MONDAY] = true
            else if (mode == WeekStartMode.SUNDAY) it[KEY_START_MONDAY] = false
        }
    }

    suspend fun setShowLunar(show: Boolean) {
        appContext.dataStore.edit {
            it[KEY_SHOW_LUNAR] = show
            if (!show) {
                it[KEY_DATE_INFO_DISPLAY_MODE] = DateInfoDisplayMode.NONE.name
            } else if (it[KEY_DATE_INFO_DISPLAY_MODE] == DateInfoDisplayMode.NONE.name) {
                it[KEY_DATE_INFO_DISPLAY_MODE] = DateInfoDisplayMode.LUNAR_AND_HOLIDAY.name
            }
        }
    }

    suspend fun setAlternateCalendarMode(mode: AlternateCalendarMode) {
        appContext.dataStore.edit {
            it[KEY_ALT_CALENDAR_MODE] = mode.name
            it[KEY_SHOW_LUNAR] = mode != AlternateCalendarMode.NONE
        }
    }

    suspend fun setDateInfoDisplayMode(mode: DateInfoDisplayMode) {
        appContext.dataStore.edit {
            it[KEY_DATE_INFO_DISPLAY_MODE] = mode.name
            it[KEY_SHOW_LUNAR] = mode != DateInfoDisplayMode.NONE
        }
    }

    suspend fun setMonthViewLayoutMode(mode: MonthViewLayoutMode) {
        appContext.dataStore.edit { it[KEY_MONTH_VIEW_LAYOUT_MODE] = mode.name }
    }

    suspend fun setTimeZoneOverrideId(zoneId: String) {
        appContext.dataStore.edit { it[KEY_TIMEZONE_OVERRIDE] = zoneId.trim() }
    }

    suspend fun setHolidayPreferences(publicHolidays: Boolean, traditional: Boolean, solarTerms: Boolean) {
        appContext.dataStore.edit {
            it[KEY_SHOW_PUBLIC_HOLIDAYS] = publicHolidays
            it[KEY_SHOW_TRADITIONAL_FESTIVALS] = traditional
            it[KEY_SHOW_SOLAR_TERMS] = solarTerms
        }
    }

    suspend fun setDefaultEventReminders(minutes: List<Int>) {
        appContext.dataStore.edit {
            it[KEY_DEFAULT_EVENT_REMINDERS] = minutes.distinct().sorted().joinToString(",")
        }
    }

    suspend fun setDefaultTaskReminders(minutes: List<Int>) {
        appContext.dataStore.edit {
            it[KEY_DEFAULT_TASK_REMINDERS] = minutes.distinct().sorted().joinToString(",")
        }
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

    suspend fun setSecondaryTimeZoneId(zoneId: String) {
        appContext.dataStore.edit { it[KEY_SECONDARY_TIMEZONE] = zoneId.trim() }
    }

    suspend fun setBackupDeviceName(name: String) {
        appContext.dataStore.edit { it[KEY_BACKUP_DEVICE_NAME] = name.trim() }
    }
}
