package com.dx.calendar.data.sync

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.util.TimeZone

data class SystemCalendarAccountInfo(
    val id: Long,
    val displayName: String,
    val accountName: String,
    val accountType: String,
    val colorArgb: Int,
    val isWritable: Boolean,
    val isPrimary: Boolean = false,
    val isGoogleAccount: Boolean = false
)

data class SystemEventInstance(
    val eventId: Long,
    val calendarId: Long,
    val calendarDisplayName: String,
    val accountName: String,
    val title: String,
    val description: String,
    val location: String,
    val beginMillis: Long,
    val endMillis: Long,
    val isAllDay: Boolean,
    val colorArgb: Int,
    val rrule: String?
)

class SystemCalendarBridge(private val context: Context) {

    companion object {
        private val PAREN_EMAIL_REGEX = Regex("""\s*\([^()]*@[^()]*\)\s*$""")

        /**
         * 過濾移除手機內建的 vivo 系統日曆（如「vivo日曆」、「vivo 日曆」、com.bbk.calendar / com.vivo），
         * 避免與 DX 本機行事曆及 Google 行事曆重複混雜。
         */
        fun isExcludedVendorCalendar(
            displayName: String?,
            accountName: String?,
            accountType: String? = null
        ): Boolean {
            val normDisplay = displayName.orEmpty().replace(" ", "").lowercase()
            val normAcc = accountName.orEmpty().replace(" ", "").lowercase()
            val normType = accountType.orEmpty().replace(" ", "").lowercase()

            if (normDisplay.contains("vivo") || normDisplay.contains("bbk")) return true
            if (normAcc.contains("vivo") || normAcc.contains("bbk")) return true
            if (normType.contains("vivo") || normType.contains("bbk")) return true
            return false
        }

        /**
         * 將行事曆名稱精簡化，隱藏所有帳號／Email 資訊：
         * - 移除尾端括號內的 Email（如 `家庭 (xxx@gmail.com)` -> `家庭`）
         * - 若 Google 主行事曆名稱本身即為 Email（如 `xxx@gmail.com`），自動轉為簡潔的「主要行事曆」
         */
        fun formatConciseCalendarName(rawDisplayName: String?, accountName: String? = null): String {
            var cleaned = rawDisplayName.orEmpty().trim()
                .replace(PAREN_EMAIL_REGEX, "")
                .trim()

            if (!accountName.isNullOrBlank() && cleaned.endsWith("($accountName)", ignoreCase = true)) {
                cleaned = cleaned.removeSuffix("($accountName)").trim()
            }

            if (cleaned.isEmpty()) return "主要行事曆"

            if (cleaned.contains("@")) {
                val lower = cleaned.lowercase()
                return when {
                    lower.contains("#holiday@") -> "節慶假日"
                    lower.contains("#contacts@") || lower.contains("addressbook#") -> "聯絡人生日"
                    else -> "主要行事曆"
                }
            }
            return cleaned
        }

        /**
         * 依據裝置已登記的 Gmail／Google 帳號，自動挑選預設新增活動之 Google 行事曆：
         * 1. 優先匹配已綁定帳號 (`preferredEmail`) 的主要 Google 行事曆
         * 2. 次選裝置上任一 `@gmail.com` 的主要 Google 行事曆
         * 3. 再選任一可寫入的 Google 行事曆，最後退回第一個可寫入同步行事曆
         */
        fun pickDefaultGoogleCalendar(
            writableCalendars: List<SystemCalendarAccountInfo>,
            preferredEmail: String = ""
        ): SystemCalendarAccountInfo? {
            if (writableCalendars.isEmpty()) return null
            val pref = preferredEmail.trim()
            if (pref.isNotBlank()) {
                writableCalendars.firstOrNull { it.accountName.equals(pref, ignoreCase = true) && it.isPrimary }
                    ?.let { return it }
                writableCalendars.firstOrNull { it.accountName.equals(pref, ignoreCase = true) }
                    ?.let { return it }
            }
            writableCalendars.firstOrNull {
                it.isGoogleAccount && it.accountName.endsWith("@gmail.com", ignoreCase = true) && it.isPrimary
            }?.let { return it }
            writableCalendars.firstOrNull {
                it.isGoogleAccount && it.accountName.endsWith("@gmail.com", ignoreCase = true)
            }?.let { return it }
            writableCalendars.firstOrNull { it.isGoogleAccount && it.isPrimary }?.let { return it }
            writableCalendars.firstOrNull { it.isGoogleAccount }?.let { return it }
            return writableCalendars.firstOrNull()
        }
    }

    fun hasReadPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasWritePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun querySystemCalendars(): List<SystemCalendarAccountInfo> {
        if (!hasReadPermission()) return emptyList()
        val result = mutableListOf<SystemCalendarAccountInfo>()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.CALENDAR_COLOR,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
            CalendarContract.Calendars.IS_PRIMARY
        )

