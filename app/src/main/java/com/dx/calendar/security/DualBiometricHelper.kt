package com.dx.calendar.security

import android.os.Handler
import android.os.Looper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * 雙指紋鎖驗證引擎：
 * 支援連續兩道 BiometricPrompt 指紋辨識驗證（第一枚指紋通過後，自動啟動第二道指紋確認），
 * 確保「隱私日曆」具備雙重生物辨識安全防護！
 */
object DualBiometricHelper {

    fun canUseBiometric(activity: FragmentActivity): Boolean {
        val manager = BiometricManager.from(activity)
        val res = manager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
        )
        return res == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun authenticateDualFingerprint(
        activity: FragmentActivity,
        requireTwoSteps: Boolean = true,
        onStageOnePassed: () -> Unit = {},
        onUnlocked: () -> Unit,
        onFallbackToPin: (reason: String) -> Unit,
        onErrorMessage: (String) -> Unit
    ) {
        if (activity.isFinishing || activity.isDestroyed) return
        if (!canUseBiometric(activity)) {
            onFallbackToPin("此裝置目前未註冊指紋或無指紋感應器，請使用雙重隱私 PIN 碼驗證")
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)

        val stage1Callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                if (!requireTwoSteps) {
                    onUnlocked()
                    return
                }
                onStageOnePassed()
                // 延遲 350ms 啟動第二道指紋鎖驗證
                Handler(Looper.getMainLooper()).postDelayed({
                    if (!activity.isFinishing && !activity.isDestroyed) {
                        launchSecondFingerprintStage(
                            activity = activity,
                            onUnlocked = onUnlocked,
                            onFallbackToPin = onFallbackToPin,
                            onErrorMessage = onErrorMessage
                        )
                    }
                }, 350L)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    onFallbackToPin("請輸入密碼完成驗證")
                } else if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                    errorCode != BiometricPrompt.ERROR_CANCELED
                ) {
                    onErrorMessage("指紋驗證未通過：$errString")
                }
            }
        }

        runCatching {
            val prompt1 = BiometricPrompt(activity, executor, stage1Callback)
            val info1 = BiometricPrompt.PromptInfo.Builder()
                .setTitle("指紋驗證")
                .setSubtitle("請使用指紋驗證")
                .setNegativeButtonText("使用密碼")
                .setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
                )
                .build()

            prompt1.authenticate(info1)
        }.onFailure {
            onFallbackToPin("生物辨識暫時無法啟動，請使用密碼驗證")
        }
    }

    private fun launchSecondFingerprintStage(
        activity: FragmentActivity,
        onUnlocked: () -> Unit,
        onFallbackToPin: (reason: String) -> Unit,
        onErrorMessage: (String) -> Unit
    ) {
        if (activity.isFinishing || activity.isDestroyed) return
        val executor = ContextCompat.getMainExecutor(activity)
        val stage2Callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onUnlocked()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    onFallbackToPin("請輸入密碼完成驗證")
                } else if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                    errorCode != BiometricPrompt.ERROR_CANCELED
                ) {
                    onErrorMessage("指紋驗證未通過：$errString")
                }
            }
        }

        runCatching {
            val prompt2 = BiometricPrompt(activity, executor, stage2Callback)
            val info2 = BiometricPrompt.PromptInfo.Builder()
                .setTitle("指紋驗證")
                .setSubtitle("請再次使用指紋驗證")
                .setNegativeButtonText("使用密碼")
                .setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
                )
                .build()

            prompt2.authenticate(info2)
        }.onFailure {
            onFallbackToPin("請輸入密碼完成驗證")
        }
    }
}
