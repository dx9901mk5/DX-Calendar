package com.dx.calendar

import android.Manifest
import android.accounts.AccountManager
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.dx.calendar.security.DualBiometricHelper
import com.dx.calendar.ui.CalendarViewModel
import com.dx.calendar.ui.DXCalendarRootApp
import com.dx.calendar.update.GitHubAutoUpdater
import com.dx.calendar.util.DynamicAppIconManager
import com.dx.calendar.widget.WidgetRefreshHelper
import com.google.android.gms.common.AccountPicker
import java.io.File

class MainActivity : AppCompatActivity() {

    private val viewModel: CalendarViewModel by viewModels()
    private var hasCalPerm by mutableStateOf(false)
    private var awaitingUnknownSourcesApkPath: String? = null
    private var screenOffReceiver: BroadcastReceiver? = null
    private var pendingVoiceResultCallback: ((String) -> Unit)? = null
    private var pendingAttachmentCallback: ((String) -> Unit)? = null

    private val voiceQuickAddLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val spoken = res.data
                ?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            if (spoken.isNotBlank()) {
                pendingVoiceResultCallback?.invoke(spoken)
            }
        }
        pendingVoiceResultCallback = null
    }

    private val eventAttachmentPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            pendingAttachmentCallback?.invoke(uri.toString())
        }
        pendingAttachmentCallback = null
    }

    private val backupExportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.exportFullBackupToUri(uri)
        }
    }

    private val backupRestoreLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.restoreFullBackupFromUri(uri)
        }
    }

    private val calendarPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        hasCalPerm = grants[Manifest.permission.READ_CALENDAR] == true
        if (hasCalPerm) {
            viewModel.refreshSystemCalendars()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    private val contactsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.importContactBirthdays()
        } else {
            Toast.makeText(this, "需要聯絡人權限才能自動匯入聯絡人生日", Toast.LENGTH_SHORT).show()
        }
    }

    private val wallpaperPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            viewModel.setWallpaper(uri.toString(), viewModel.settings.value.wallpaperAlpha)
        }
    }

    private val icsFilePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importIcsFromUri(uri)
        }
    }

    private val googleAccountPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val email = res.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME).orEmpty()
            if (email.isNotBlank()) {
                viewModel.syncGoogleTasksWithEmail(email)
            }
        }
    }

    private val googleTasksConsentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val email = viewModel.settings.value.googleAccountEmail
            if (email.isNotBlank()) {
                viewModel.syncGoogleTasksWithEmail(email)
            }
        }
    }

    private val unknownSourcesPermLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        resumePendingApkInstallIfAllowed()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching { enableEdgeToEdge() }

        hasCalPerm = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED

        if (savedInstanceState == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                runCatching {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }

        handleWidgetIntent(intent)
        DynamicAppIconManager.scheduleMidnightRefresh(this)

        // 監聽螢幕關閉：只要關閉螢幕，立刻重新隱藏隱私日曆內容
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                    viewModel.setPrivacyUnlocked(false)
                }
            }
        }
        screenOffReceiver = receiver
        runCatching {
            registerReceiver(receiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
        }

        setContent {
            val pendingApkPath by viewModel.pendingInstallApkPath.collectAsState()
            val pendingConsentIntent by viewModel.pendingGoogleTasksConsentIntent.collectAsState()

            // 下載 100% 完成時，自動由前景 Activity 直接啟動系統安裝器（若首次未開未知來源權限，開啟授權返回後亦自動接續進入安裝！）
            LaunchedEffect(pendingApkPath) {
                val path = pendingApkPath ?: return@LaunchedEffect
                viewModel.consumePendingInstallApkPath()
                triggerApkInstallWithPermissionCheck(path)
            }

            // Google Tasks 首次綁定 OAuth 授權視窗自動導引
            LaunchedEffect(pendingConsentIntent) {
                val consent = pendingConsentIntent ?: return@LaunchedEffect
                viewModel.consumeGoogleTasksConsentIntent()
                runCatching { googleTasksConsentLauncher.launch(consent) }
            }

            DXCalendarRootApp(
                viewModel = viewModel,
                hasCalendarPermission = hasCalPerm,
                onRequestCalendarPermission = {
                    runCatching {
                        calendarPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.READ_CALENDAR,
                                Manifest.permission.WRITE_CALENDAR
                            )
                        )
                    }
                },
                onLaunchGoogleAccountPicker = {
                    val savedEmail = viewModel.settings.value.googleAccountEmail
                    if (savedEmail.isNotBlank()) {
                        viewModel.syncGoogleTasksWithEmail(savedEmail)
                    } else {
                        launchGoogleAccountChooser()
                    }
                },
                onAddGoogleAccount = {
                    launchGoogleAccountChooser()
                },
                onAddSystemGoogleAccount = {
                    runCatching {
                        val intent = Intent(android.provider.Settings.ACTION_ADD_ACCOUNT).apply {
                            putExtra(android.provider.Settings.EXTRA_ACCOUNT_TYPES, arrayOf("com.google"))
                        }
                        startActivity(intent)
                    }.onFailure {
                        launchGoogleAccountChooser()
                    }
                },
                onTriggerDualBiometric = { onVerified ->
                    DualBiometricHelper.authenticateDualFingerprint(
                        activity = this,
                        requireTwoSteps = true,
                        onStageOnePassed = {
                            Toast.makeText(this, "請再次感應指紋", Toast.LENGTH_SHORT).show()
                        },
                        onUnlocked = {
                            onVerified()
                        },
                        onFallbackToPin = { reason ->
                            Toast.makeText(this, reason, Toast.LENGTH_SHORT).show()
                        },
                        onErrorMessage = { msg ->
                            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                        }
                    )
                },
                onPickWallpaperImage = {
                    runCatching { wallpaperPickerLauncher.launch(arrayOf("image/*")) }
                },
                onPickIcsFile = {
                    runCatching { icsFilePickerLauncher.launch(arrayOf("text/calendar", "*/*")) }
                },
                onLaunchVoiceInput = { callback ->
                    pendingVoiceResultCallback = callback
                    runCatching {
                        val intent = Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(
                                android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                            )
                            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, java.util.Locale.getDefault())
                            putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "請說出行程，例如：明天下午三點半開會 提前15分")
                        }
                        voiceQuickAddLauncher.launch(intent)
                    }.onFailure {
                        pendingVoiceResultCallback = null
                        Toast.makeText(this, "此裝置未安裝語音辨識引擎，請改以文字一句話輸入", Toast.LENGTH_SHORT).show()
                    }
                },
                onPickEventAttachment = { callback ->
                    pendingAttachmentCallback = callback
                    runCatching { eventAttachmentPickerLauncher.launch(arrayOf("image/*")) }
                        .onFailure { pendingAttachmentCallback = null }
                },
                onUploadBackupToGoogleDrive = {
                    viewModel.buildGoogleDriveBackupIntent { intent ->
                        runCatching { startActivity(intent) }
                            .onFailure {
                                Toast.makeText(this, "無法開啟 Google 雲端硬碟 App，請改用「匯出備份檔」", Toast.LENGTH_SHORT).show()
                            }
                    }
                },
                onExportFullBackupFile = {
                    val fname = com.dx.calendar.data.backup.FullBackupManager.buildBackupFileName(
                        viewModel.settings.value.backupDeviceName
                    )
                    runCatching { backupExportLauncher.launch(fname) }
                },
                onRestoreFullBackupFile = {
                    runCatching { backupRestoreLauncher.launch(arrayOf("application/json", "*/*")) }
                },
                onImportContactBirthdays = {
                    val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) ==
                        PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        viewModel.importContactBirthdays()
                    } else {
                        contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                    }
                }
            )
        }
    }

    private fun launchGoogleAccountChooser() {
        runCatching {
            val pickerIntent = AccountPicker.newChooseAccountIntent(
                AccountPicker.AccountChooserOptions.Builder()
                    .setAllowableAccountsTypes(listOf("com.google"))
                    .setAlwaysShowAccountPicker(true)
                    .build()
            )
            googleAccountPickerLauncher.launch(pickerIntent)
        }.onFailure {
            Toast.makeText(this, "無法開啟 Google 帳號選擇器，請確認 Google 服務狀態", Toast.LENGTH_SHORT).show()
        }
    }

    private fun triggerApkInstallWithPermissionCheck(apkPath: String) {
        val file = File(apkPath)
        if (!file.exists()) return
        if (!GitHubAutoUpdater.canInstallPackages(this)) {
            awaitingUnknownSourcesApkPath = apkPath
            Toast.makeText(this, "請開啟「允許來自此來源的應用程式」，返回後將自動進入安裝", Toast.LENGTH_LONG).show()
            runCatching {
                unknownSourcesPermLauncher.launch(GitHubAutoUpdater.buildUnknownSourcesSettingsIntent(this))
            }
            return
        }
        awaitingUnknownSourcesApkPath = null
        GitHubAutoUpdater.launchApkInstaller(this, file)
    }

    private fun resumePendingApkInstallIfAllowed() {
        val path = awaitingUnknownSourcesApkPath ?: return
        if (GitHubAutoUpdater.canInstallPackages(this)) {
            awaitingUnknownSourcesApkPath = null
            val file = File(path)
            if (file.exists()) {
                GitHubAutoUpdater.launchApkInstaller(this, file)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleWidgetIntent(intent)
        DynamicAppIconManager.scheduleMidnightRefresh(this)
    }

    private fun handleWidgetIntent(intent: Intent?) {
        if (intent == null) return
        runCatching {
            val openEpochDay = intent.getLongExtra(WidgetRefreshHelper.EXTRA_OPEN_EPOCH_DAY, Long.MIN_VALUE)
            val openItemRawId = intent.getLongExtra(WidgetRefreshHelper.EXTRA_OPEN_ITEM_RAW_ID, -1L)
            val openIsTask = intent.getBooleanExtra(WidgetRefreshHelper.EXTRA_CLICK_IS_TASK, false)
            val openDayView = intent.getBooleanExtra(WidgetRefreshHelper.EXTRA_OPEN_DAY_VIEW, false)

            if (openEpochDay != Long.MIN_VALUE && openEpochDay in -365243219162L..365241780471L) {
                intent.removeExtra(WidgetRefreshHelper.EXTRA_OPEN_EPOCH_DAY)
                intent.removeExtra(WidgetRefreshHelper.EXTRA_OPEN_ITEM_RAW_ID)
                intent.removeExtra(WidgetRefreshHelper.EXTRA_CLICK_IS_TASK)
                intent.removeExtra(WidgetRefreshHelper.EXTRA_OPEN_DAY_VIEW)

                if (openItemRawId > 0L) {
                    viewModel.openEventFromWidget(openEpochDay, openItemRawId, openIsTask)
                } else {
                    viewModel.openDateFromWidget(openEpochDay, switchToDayView = openDayView)
                }
            }
            if (intent.getBooleanExtra(WidgetRefreshHelper.EXTRA_QUICK_ADD, false)) {
                intent.removeExtra(WidgetRefreshHelper.EXTRA_QUICK_ADD)
                viewModel.openNewEditor()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hasCalPerm = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        viewModel.onAppForegroundResumed()
        resumePendingApkInstallIfAllowed()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            viewModel.setPrivacyUnlocked(false)
            // 多視窗／分割畫面或摺疊視窗切換期間不變更 <activity-alias>，避免系統 ActivityTaskManager 強制終止分割任務閃退
            if (!isInMultiWindowMode && !isFinishing && !isDestroyed) {
                viewModel.syncDynamicAppIconNow()
            }
        }
    }

    override fun onDestroy() {
        viewModel.setPrivacyUnlocked(false)
        screenOffReceiver?.let { r ->
            runCatching { unregisterReceiver(r) }
        }
        screenOffReceiver = null
        super.onDestroy()
    }
}
