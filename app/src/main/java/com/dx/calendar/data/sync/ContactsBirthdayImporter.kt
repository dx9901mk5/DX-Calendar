package com.dx.calendar.data.sync

import android.content.Context
import android.provider.ContactsContract
import com.dx.calendar.data.local.CalendarDao
import com.dx.calendar.data.local.EventRichMeta
import com.dx.calendar.data.local.EventRichMetaStore
import com.dx.calendar.data.model.CalendarEventEntity
import com.dx.calendar.data.model.RecurrenceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

data class ContactBirthdayEntry(
    val displayName: String,
    val month: Int,
    val day: Int,
    val birthYear: Int?
)

/**
 * #3 聯絡人生日自動匯入與歲數計算引擎：
 * 讀取手機聯絡人中的生日欄位（支援 YYYY-MM-DD 與 --MM-DD），
 * 自動建立每年重複的「生日」行程並寫入 EventRichMeta（自動計算倒數天數與今年幾歲）。
 */
object ContactsBirthdayImporter {

    private val FULL_DATE_REGEX = Regex("^(\\d{4})[-/.](\\d{1,2})[-/.](\\d{1,2})$")
    private val MONTH_DAY_REGEX = Regex("^(?:--)?(\\d{1,2})[-/.](\\d{1,2})$")

    fun parseBirthdayString(raw: String): Triple<Int?, Int, Int>? {
        val clean = raw.trim().substringBefore("T").substringBefore(" ")
        FULL_DATE_REGEX.matchEntire(clean)?.let { m ->
            val y = m.groupValues[1].toIntOrNull()
            val mo = m.groupValues[2].toIntOrNull() ?: return null
            val d = m.groupValues[3].toIntOrNull() ?: return null
            if (mo in 1..12 && d in 1..31) return Triple(y?.takeIf { it in 1900..2100 }, mo, d)
        }
        MONTH_DAY_REGEX.matchEntire(clean)?.let { m ->
            val mo = m.groupValues[1].toIntOrNull() ?: return null
            val d = m.groupValues[2].toIntOrNull() ?: return null
            if (mo in 1..12 && d in 1..31) return Triple(null, mo, d)
        }
        return null
    }

    suspend fun importFromContacts(context: Context, dao: CalendarDao, zone: ZoneId): Result<Int> =
        withContext(Dispatchers.IO) {
            runCatching {
                val found = mutableListOf<ContactBirthdayEntry>()
                val projection = arrayOf(
                    ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                    ContactsContract.CommonDataKinds.Event.START_DATE
                )
                val selection = "${ContactsContract.Data.MIMETYPE} = ? AND ${ContactsContract.CommonDataKinds.Event.TYPE} = ?"
                val selectionArgs = arrayOf(
                    ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE,
                    ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY.toString()
                )
                context.contentResolver.query(
                    ContactsContract.Data.CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    null
                )?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
                    val dateIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Event.START_DATE)
                    while (cursor.moveToNext()) {
                        val name = if (nameIdx >= 0) cursor.getString(nameIdx)?.trim().orEmpty() else ""
                        val dateStr = if (dateIdx >= 0) cursor.getString(dateIdx)?.trim().orEmpty() else ""
                        if (name.isBlank() || dateStr.isBlank()) continue
                        val parsed = parseBirthdayString(dateStr) ?: continue
                        found += ContactBirthdayEntry(
                            displayName = name,
                            month = parsed.second,
                            day = parsed.third,
                            birthYear = parsed.first
                        )
                    }
                }

                if (found.isEmpty()) return@runCatching 0

                val existingEvents = dao.getAllEventsOnce()
                val existingTitles = existingEvents.associateBy { it.title }
                val birthdayCategoryId = dao.getCategoriesOnce()
                    .firstOrNull { it.name.contains("生日") }?.id ?: 2L
                val todayYear = LocalDate.now(zone).year
                var importedCount = 0

                found.distinctBy { "${it.displayName}_${it.month}_${it.day}" }.forEach { entry ->
                    val title = "${entry.displayName} 生日"
                    val safeDay = entry.day.coerceAtMost(LocalDate.of(todayYear, entry.month, 1).lengthOfMonth())
                    val baseDate = LocalDate.of(entry.birthYear ?: todayYear, entry.month, safeDay)
                    val startMs = baseDate.atStartOfDay(zone).toInstant().toEpochMilli()
                    val endMs = baseDate.atTime(23, 59).atZone(zone).toInstant().toEpochMilli()

                    val existing = existingTitles[title]
                    val savedId = if (existing != null) {
                        existing.id
                    } else {
                        dao.upsertEvent(
                            CalendarEventEntity(
                                categoryId = birthdayCategoryId,
                                title = title,
                                notes = entry.birthYear?.let { "聯絡人生日（西元 $it 年生）" } ?: "聯絡人生日",
                                startEpochMillis = startMs,
                                endEpochMillis = endMs,
                                isAllDay = true,
                                recurrenceType = RecurrenceType.YEARLY.name,
                                showInMonthGrid = true,
                                showInAgendaAndWidget = true,
                                reminderMinutesJson = "[0, 1440]"
                            )
                        )
                    }

                    val metaKey = EventRichMetaStore.makeKey("LOCAL_EVENT", savedId)
                    val oldMeta = EventRichMetaStore.get(context, metaKey)
                    EventRichMetaStore.upsert(
                        context,
                        (oldMeta ?: EventRichMeta(key = metaKey)).copy(
                            isCountdown = true,
                            isBirthday = true,
                            anniversaryStartYear = entry.birthYear ?: oldMeta?.anniversaryStartYear
                        )
                    )
                    importedCount++
                }
                importedCount
            }
        }
}
