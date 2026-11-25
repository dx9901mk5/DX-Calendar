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
    val isWritable: Boolean
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
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL
        )

        runCatching {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                null,
                null,
                "${CalendarContract.Calendars.ACCOUNT_NAME} ASC, ${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} ASC"
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    val displayName = cursor.getString(1) ?: "未命名日曆"
                    val accountName = cursor.getString(2) ?: "本機帳號"
                    val accountType = cursor.getString(3) ?: ""
                    val rawColor = cursor.getInt(4)
                    val color = if (rawColor == 0) 0xFF4285F4.toInt() else (rawColor or 0xFF000000.toInt())
                    val access = cursor.getInt(5)
                    val writable = access >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR
                    result.add(
                        SystemCalendarAccountInfo(
                            id = id,
                            displayName = displayName,
                            accountName = accountName,
                            accountType = accountType,
                            colorArgb = color,
                            isWritable = writable
                        )
                    )
                }
            }
        }
        return result
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
            CalendarContract.Instances.RRULE
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
                    val calName = cursor.getString(2) ?: "系統日曆"
                    val accName = cursor.getString(3) ?: ""
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
        reminderMinutes: List<Int>
    ): Long? {
        if (!hasWritePermission()) return null
        return runCatching {
            val values = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, systemCalendarId)
                put(CalendarContract.Events.TITLE, title)
                put(CalendarContract.Events.DESCRIPTION, description)
                put(CalendarContract.Events.EVENT_LOCATION, location)
                put(CalendarContract.Events.DTSTART, startMillis)
                put(CalendarContract.Events.DTEND, endMillis.coerceAtLeast(startMillis + 60_000L))
                put(CalendarContract.Events.ALL_DAY, if (isAllDay) 1 else 0)
                put(
                    CalendarContract.Events.EVENT_TIMEZONE,
                    if (isAllDay) "UTC" else TimeZone.getDefault().id
                )
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
