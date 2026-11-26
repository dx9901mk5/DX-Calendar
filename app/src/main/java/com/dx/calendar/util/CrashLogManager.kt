package com.dx.calendar.util

import android.content.Context
import android.os.Build
import com.dx.calendar.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * 全域崩潰捕捉紀錄器：將最近一次未捕捉例外安全寫入 `filesDir/last_crash.txt`，
 * 供設定頁檢視與一鍵複製除錯，協助精準定位摺疊機／實機問題。
 */
object CrashLogManager {

    private const val CRASH_FILE_NAME = "last_crash.txt"

    fun install(context: Context) {
        val appCtx = context.applicationContext ?: context
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                writeCrash(appCtx, thread, throwable)
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun writeCrash(context: Context, thread: Thread, throwable: Throwable) {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val timeStr = runCatching {
            ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        }.getOrDefault(System.currentTimeMillis().toString())

        val report = buildString {
            appendLine("=== DX-Calendar Crash Report ===")
            appendLine("Time: $timeStr")
            appendLine("Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) | PlayBuild=${BuildConfig.IS_PLAY_BUILD}")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} | Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Thread: ${thread.name}")
            appendLine("--------------------------------")
            append(sw.toString())
        }
        File(context.filesDir, CRASH_FILE_NAME).writeText(report, Charsets.UTF_8)
    }

    fun readLastCrash(context: Context): String? {
        return runCatching {
            val f = File(context.filesDir, CRASH_FILE_NAME)
            if (f.exists() && f.length() > 0L) f.readText(Charsets.UTF_8) else null
        }.getOrNull()
    }

    fun clearLastCrash(context: Context) {
        runCatching {
            val f = File(context.filesDir, CRASH_FILE_NAME)
            if (f.exists()) f.delete()
        }
    }
}
