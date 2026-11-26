package com.dx.calendar.data.local

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class SharedEventComment(
    val id: String = java.util.UUID.randomUUID().toString(),
    val author: String,
    val content: String,
    val createdAtMillis: Long = System.currentTimeMillis()
)

/**
 * 輕量擴充中繼資料（倒數日／紀念日出生年／圖片附件 URI／共享協作留言）：
 * 獨立儲存於 JSON 側寫檔，完全不動 Room Schema、零升級資料遺失風險，且完整納入整庫備份還原！
 */
data class EventRichMeta(
    val key: String, // e.g. "LOCAL_EVENT:12" or "SYSTEM_EVENT:998" or "TASK:5"
    val isCountdown: Boolean = false,
    val anniversaryStartYear: Int? = null,
    val isBirthday: Boolean = false,
    val attachmentUris: List<String> = emptyList(),
    val sharedComments: List<SharedEventComment> = emptyList()
)

object EventRichMetaStore {
    private const val PREF_NAME = "dx_event_rich_meta_v2"
    private const val KEY_MAP = "meta_map_json"
    private val gson = Gson()
    private val _metaMap = MutableStateFlow<Map<String, EventRichMeta>>(emptyMap())
    val metaMap: StateFlow<Map<String, EventRichMeta>> = _metaMap.asStateFlow()

    @Volatile
    private var loaded = false

    fun makeKey(sourceTypeName: String, rawId: Long): String = "$sourceTypeName:$rawId"

    @Synchronized
    fun ensureLoaded(context: Context): Map<String, EventRichMeta> {
        if (!loaded) {
            val raw = context.applicationContext
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getString(KEY_MAP, null)
            val parsed: Map<String, EventRichMeta> = if (!raw.isNullOrBlank()) {
                runCatching {
                    val type = object : TypeToken<Map<String, EventRichMeta>>() {}.type
                    gson.fromJson<Map<String, EventRichMeta>>(raw, type) ?: emptyMap()
                }.getOrDefault(emptyMap())
            } else emptyMap()
            _metaMap.value = parsed
            loaded = true
        }
        return _metaMap.value
    }

    fun get(context: Context, key: String): EventRichMeta? = ensureLoaded(context)[key]

    fun getAll(context: Context): Map<String, EventRichMeta> = ensureLoaded(context)

    @Synchronized
    fun upsert(context: Context, meta: EventRichMeta) {
        val current = ensureLoaded(context).toMutableMap()
        if (!meta.isCountdown && meta.anniversaryStartYear == null && !meta.isBirthday &&
            meta.attachmentUris.isEmpty() && meta.sharedComments.isEmpty()
        ) {
            current.remove(meta.key)
        } else {
            current[meta.key] = meta
        }
        saveMap(context, current)
    }

    @Synchronized
    fun addComment(context: Context, key: String, author: String, content: String) {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return
        val existing = get(context, key) ?: EventRichMeta(key = key)
        val updated = existing.copy(
            sharedComments = existing.sharedComments + SharedEventComment(
                author = author.ifBlank { "我" },
                content = trimmed
            )
        )
        upsert(context, updated)
    }

    @Synchronized
    fun replaceAll(context: Context, newMap: Map<String, EventRichMeta>) {
        loaded = true
        saveMap(context, newMap)
    }

    private fun saveMap(context: Context, map: Map<String, EventRichMeta>) {
        _metaMap.value = map.toMap()
        context.applicationContext
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MAP, gson.toJson(map))
            .apply()
    }

    fun formatCountdownBadge(targetDate: LocalDate, meta: EventRichMeta?, today: LocalDate = LocalDate.now()): String? {
        if (meta == null) return null
        val parts = mutableListOf<String>()
        if (meta.isCountdown) {
            val diff = ChronoUnit.DAYS.between(today, targetDate).toInt()
            parts += when {
                diff == 0 -> "🎯 就是今天"
                diff > 0 -> "⏳ 還有 ${diff} 天"
                else -> "📌 已過 ${-diff} 天"
            }
        }
        meta.anniversaryStartYear?.let { startY ->
            val years = targetDate.year - startY
            if (years > 0) {
                parts += if (meta.isBirthday) "🎂 ${years} 歲" else "💍 第 ${years} 週年"
            }
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }
}
