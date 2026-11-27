package com.dx.calendar.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * 外部常用 App（Google Tasks / LINE）直開工具：
 * 配合 AndroidManifest `<queries>` 宣告，優先直接啟動手機已安裝之原生 App；
 * 唯有確認裝置未安裝時，才開啟 Google Play 商店下載頁。
 */
object ExternalAppLauncher {

    const val PKG_GOOGLE_TASKS = "com.google.android.apps.tasks"
    const val PKG_LINE = "jp.naver.line.android"

    fun openGoogleTasks(context: Context) {
        if (launchInstalledPackage(context, PKG_GOOGLE_TASKS)) return
        // 僅在手機真的未安裝 Google Tasks 時，才開啟 Play 商店下載頁
        val openedMarket = runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$PKG_GOOGLE_TASKS")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            true
        }.getOrDefault(false)
        if (!openedMarket) {
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$PKG_GOOGLE_TASKS")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            }.onFailure {
                Toast.makeText(context, "找不到 Google Tasks 或 Play 商店", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun openLineApp(context: Context) {
        if (launchInstalledPackage(context, PKG_LINE)) return
        Toast.makeText(context, "此裝置尚未安裝 LINE App", Toast.LENGTH_SHORT).show()
    }

    private fun launchInstalledPackage(context: Context, packageName: String): Boolean {
        val pm = context.packageManager
        // 1. 標準啟動入口
        val launchIntent = pm.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            val ok = runCatching {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                true
            }.getOrDefault(false)
            if (ok) return true
        }
        // 2. 明確 ACTION_MAIN + CATEGORY_LAUNCHER 備援（相容部分陸版/客製 ROM 限制）
        val explicitLauncher = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setPackage(packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val resolved = pm.queryIntentActivities(explicitLauncher, 0).firstOrNull()
        if (resolved != null) {
            explicitLauncher.setClassName(resolved.activityInfo.packageName, resolved.activityInfo.name)
            return runCatching {
                context.startActivity(explicitLauncher)
                true
            }.getOrDefault(false)
        }
        return false
    }
}
