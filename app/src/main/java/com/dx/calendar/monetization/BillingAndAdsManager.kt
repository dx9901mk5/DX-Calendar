package com.dx.calendar.monetization

import android.app.Activity
import android.content.Context
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 離線序號驗證器（格式：DXPRO-XXXX-XXXX-CCCC）
 * 前兩段 8 碼為序號主體，末 4 碼為 SHA-256 簽章校驗碼（不區分大小寫與空白），
 * 驗證通過即可永久解鎖 DX Pro 全功能與無廣告權限，無需外部伺服器。
 */
object DxLicenseCodeValidator {
    private const val SALT = "DX_CALENDAR_PRO_LICENSE_V1_2026"
    private val FORMAT_REGEX = Regex("^DXPRO-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-F0-9]{4}$")

    fun normalize(raw: String): String =
        raw.trim().uppercase(Locale.ROOT).replace(" ", "")

    fun computeChecksum(seg1: String, seg2: String): String {
        val payload = "${seg1.uppercase(Locale.ROOT)}-${seg2.uppercase(Locale.ROOT)}|$SALT"
        val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8))
        return digest.take(2).joinToString("") { "%02X".format(it) }
    }

    fun generateCode(seg1: String, seg2: String): String {
        val s1 = seg1.trim().uppercase(Locale.ROOT).take(4).padEnd(4, '0')
        val s2 = seg2.trim().uppercase(Locale.ROOT).take(4).padEnd(4, '0')
        return "DXPRO-$s1-$s2-${computeChecksum(s1, s2)}"
    }

    fun isValid(raw: String): Boolean {
        val norm = normalize(raw)
        if (!FORMAT_REGEX.matches(norm)) return false
        val parts = norm.split("-")
        if (parts.size != 4) return false
        val expected = computeChecksum(parts[1], parts[2])
        return parts[3] == expected
    }
}

/**
 * DX日曆 Google Play 內購商品 ID（以新台幣為基準）
 * - 月訂閱：NT$ 49 / 月
 * - 年訂閱：NT$ 290 / 年（主力推薦，附 7 天免費試用）
 * - 終身買斷：NT$ 590 一次買斷
 * - 單純去廣告包：NT$ 150 一次買斷
 */
object DxBillingProducts {
    const val SUB_MONTHLY = "dx_pro_monthly"
    const val SUB_YEARLY = "dx_pro_yearly"
    const val INAPP_LIFETIME = "dx_pro_lifetime"
    const val INAPP_REMOVE_ADS = "dx_remove_ads"

    val ALL_SUBS = listOf(SUB_MONTHLY, SUB_YEARLY)
    val ALL_INAPP = listOf(INAPP_LIFETIME, INAPP_REMOVE_ADS)

    // Google AdMob 官方測試單元 ID（上架正式版後於 Play Console / AdMob 後台替換為正式單元 ID）
    const val ADMOB_BANNER_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
    const val ADMOB_REWARDED_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"

    const val FREE_TRIAL_DAYS = 7L
    const val REWARDED_UNLOCK_HOURS = 24L
}

data class DxEntitlementState(
    val isInFreeTrial: Boolean = true,
    val trialDaysRemaining: Int = 7,
    val isRewardedUnlockActive: Boolean = false,
    val rewardedHoursRemaining: Int = 0,
    val isProPurchased: Boolean = false,
    val isLicenseCodeRedeemed: Boolean = false,
    val isRemoveAdsPurchased: Boolean = false,
    val activePlanLabel: String = "新手 7 天全功能免費體驗"
) {
    /** 是否具備完整 Pro 功能權限（7天免費試用 OR 激勵廣告24h解鎖 OR 已購 Pro OR 序號解鎖） */
    val hasProFeatures: Boolean
        get() = isInFreeTrial || isRewardedUnlockActive || isProPurchased || isLicenseCodeRedeemed

    /** 是否顯示低干擾橫幅廣告（免費體驗期間、激勵解鎖24h內、已購 Pro／去廣告包或序號解鎖皆 100% 不顯示廣告） */
    val shouldShowBannerAd: Boolean
        get() = !isInFreeTrial && !isRewardedUnlockActive && !isProPurchased && !isLicenseCodeRedeemed && !isRemoveAdsPurchased
}

