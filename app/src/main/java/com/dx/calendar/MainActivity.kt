package com.dx.calendar

import android.Manifest
import android.accounts.AccountManager
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.dx.calendar.security.DualBiometricHelper
import com.dx.calendar.ui.CalendarViewModel
import com.dx.calendar.ui.DXCalendarRootApp
import com.dx.calendar.util.DynamicAppIconManager
import com.dx.calendar.widget.WidgetRefreshHelper
import com.google.android.gms.common.AccountPicker

class MainActivity : AppCompatActivity() {

    private val viewModel: CalendarViewModel by viewModels()
    private var hasCalPerm by mutableStateOf(false)

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        hasCalPerm = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        handleWidgetIntent(intent)
        DynamicAppIconManager.scheduleMidnightRefresh(this)
        viewModel.syncDynamicAppIconNow()

        setContent {
            DXCalendarRootApp(
                viewModel = viewModel,
                hasCalendarPermission = hasCalPerm,
                onRequestCalendarPermission = {
                    calendarPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.READ_CALENDAR,
                            Manifest.permission.WRITE_CALENDAR
                        )
                    )
                },
                onLaunchGoogleAccountPicker = {
                    val savedEmail = viewModel.settings.value.googleAccountEmail
                    if (savedEmail.isNotBlank()) {
                        viewModel.syncGoogleTasksWithEmail(savedEmail)
                    } else {
                        val pickerIntent = AccountPicker.newChooseAccountIntent(
                            AccountPicker.AccountChooserOptions.Builder()
                                .setAllowableAccountsTypes(listOf("com.google"))
                                .build()
                        )
                        googleAccountPickerLauncher.launch(pickerIntent)
                    }
                },
                onTriggerDualBiometric = {
                    val requireTwo = viewModel.settings.value.requireDualFingerprint
                    DualBiometricHelper.authenticateDualFingerprint(
                        activity = this,
                        requireTwoSteps = requireTwo,
                        onStageOnePassed = {
                            Toast.makeText(this, "第一道指紋驗證成功，請感應第二枚指紋！", Toast.LENGTH_SHORT).show()
                        },
                        onUnlocked = {
                            viewModel.setPrivacyUnlocked(true)
                            Toast.makeText(this, "🔓 雙指紋驗證通過，隱私日曆已解鎖", Toast.LENGTH_SHORT).show()
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
                    wallpaperPickerLauncher.launch(arrayOf("image/*"))
                },
                onPickIcsFile = {
                    icsFilePickerLauncher.launch(arrayOf("text/calendar", "*/*"))
                }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleWidgetIntent(intent)
        DynamicAppIconManager.scheduleMidnightRefresh(this)
        viewModel.syncDynamicAppIconNow()
    }

    private fun handleWidgetIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(WidgetRefreshHelper.EXTRA_QUICK_ADD, false) == true) {
            intent.removeExtra(WidgetRefreshHelper.EXTRA_QUICK_ADD)
            viewModel.openNewEditor()
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.syncDynamicAppIconNow()
    }

    override fun onStop() {
        super.onStop()
        // 當離開前景或鎖定螢幕（且非組態旋轉變更）時，自動重新鎖上隱私日曆，確保機密不外洩！
        if (!isChangingConfigurations) {
            viewModel.setPrivacyUnlocked(false)
        }
    }
}
