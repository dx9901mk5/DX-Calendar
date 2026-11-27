package com.dx.calendar.data.local

import android.content.Context
import java.time.LocalDate

/**
 * 重複活動／重複例行事務的單日排除與截止日儲存器（免 Room Schema Migration、100% 跨版本零閃退）
 *
 * 支援：
 * 1. 僅刪除此日（THIS_DAY_ONLY）：將指定 ISO 日期加入排除集合，月曆、時間表、小工具與鬧鐘排程皆自動跳過該日，不影響後續所有重複提醒。
 * 2. 刪除此日與後續全部（THIS_AND_FOLLOWING）：記錄重複截止日（前一天），保留過去歷史，停止後續重複與提醒。
 */
object RecurrenceExclusionStore {

    private const val PREF_NAME = "dx_recurrence_exclusions_v1"

    private fun excludedKey(sourceTypeName: String, rawId: Long) = "ex_${sourceTypeName}_$rawId"
    private fun cutoffKey(sourceTypeName: String, rawId: Long) = "cut_${sourceTypeName}_$rawId"

    fun addExcludedDate(context: Context, sourceTypeName: String, rawId: Long, date: LocalDate) {
        val prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val key = excludedKey(sourceTypeName, rawId)
        val existing = prefs.getStringSet(key, emptySet())?.toMutableSet() ?: mutableSetOf()
        existing.add(date.toString())
        prefs.edit().putStringSet(key, existing).apply()
    }

    fun getExcludedDates(context: Context, sourceTypeName: String, rawId: Long): Set<String> {
        val prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(excludedKey(sourceTypeName, rawId), emptySet())?.toSet() ?: emptySet()
    }

    fun isDateExcluded(context: Context, sourceTypeName: String, rawId: Long, date: LocalDate): Boolean {
        return getExcludedDates(context, sourceTypeName, rawId).contains(date.toString())
    }

    fun setCutoffEndDate(context: Context, sourceTypeName: String, rawId: Long, endDateInclusive: LocalDate) {
        val prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(cutoffKey(sourceTypeName, rawId), endDateInclusive.toString()).apply()
    }

    fun getCutoffEndDate(context: Context, sourceTypeName: String, rawId: Long): LocalDate? {
        val prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(cutoffKey(sourceTypeName, rawId), null) ?: return null
        return runCatching { LocalDate.parse(raw) }.getOrNull()
    }

    fun clearAllForItem(context: Context, sourceTypeName: String, rawId: Long) {
        val prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(excludedKey(sourceTypeName, rawId))
            .remove(cutoffKey(sourceTypeName, rawId))
            .apply()
    }
}