data class DxBillingUiState(
    val isConnected: Boolean = false,
    val monthlyPrice: String = "NT$ 49 / 月",
    val yearlyPrice: String = "NT$ 290 / 年",
    val lifetimePrice: String = "NT$ 590 終身買斷",
    val removeAdsPrice: String = "NT$ 150 一次去廣告",
    val isRewardedAdReady: Boolean = false,
    val statusMessage: String = ""
)

object EntitlementEvaluator {
    fun evaluate(
        firstInstallMillis: Long,
        rewardedUnlockUntilMillis: Long,
        isProPurchased: Boolean,
        isRemoveAdsPurchased: Boolean,
        redeemedLicenseCode: String = "",
        nowMillis: Long = System.currentTimeMillis()
    ): DxEntitlementState {
        val licenseValid = redeemedLicenseCode.isNotBlank() && DxLicenseCodeValidator.isValid(redeemedLicenseCode)
        val safeInstall = if (firstInstallMillis > 0L) firstInstallMillis else nowMillis
        val trialEndMillis = safeInstall + TimeUnit.DAYS.toMillis(DxBillingProducts.FREE_TRIAL_DAYS)
        val trialRemMs = (trialEndMillis - nowMillis).coerceAtLeast(0L)
        val inTrial = trialRemMs > 0L
        val trialDays = if (inTrial) ((trialRemMs + TimeUnit.DAYS.toMillis(1) - 1) / TimeUnit.DAYS.toMillis(1)).toInt().coerceIn(1, 7) else 0

        val rewardRemMs = (rewardedUnlockUntilMillis - nowMillis).coerceAtLeast(0L)
        val rewardActive = rewardRemMs > 0L
        val rewardHours = if (rewardActive) ((rewardRemMs + TimeUnit.HOURS.toMillis(1) - 1) / TimeUnit.HOURS.toMillis(1)).toInt().coerceIn(1, 24) else 0

        val label = when {
            isProPurchased -> "DX Pro 正式會員（無廣告＋全功能）"
            licenseValid -> "DX Pro 序號授權會員（無廣告＋全功能）"
            inTrial -> "新手 7 天免費全功能體驗（剩餘 $trialDays 天・無廣告）"
            rewardActive -> "激勵解鎖體驗中（剩餘 $rewardHours 小時・無廣告）"
            isRemoveAdsPurchased -> "純淨去廣告版"
            else -> "免費版（含低干擾橫幅廣告）"
        }

        return DxEntitlementState(
            isInFreeTrial = inTrial,
            trialDaysRemaining = trialDays,
            isRewardedUnlockActive = rewardActive,
            rewardedHoursRemaining = rewardHours,
            isProPurchased = isProPurchased,
            isLicenseCodeRedeemed = licenseValid,
            isRemoveAdsPurchased = isRemoveAdsPurchased,
            activePlanLabel = label
        )
    }
}

/**
 * Google Play Billing v7 內購與 AdMob 激勵廣告管理器
 * 注意：依產品規範，絕不載入任何插頁廣告 (Interstitial) 與開屏廣告。
 */
