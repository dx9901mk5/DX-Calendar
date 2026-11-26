package com.dx.calendar.data.local

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 輪班與常用行程範本（支援一鍵連點快速排班 Stamp Mode）
 */
data class EventTemplateItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val startHour: Int,
    val startMinute: Int,
    val durationMinutes: Int,
    val isAllDay: Boolean = false,
    val colorArgb: Int = 0xFFC8962E.toInt(),
    val location: String = "",
    val notes: String = "",
    val reminderMinutes: List<Int> = listOf(0, 15),
    val enableMultiCall: Boolean = false
)

object EventTemplateStore {
    private const val PREF_NAME = "dx_event_templates_v2"
    private const val KEY_LIST = "templates_json"
    private val gson = Gson()
    private val _templates = MutableStateFlow<List<EventTemplateItem>>(emptyList())
    val templates: StateFlow<List<EventTemplateItem>> = _templates.asStateFlow()

    @Volatile
    private var loaded = false

    private fun defaultTemplates(): List<EventTemplateItem> = listOf(
        EventTemplateItem(
            id = "tpl_day_shift",
            name = "早班",
            startHour = 7,
            startMinute = 0,
            durationMinutes = 480,
            colorArgb = 0xFF2E7D32.toInt(),
            reminderMinutes = listOf(0, 30)
        ),
        EventTemplateItem(
            id = "tpl_evening_shift",
            name = "小夜班",
            startHour = 15,
            startMinute = 0,
            durationMinutes = 480,
            colorArgb = 0xFF1976D2.toInt(),
            reminderMinutes = listOf(0, 30)
        ),
        EventTemplateItem(
            id = "tpl_night_shift",
            name = "大夜班",
            startHour = 23,
            startMinute = 0,
            durationMinutes = 480,
            colorArgb = 0xFF6A1B9A.toInt(),
            reminderMinutes = listOf(0, 30)
        ),
        EventTemplateItem(
            id = "tpl_day_off",
            name = "休假",
            startHour = 9,
            startMinute = 0,
            durationMinutes = 60,
            isAllDay = true,
            colorArgb = 0xFFC8962E.toInt(),
            reminderMinutes = emptyList()
        ),
        EventTemplateItem(
            id = "tpl_workout",
            name = "健身運動",
            startHour = 19,
            startMinute = 30,
            durationMinutes = 60,
            colorArgb = 0xFFE65100.toInt(),
            reminderMinutes = listOf(0, 15)
        )
    )

    @Synchronized
    fun ensureLoaded(context: Context): List<EventTemplateItem> {
        if (!loaded) {
            val raw = context.applicationContext
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getString(KEY_LIST, null)
            val parsed: List<EventTemplateItem> = if (!raw.isNullOrBlank()) {
                runCatching {
                    val type = object : TypeToken<List<EventTemplateItem>>() {}.type
                    gson.fromJson<List<EventTemplateItem>>(raw, type)
                }.getOrNull()?.takeIf { it.isNotEmpty() } ?: defaultTemplates()
            } else {
                defaultTemplates()
            }
            _templates.value = parsed
            loaded = true
        }
        return _templates.value
    }

    @Synchronized
    fun addOrUpdate(context: Context, item: EventTemplateItem) {
        val list = ensureLoaded(context).toMutableList()
        val idx = list.indexOfFirst { it.id == item.id }
        if (idx >= 0) list[idx] = item else list.add(0, item)
        saveList(context, list)
    }

    @Synchronized
    fun delete(context: Context, id: String) {
        val list = ensureLoaded(context).filterNot { it.id == id }
        saveList(context, list)
    }

    @Synchronized
    fun replaceAll(context: Context, items: List<EventTemplateItem>) {
        loaded = true
        saveList(context, items.ifEmpty { defaultTemplates() })
    }

    private fun saveList(context: Context, list: List<EventTemplateItem>) {
        _templates.value = list
        context.applicationContext
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LIST, gson.toJson(list))
            .apply()
    }
}
