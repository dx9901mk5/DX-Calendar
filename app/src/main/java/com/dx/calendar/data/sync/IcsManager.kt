package com.dx.calendar.data.sync

import com.dx.calendar.data.model.CalendarEventEntity
import com.dx.calendar.data.model.RecurrenceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * 支援 vivo 日曆「匯入日程（本機 ICS 檔案 / URL 訂閱連結）」與「分享匯出 ICS」
 */
object IcsManager {

    private val utcFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
    private val localFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val dateOnlyFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

    suspend fun fetchIcsFromUrl(url: String): String = withContext(Dispatchers.IO) {
        val normalizedUrl = if (url.startsWith("webcal://", ignoreCase = true)) {
            "https://" + url.substring("webcal://".length)
        } else url
        val client = OkHttpClient()
        val req = Request.Builder().url(normalizedUrl).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("無法下載 ICS 連結 (${resp.code})")
            resp.body?.string().orEmpty()
        }
    }

    fun parseIcsContent(icsText: String, targetCategoryId: Long = 1L): List<CalendarEventEntity> {
        val events = mutableListOf<CalendarEventEntity>()
        // 處理 RFC 5545 折行 (folded lines)
        val unfolded = icsText.replace("\r\n ", "").replace("\r\n\t", "").replace("\n ", "").replace("\n\t", "")
        val lines = unfolded.lines()

        var inEvent = false
        var summary = ""
        var description = ""
        var location = ""
        var url = ""
        var dtStart: Long? = null
        var dtEnd: Long? = null
        var isAllDay = false
        var recurrence = RecurrenceType.NONE

        for (rawLine in lines) {
            val line = rawLine.trim()
            when {
                line.equals("BEGIN:VEVENT", ignoreCase = true) -> {
                    inEvent = true
                    summary = ""
                    description = ""
                    location = ""
                    url = ""
                    dtStart = null
                    dtEnd = null
                    isAllDay = false
                    recurrence = RecurrenceType.NONE
                }
                line.equals("END:VEVENT", ignoreCase = true) -> {
                    if (inEvent && summary.isNotBlank() && dtStart != null) {
                        val start = dtStart
                        val end = dtEnd ?: (start + if (isAllDay) 86_400_000L else 3_600_000L)
                        events.add(
                            CalendarEventEntity(
                                categoryId = targetCategoryId,
                                title = unescapeIcs(summary),
                                notes = unescapeIcs(description),
                                location = unescapeIcs(location),
                                url = unescapeIcs(url),
                                startEpochMillis = start,
                                endEpochMillis = end.coerceAtLeast(start),
                                isAllDay = isAllDay,
                                recurrenceType = recurrence.name
                            )
                        )
                    }
                    inEvent = false
                }
                inEvent -> {
                    val colonIdx = line.indexOf(':')
                    if (colonIdx > 0) {
                        val keyPart = line.substring(0, colonIdx).uppercase()
                        val valuePart = line.substring(colonIdx + 1)
                        when {
                            keyPart.startsWith("SUMMARY") -> summary = valuePart
                            keyPart.startsWith("DESCRIPTION") -> description = valuePart
                            keyPart.startsWith("LOCATION") -> location = valuePart
                            keyPart.startsWith("URL") -> url = valuePart
                            keyPart.startsWith("DTSTART") -> {
                                val parsed = parseIcsDateTime(keyPart, valuePart)
                                dtStart = parsed?.first
                                if (parsed?.second == true) isAllDay = true
                            }
                            keyPart.startsWith("DTEND") -> {
                                dtEnd = parseIcsDateTime(keyPart, valuePart)?.first
                            }
                            keyPart.startsWith("RRULE") -> {
                                recurrence = when {
                                    valuePart.contains("FREQ=DAILY", true) -> RecurrenceType.DAILY
                                    valuePart.contains("FREQ=WEEKLY", true) -> RecurrenceType.WEEKLY
                                    valuePart.contains("FREQ=MONTHLY", true) -> RecurrenceType.MONTHLY
                                    valuePart.contains("FREQ=YEARLY", true) -> RecurrenceType.YEARLY
                                    else -> RecurrenceType.NONE
                                }
                            }
                        }
                    }
                }
            }
        }
        return events
    }

    private fun parseIcsDateTime(keyPart: String, value: String): Pair<Long, Boolean>? {
        val clean = value.trim()
        val zone = ZoneId.systemDefault()
        return runCatching {
            if (keyPart.contains("VALUE=DATE") || clean.length == 8) {
                val d = LocalDate.parse(clean.take(8), dateOnlyFormatter)
                d.atStartOfDay(zone).toInstant().toEpochMilli() to true
            } else if (clean.endsWith("Z")) {
                val ldt = LocalDateTime.parse(clean, utcFormatter)
                ldt.toInstant(ZoneOffset.UTC).toEpochMilli() to false
            } else {
                val ldt = LocalDateTime.parse(clean.take(15), localFormatter)
                ldt.atZone(zone).toInstant().toEpochMilli() to false
            }
        }.getOrNull()
    }

    fun exportEventsToIcs(events: List<CalendarEventEntity>): String {
        val sb = StringBuilder()
        sb.appendLine("BEGIN:VCALENDAR")
        sb.appendLine("VERSION:2.0")
        sb.appendLine("PRODID:-//DX Calendar//TW")
        sb.appendLine("CALSCALE:GREGORIAN")
        val zone = ZoneId.systemDefault()

        for (ev in events) {
            if (ev.isPrivacy) continue // 隱私日曆絕不外洩於一般匯出
            sb.appendLine("BEGIN:VEVENT")
            sb.appendLine("UID:${ev.id}-${UUID.randomUUID()}@dx.calendar")
            if (ev.isAllDay) {
                val startD = Instant.ofEpochMilli(ev.startEpochMillis).atZone(zone).toLocalDate()
                val endD = Instant.ofEpochMilli(ev.endEpochMillis).atZone(zone).toLocalDate().plusDays(1)
                sb.appendLine("DTSTART;VALUE=DATE:${startD.format(dateOnlyFormatter)}")
                sb.appendLine("DTEND;VALUE=DATE:${endD.format(dateOnlyFormatter)}")
            } else {
                val startUtc = Instant.ofEpochMilli(ev.startEpochMillis).atZone(ZoneOffset.UTC)
                val endUtc = Instant.ofEpochMilli(ev.endEpochMillis).atZone(ZoneOffset.UTC)
                sb.appendLine("DTSTART:${startUtc.format(utcFormatter)}")
                sb.appendLine("DTEND:${endUtc.format(utcFormatter)}")
            }
            sb.appendLine("SUMMARY:${escapeIcs(ev.title)}")
            if (ev.location.isNotBlank()) sb.appendLine("LOCATION:${escapeIcs(ev.location)}")
            if (ev.notes.isNotBlank()) sb.appendLine("DESCRIPTION:${escapeIcs(ev.notes)}")
            if (ev.url.isNotBlank()) sb.appendLine("URL:${escapeIcs(ev.url)}")
            sb.appendLine("END:VEVENT")
        }
        sb.appendLine("END:VCALENDAR")
        return sb.toString()
    }

    private fun unescapeIcs(text: String): String =
        text.replace("\\n", "\n").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\")

    private fun escapeIcs(text: String): String =
        text.replace("\\", "\\\\").replace("\n", "\\n").replace(",", "\\,").replace(";", "\\;")
}
