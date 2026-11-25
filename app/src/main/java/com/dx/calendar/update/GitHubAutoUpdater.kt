package com.dx.calendar.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

data class ReleaseUpdateInfo(
    val tagName: String,
    val cleanVersion: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val apkDownloadUrl: String,
    val apkFileName: String,
    val apkSizeBytes: Long,
    val htmlUrl: String
)

sealed class UpdateCheckResult {
    data class UpdateAvailable(val currentVersion: String, val info: ReleaseUpdateInfo) : UpdateCheckResult()
    data class UpToDate(val currentVersion: String, val latestVersion: String) : UpdateCheckResult()
    data class Error(val message: String) : UpdateCheckResult()
}

object GitHubAutoUpdater {

    const val GITHUB_REPO_OWNER = "dx9901mk5"
    const val GITHUB_REPO_NAME = "DX-Calendar"
    const val GITHUB_REPO_URL = "https://github.com/$GITHUB_REPO_OWNER/$GITHUB_REPO_NAME"
    const val GITHUB_RELEASES_URL = "$GITHUB_REPO_URL/releases"
    private const val LATEST_API_URL = "https://api.github.com/repos/$GITHUB_REPO_OWNER/$GITHUB_REPO_NAME/releases/latest"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun getCurrentVersionName(context: Context): String {
        return runCatching {
            val pkgInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pkgInfo.versionName ?: "1.0.0"
        }.getOrDefault("1.0.0")
    }

    suspend fun checkLatestRelease(context: Context): UpdateCheckResult = withContext(Dispatchers.IO) {
        val currentVer = getCurrentVersionName(context)
        runCatching {
            val request = Request.Builder()
                .url(LATEST_API_URL)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "DX-Calendar-Android/$currentVer")
                .build()

            val jsonObj = httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext UpdateCheckResult.Error("無法連線至 GitHub Releases (HTTP ${resp.code})")
                }
                val bodyStr = resp.body?.string().orEmpty()
                JsonParser.parseString(bodyStr).asJsonObject
            }

            val rawTag = jsonObj.get("tag_name")?.asString?.trim().orEmpty()
            if (rawTag.isEmpty()) {
                return@withContext UpdateCheckResult.Error("未讀取到有效的版本標籤")
            }

            val cleanRemote = normalizeVersion(rawTag)
            val cleanLocal = normalizeVersion(currentVer)
            val title = jsonObj.get("name")?.asString?.takeIf { it.isNotBlank() } ?: rawTag
            val notes = jsonObj.get("body")?.asString?.trim().orEmpty()
            val htmlUrl = jsonObj.get("html_url")?.asString ?: GITHUB_RELEASES_URL

            var apkUrl = ""
            var apkName = "DX-Calendar-$rawTag.apk"
            var apkSize = 0L

            val assets = jsonObj.getAsJsonArray("assets")
            if (assets != null) {
                for (elem in assets) {
                    val assetObj = elem.asJsonObject
                    val name = assetObj.get("name")?.asString.orEmpty()
                    val dlUrl = assetObj.get("browser_download_url")?.asString.orEmpty()
                    if (name.endsWith(".apk", ignoreCase = true) && dlUrl.isNotBlank()) {
                        apkUrl = dlUrl
                        apkName = name
                        apkSize = assetObj.get("size")?.asLong ?: 0L
                        break
                    }
                }
            }

            if (compareSemVer(cleanRemote, cleanLocal) > 0 && apkUrl.isNotBlank()) {
                UpdateCheckResult.UpdateAvailable(
                    currentVersion = currentVer,
                    info = ReleaseUpdateInfo(
                        tagName = rawTag,
                        cleanVersion = cleanRemote,
                        releaseTitle = title,
                        releaseNotes = notes,
                        apkDownloadUrl = apkUrl,
                        apkFileName = apkName,
                        apkSizeBytes = apkSize,
                        htmlUrl = htmlUrl
                    )
                )
            } else {
                UpdateCheckResult.UpToDate(
                    currentVersion = currentVer,
                    latestVersion = cleanRemote
                )
            }
        }.getOrElse { e ->
            if (e is CancellationException) throw e
            UpdateCheckResult.Error("檢查更新失敗：${e.localizedMessage ?: "請檢查網路連線"}")
        }
    }

    /**
     * 串流下載更新 APK 並即時回報進度條百分比與已下載位元組
     */
    suspend fun downloadReleaseApk(
        context: Context,
        info: ReleaseUpdateInfo,
        onProgress: (downloadedBytes: Long, totalBytes: Long, fraction: Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
            // 清理舊版下載殘留檔
            updatesDir.listFiles()?.forEach { it.delete() }

            val targetFile = File(updatesDir, info.apkFileName.replace(Regex("[^a-zA-Z0-9._-]"), "_"))
            val req = Request.Builder()
                .url(info.apkDownloadUrl)
                .header("User-Agent", "DX-Calendar-Android")
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IllegalStateException("APK 下載失敗 (HTTP ${resp.code})")
                }
                val body = resp.body ?: throw IllegalStateException("空的回應內容")
                val total = body.contentLength().takeIf { it > 0 } ?: info.apkSizeBytes

                body.byteStream().use { input ->
                    FileOutputStream(targetFile).use { output ->
                        val buffer = ByteArray(8 * 1024)
                        var downloaded = 0L
                        var lastEmitTime = 0L

                        while (true) {
                            coroutineContext.ensureActive()
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloaded += read

                            val now = System.currentTimeMillis()
                            if (now - lastEmitTime >= 60L || downloaded == total) {
                                lastEmitTime = now
                                val frac = if (total > 0L) {
                                    (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                                } else 0.5f
                                onProgress(downloaded, total, frac)
                            }
                        }
                        output.flush()
                    }
                }
            }
            onProgress(targetFile.length(), targetFile.length(), 1f)
            targetFile
        }
    }

    /**
     * 檢查安裝權限並自動啟動系統套件安裝器（若尚未允許安裝未知應用，自動跳轉授權設定頁）
     */
    fun launchApkInstaller(context: Context, apkFile: File): Boolean {
        if (!apkFile.exists()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val permIntent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(permIntent)
                return false
            }
        }

        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(installIntent)
        return true
    }

    fun normalizeVersion(raw: String): String {
        return raw.trim().removePrefix("v").removePrefix("V").substringBefore("-")
    }

    fun compareSemVer(v1: String, v2: String): Int {
        val p1 = normalizeVersion(v1).split(".").map { it.toIntOrNull() ?: 0 }
        val p2 = normalizeVersion(v2).split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(p1.size, p2.size)
        for (i in 0 until maxLen) {
            val a = p1.getOrElse(i) { 0 }
            val b = p2.getOrElse(i) { 0 }
            if (a != b) return a.compareTo(b)
        }
        return 0
    }
}