class DxMonetizationManager(
    context: Context,
    private val onPurchaseEntitlementChanged: (isPro: Boolean, isRemoveAds: Boolean) -> Unit,
    private val onRewarded24hGranted: () -> Unit,
    private val onToast: (String) -> Unit
) : PurchasesUpdatedListener {

    private val appContext = context.applicationContext ?: context
    private val productDetailsMap = mutableMapOf<String, ProductDetails>()
    private var rewardedAd: RewardedAd? = null
    private var isAdsInitialized = false

    private val _billingUiState = MutableStateFlow(DxBillingUiState())
    val billingUiState: StateFlow<DxBillingUiState> = _billingUiState.asStateFlow()

    private val billingClient: BillingClient = BillingClient.newBuilder(appContext)
        .setListener(this)
        .enablePendingPurchases(
            com.android.billingclient.api.PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .build()

    fun initialize() {
        connectBilling()
        initializeAds()
    }

    private fun initializeAds() {
        if (isAdsInitialized) return
        isAdsInitialized = true
        runCatching {
            MobileAds.initialize(appContext) {
                preloadRewardedAd()
            }
        }
    }

    fun preloadRewardedAd() {
        runCatching {
            val req = AdRequest.Builder().build()
            RewardedAd.load(
                appContext,
                DxBillingProducts.ADMOB_REWARDED_UNIT_ID,
                req,
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedAd) {
                        rewardedAd = ad
                        _billingUiState.value = _billingUiState.value.copy(isRewardedAdReady = true)
                    }

                    override fun onAdFailedToLoad(err: LoadAdError) {
                        rewardedAd = null
                        _billingUiState.value = _billingUiState.value.copy(isRewardedAdReady = false)
                    }
                }
            )
        }
    }

    fun showRewardedAdFor24hUnlock(activity: Activity) {
        val ad = rewardedAd
        if (ad == null) {
            preloadRewardedAd()
            onToast("正在載入激勵影片廣告，請稍候 2 秒再點擊一次")
            return
        }
        rewardedAd = null
        _billingUiState.value = _billingUiState.value.copy(isRewardedAdReady = false)
        runCatching {
            ad.show(activity) {
                onRewarded24hGranted()
                onToast("🎉 已完成觀看！為您解鎖 24 小時無廣告與全功能 DX Pro 體驗")
                preloadRewardedAd()
            }
        }.onFailure {
            preloadRewardedAd()
            onToast("廣告顯示失敗，請稍後再試")
        }
    }

    private fun connectBilling() {
        runCatching {
            billingClient.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        _billingUiState.value = _billingUiState.value.copy(isConnected = true)
                        queryAllProductDetails()
                        restorePurchases()
                    }
                }

                override fun onBillingServiceDisconnected() {
                    _billingUiState.value = _billingUiState.value.copy(isConnected = false)
                }
            })
        }
    }

    private fun queryAllProductDetails() {
        val subList = DxBillingProducts.ALL_SUBS.map { id ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(id)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }
        val inAppList = DxBillingProducts.ALL_INAPP.map { id ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(id)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }

        billingClient.queryProductDetailsAsync(
            QueryProductDetailsParams.newBuilder().setProductList(subList).build()
        ) { res, detailsResult ->
            if (res.responseCode == BillingClient.BillingResponseCode.OK) {
                detailsResult.productDetailsList.forEach { d -> productDetailsMap[d.productId] = d }
                refreshFormattedPrices()
            }
        }

        billingClient.queryProductDetailsAsync(
            QueryProductDetailsParams.newBuilder().setProductList(inAppList).build()
        ) { res, detailsResult ->
            if (res.responseCode == BillingClient.BillingResponseCode.OK) {
                detailsResult.productDetailsList.forEach { d -> productDetailsMap[d.productId] = d }
                refreshFormattedPrices()
            }
        }
    }

    private fun refreshFormattedPrices() {
        val mPrice = productDetailsMap[DxBillingProducts.SUB_MONTHLY]
            ?.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.lastOrNull()?.formattedPrice
        val yPrice = productDetailsMap[DxBillingProducts.SUB_YEARLY]
            ?.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.lastOrNull()?.formattedPrice
        val lPrice = productDetailsMap[DxBillingProducts.INAPP_LIFETIME]?.oneTimePurchaseOfferDetails?.formattedPrice
        val rPrice = productDetailsMap[DxBillingProducts.INAPP_REMOVE_ADS]?.oneTimePurchaseOfferDetails?.formattedPrice

        _billingUiState.value = _billingUiState.value.copy(
            monthlyPrice = mPrice?.let { "$it / 月" } ?: _billingUiState.value.monthlyPrice,
            yearlyPrice = yPrice?.let { "$it / 年" } ?: _billingUiState.value.yearlyPrice,
            lifetimePrice = lPrice?.let { "$it 終身買斷" } ?: _billingUiState.value.lifetimePrice,
            removeAdsPrice = rPrice?.let { "$it 一次去廣告" } ?: _billingUiState.value.removeAdsPrice
        )
    }

    fun launchPurchase(activity: Activity, productId: String) {
        if (!billingClient.isReady) {
            connectBilling()
            onToast("正在連線 Google Play 商店，請稍候再試")
            return
        }
        val details = productDetailsMap[productId]
        if (details == null) {
            queryAllProductDetails()
            onToast("Google Play 商店商品尚在同步或需透過 Play 商店安裝版本才可結帳")
            return
        }
        val productParamsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
        if (details.productType == BillingClient.ProductType.SUBS) {
            val offerToken = details.subscriptionOfferDetails?.firstOrNull()?.offerToken
            if (offerToken.isNullOrBlank()) {
                onToast("無法取得訂閱方案資訊，請稍後再試")
                return
            }
            productParamsBuilder.setOfferToken(offerToken)
        }
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParamsBuilder.build()))
            .build()
        billingClient.launchBillingFlow(activity, flowParams)
    }

    fun restorePurchases() {
        if (!billingClient.isReady) return
        var proFound = false
        var removeAdsFound = false

        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
        ) { res, subsPurchases ->
            if (res.responseCode == BillingClient.BillingResponseCode.OK) {
                subsPurchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }.forEach { p ->
                    acknowledgeIfNeeded(p)
                    if (p.products.any { it in DxBillingProducts.ALL_SUBS }) proFound = true
                }
            }
            billingClient.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
            ) { res2, inAppPurchases ->
                if (res2.responseCode == BillingClient.BillingResponseCode.OK) {
                    inAppPurchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }.forEach { p ->
                        acknowledgeIfNeeded(p)
                        if (p.products.contains(DxBillingProducts.INAPP_LIFETIME)) proFound = true
                        if (p.products.contains(DxBillingProducts.INAPP_REMOVE_ADS)) removeAdsFound = true
                    }
                }
                onPurchaseEntitlementChanged(proFound, removeAdsFound)
            }
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            var pro = false
            var removeAds = false
            purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }.forEach { p ->
                acknowledgeIfNeeded(p)
                if (p.products.any { it in DxBillingProducts.ALL_SUBS || it == DxBillingProducts.INAPP_LIFETIME }) {
                    pro = true
                }
                if (p.products.contains(DxBillingProducts.INAPP_REMOVE_ADS)) {
                    removeAds = true
                }
            }
            if (pro || removeAds) {
                onPurchaseEntitlementChanged(pro, removeAds)
                onToast("🎉 感謝支持！您的 DX Pro 權益已即時生效")
            }
        } else if (billingResult.responseCode != BillingClient.BillingResponseCode.USER_CANCELED) {
            onToast("結帳未完成（代碼 ${billingResult.responseCode}）")
        }
    }

    private fun acknowledgeIfNeeded(purchase: Purchase) {
        if (!purchase.isAcknowledged) {
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            billingClient.acknowledgePurchase(params) { }
        }
    }

    fun destroy() {
        runCatching { billingClient.endConnection() }
    }
}

/**
 * 低干擾 AdMob 橫幅廣告元件（僅於免費體驗結束、未付費且未處於 24h 激勵解鎖時顯示）
 */
@Composable
fun DxAdaptiveBannerAd(
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    if (!visible) return
    val context = LocalContext.current
    val adView = remember(context) {
        runCatching {
            AdView(context).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = DxBillingProducts.ADMOB_BANNER_UNIT_ID
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
                loadAd(AdRequest.Builder().build())
            }
        }.getOrNull()
    }
    if (adView != null) {
        DisposableEffect(adView) {
            onDispose { runCatching { adView.destroy() } }
        }
        AndroidView(
            factory = { adView },
            modifier = modifier.fillMaxWidth()
        )
    }
}
