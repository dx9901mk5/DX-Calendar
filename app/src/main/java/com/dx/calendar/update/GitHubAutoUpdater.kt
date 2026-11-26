package com.dx.calendar.update

import android.app.Activity
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
import okhttp3.CacheControl
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
    private const val LATEST_WEB_URL = "$GITHUB_REPO_URL/releases/latest"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val noRedirectClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    fun getCurrentVersionName(context: Context): String {
        return runCatching {
            val pkgInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pkgInfo.versionName ?: "1.0.0"
        }.getOrDefault("1.0.0")
    }

    suspend fun checkLatestRelease(context: Context): UpdateCheckResult = withContext(Dispatchers.IO) {
        val currentVer = getCurrentVersionName(context)
        val cleanLocal = normalizeVersion(currentVer)

        // 1. 優先透過 GitHub REST API 強制無快取查詢最新版本與更新日誌
        val apiResult = runCatching {
            val request = Request.Builder()
                .url(LATEST_API_URL)
                .cacheControl(CacheControl.FORCE_NETWORK)
                .header("Cache-Control", "no-cache")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "DX-Calendar-Android/$currentVer")
                .build()

            httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IllegalStateException("GitHub API HTTP ${resp.code}")
                }
                val bodyStr = resp.body?.string().orEmpty()
                val jsonObj = JsonParser.parseString(bodyStr).asJsonObject
                val rawTag = jsonObj.get("tag_name")?.asString?.trim().orEmpty()
                if (rawTag.isEmpty()) throw IllegalStateException("Empty tag_name")

                val cleanRemote = normalizeVersion(rawTag)
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
                    UpdateCheckResult.UpToDate(currentVersion = currentVer, latestVersion = cleanRemote)
                }
            }
        }

        if (apiResult.isSuccess) {
            return@withContext apiResult.getOrThrow()
        }

        // 2. 無配額限制備援：若行動網路遭遇 GitHub API 403 Rate Limit，透過 /releases/latest 302 Location 直接解析最新標籤與 APK！
        runCatching {
            val req = Request.Builder()
                .url(LATEST_WEB_URL)
                .cacheControl(CacheControl.FORCE_NETWORK)
                .header("Cache-Control", "no-cache")
                .header("User-Agent", "DX-Calendar-Android/$currentVer")
                .build()

            val rawTag = noRedirectClient.newCall(req).execute().use { resp ->
                val loc = resp.header("Location").orEmpty()
                loc.substringAfterLast("/tag/", "").substringBefore("?").trim()
            }
            if (rawTag.isEmpty()) {
                throw IllegalStateException("無法取得 GitHub 最新版本資訊")
            }

            val cleanRemote = normalizeVersion(rawTag)
            if (compareSemVer(cleanRemote, cleanLocal) > 0) {
                val apkName = "DX-Calendar-$rawTag.apk"
                val apkUrl = "$GITHUB_REPO_URL/releases/download/$rawTag/$apkName"
                UpdateCheckResult.UpdateAvailable(
                    currentVersion = currentVer,
                    info = ReleaseUpdateInfo(
                        tagName = rawTag,
                        cleanVersion = cleanRemote,
                        releaseTitle = "DX日曆 $rawTag",
                        releaseNotes = "偵測到 GitHub 新版本 $rawTag，正在自動下載安裝包...",
                        apkDownloadUrl = apkUrl,
                        apkFileName = apkName,
                        apkSizeBytes = 0L,
                        htmlUrl = "$GITHUB_REPO_URL/releases/tag/$rawTag"
                    )
                )
            } else {
                UpdateCheckResult.UpToDate(currentVersion = currentVer, latestVersion = cleanRemote)
            }
        }.getOrElse { e ->
            if (e is CancellationException) throw e
            UpdateCheckResult.Error("檢查更新失敗：請確認網路連線後再試一次")
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
            updatesDir.listFiles()?.forEach { it.delete() }

            val targetFile = File(updatesDir, info.apkFileName.replace(Regex("[^a-zA-Z0-9._-]"), "_"))
            val req = Request.Builder()
                .url(info.apkDownloadUrl)
                .cacheControl(CacheControl.FORCE_NETWORK)
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
                        val buffer = ByteArray(16 * 1024)
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
            if (!targetFile.exists() || targetFile.length() < 100_000L) {
                throw IllegalStateException("下載的 APK 檔案不完整")
            }
            onProgress(targetFile.length(), targetFile.length(), 1f)
            targetFile
        }
    }

    fun canInstallPackages(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching { context.packageManager.canRequestPackageInstalls() }.getOrDefault(false)
        } else true
    }

    fun buildUnknownSourcesSettingsIntent(context: Context): Intent {
        return Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        )
    }

    /**
     * 啟動系統套件安裝器直接進入覆蓋安裝畫面
     */
    fun launchApkInstaller(context: Context, apkFile: File): Boolean {
        if (!apkFile.exists()) return false
        if (!canInstallPackages(context)) {
            val permIntent = buildUnknownSourcesSettingsIntent(context).apply {
                if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(permIntent) }
            return false
        }

        return runCatching {
            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(installIntent)
            true
        }.getOrDefault(false)
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