        runCatching {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                null,
                null,
                "${CalendarContract.Calendars.IS_PRIMARY} DESC, ${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} ASC"
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    val rawDisplayName = cursor.getString(1)
                    val accountName = cursor.getString(2) ?: ""
                    val accountType = cursor.getString(3) ?: ""
                    if (isExcludedVendorCalendar(rawDisplayName, accountName, accountType)) {
                        continue
                    }
                    val displayName = formatConciseCalendarName(rawDisplayName, accountName)
                    val rawColor = cursor.getInt(4)
                    val color = if (rawColor == 0) 0xFF4285F4.toInt() else (rawColor or 0xFF000000.toInt())
                    val access = cursor.getInt(5)
                    val writable = access >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR
                    val isGoogle = accountType.equals("com.google", ignoreCase = true) ||
                        accountName.endsWith("@gmail.com", ignoreCase = true)
                    val isPrimaryCol = runCatching { cursor.getInt(6) == 1 }.getOrDefault(false)
                    val isPrimary = isPrimaryCol ||
                        (isGoogle && rawDisplayName.orEmpty().trim().equals(accountName.trim(), ignoreCase = true))

                    result.add(
                        SystemCalendarAccountInfo(
                            id = id,
                            displayName = displayName,
                            accountName = accountName,
                            accountType = accountType,
                            colorArgb = color,
                            isWritable = writable,
                            isPrimary = isPrimary,
                            isGoogleAccount = isGoogle
                        )
                    )
                }
            }
        }
        // Google 主行事曆優先排在最前面，方便新增活動時一眼挑選！
        return result.sortedWith(
            compareByDescending<SystemCalendarAccountInfo> { it.isGoogleAccount && it.isPrimary }
                .thenByDescending { it.isGoogleAccount }
                .thenByDescending { it.isPrimary }
                .thenBy { it.displayName }
        )
    }

    fun querySystemInstances(startMillis: Long, endMillis: Long): List<SystemEventInstance> {
        if (!hasReadPermission()) return emptyList()
        val list = mutableListOf<SystemEventInstance>()
        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, startMillis)
        ContentUris.appendId(builder, endMillis)

        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.DESCRIPTION,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.DISPLAY_COLOR,
            CalendarContract.Instances.RRULE,
            CalendarContract.Calendars.ACCOUNT_TYPE
        )

        runCatching {
            context.contentResolver.query(
                builder.build(),
                projection,
                "${CalendarContract.Instances.STATUS} != ${CalendarContract.Instances.STATUS_CANCELED}",
                null,
                "${CalendarContract.Instances.BEGIN} ASC"
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val eventId = cursor.getLong(0)
                    val calId = cursor.getLong(1)
                    val rawCalName = cursor.getString(2)
                    val accName = cursor.getString(3) ?: ""
                    val accType = cursor.getString(12) ?: ""
                    if (isExcludedVendorCalendar(rawCalName, accName, accType)) {
                        continue
                    }
                    val calName = formatConciseCalendarName(rawCalName, accName)
                    val title = cursor.getString(4)?.takeIf { it.isNotBlank() } ?: "(無標題)"
                    val desc = cursor.getString(5) ?: ""
                    val loc = cursor.getString(6) ?: ""
                    val begin = cursor.getLong(7)
                    val end = cursor.getLong(8).coerceAtLeast(begin)
                    val allDay = cursor.getInt(9) == 1
                    val rawColor = cursor.getInt(10)
                    val color = if (rawColor == 0) 0xFF4285F4.toInt() else (rawColor or 0xFF000000.toInt())
                    val rrule = cursor.getString(11)

                    list.add(
                        SystemEventInstance(
                            eventId = eventId,
                            calendarId = calId,
                            calendarDisplayName = calName,
                            accountName = accName,
                            title = title,
                            description = desc,
                            location = loc,
                            beginMillis = begin,
                            endMillis = end,
                            isAllDay = allDay,
                            colorArgb = color,
                            rrule = rrule
                        )
                    )
                }
            }
        }
        return list
    }

    fun insertEventToSystemCalendar(
        systemCalendarId: Long,
        title: String,
        description: String,
        location: String,
        startMillis: Long,
        endMillis: Long,
        isAllDay: Boolean,
        reminderMinutes: List<Int>,
        rrule: String? = null
    ): Long? {
        if (!hasWritePermission()) return null
        return runCatching {
            val safeEnd = endMillis.coerceAtLeast(startMillis + 60_000L)
            val values = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, systemCalendarId)
                put(CalendarContract.Events.TITLE, title)
                put(CalendarContract.Events.DESCRIPTION, description)
                put(CalendarContract.Events.EVENT_LOCATION, location)
                put(CalendarContract.Events.DTSTART, startMillis)
                put(CalendarContract.Events.ALL_DAY, if (isAllDay) 1 else 0)
                put(
                    CalendarContract.Events.EVENT_TIMEZONE,
                    if (isAllDay) "UTC" else TimeZone.getDefault().id
                )
                if (!rrule.isNullOrBlank()) {
                    val durMinutes = ((safeEnd - startMillis) / 60_000L).coerceAtLeast(30L)
                    put(CalendarContract.Events.RRULE, rrule)
                    put(CalendarContract.Events.DURATION, if (isAllDay) "P1D" else "PT${durMinutes}M")
                } else {
                    put(CalendarContract.Events.DTEND, safeEnd)
                }
            }
            val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            val newEventId = uri?.lastPathSegment?.toLongOrNull()
            if (newEventId != null) {
                reminderMinutes.forEach { min ->
                    val remValues = ContentValues().apply {
                        put(CalendarContract.Reminders.EVENT_ID, newEventId)
                        put(CalendarContract.Reminders.MINUTES, min)
                        put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                    }
                    context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, remValues)
                }
            }
            newEventId
        }.getOrNull()
    }
}
