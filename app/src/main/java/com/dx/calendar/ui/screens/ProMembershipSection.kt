package com.dx.calendar.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dx.calendar.monetization.DxBillingProducts
import com.dx.calendar.monetization.DxBillingUiState
import com.dx.calendar.monetization.DxEntitlementState
import com.dx.calendar.ui.theme.LocalBrandTokens
import java.util.Locale

private enum class UiLang { ZH_TW, ZH_CN, EN, JA }

private fun currentUiLang(): UiLang {
    val l = Locale.getDefault()
    val lang = l.language.lowercase(Locale.ROOT)
    val country = l.country.uppercase(Locale.ROOT)
    return when {
        lang == "ja" -> UiLang.JA
        lang == "en" -> UiLang.EN
        lang == "zh" && (country == "CN" || country == "SG") -> UiLang.ZH_CN
        else -> UiLang.ZH_TW
    }
}

/**
 * DX Pro 方案、7天免費全體驗提示、低干擾廣告說明、Google Play 內購與四語系「關於我們」區塊
 */
@Composable
fun ProMembershipAndAboutSection(
    entitlement: DxEntitlementState,
    billingUi: DxBillingUiState,
    currentVersion: String,
    onPurchaseProduct: (String) -> Unit,
    onWatchRewardedAd24h: () -> Unit,
    onRestorePurchases: () -> Unit,
    onRedeemLicenseCode: (String) -> Boolean = { false }
) {
    val tokens = LocalBrandTokens.current
    val lang = currentUiLang()
    var licenseInput by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
        // 1. 當前方案與 7 天免費無廣告狀態卡
        Surface(
            color = tokens.surfaceCardColor,
            shape = RoundedCornerShape(tokens.cardCorner),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.2.dp, tokens.accentColor.copy(alpha = 0.55f), RoundedCornerShape(tokens.cardCorner))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Star, contentDescription = null, tint = tokens.accentColor, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when (lang) {
                            UiLang.ZH_TW -> "DX Pro 方案與免費／付費說明"
                            UiLang.ZH_CN -> "DX Pro 方案与免费／付费说明"
                            UiLang.EN -> "DX Pro Plans & Free vs Pro Guide"
                            UiLang.JA -> "DX Pro プラン・無料／有料機能のご案内"
                        },
                        color = tokens.primaryTextColor,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // 目前狀態橫幅
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(tokens.accentColor.copy(alpha = 0.14f))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = entitlement.activePlanLabel,
                            color = tokens.accentColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = when (lang) {
                                UiLang.ZH_TW -> if (entitlement.isInFreeTrial) {
                                    "✨ 前 7 天免費全功能體驗中（剩餘 ${entitlement.trialDaysRemaining} 天）— 免費期間 100% 不會出現任何廣告！"
                                } else {
                                    "🛡️ 廣告承諾：前 7 天免費體驗期間絕不出現廣告；永遠不放開屏廣告、插頁廣告或桌面小工具廣告。"
                                }
                                UiLang.ZH_CN -> if (entitlement.isInFreeTrial) {
                                    "✨ 前 7 天免费全功能体验中（剩余 ${entitlement.trialDaysRemaining} 天）— 免费期间 100% 不会出现任何广告！"
                                } else {
                                    "🛡️ 广告承诺：前 7 天免费体验期间绝不出现广告；永不放置开屏广告、插页广告或桌面小组件广告。"
                                }
                                UiLang.EN -> if (entitlement.isInFreeTrial) {
                                    "✨ 7-Day Full Free Trial active (${entitlement.trialDaysRemaining}d left) — ZERO ads will appear during your free trial!"
                                } else {
                                    "🛡️ Ad Promise: No ads during the 7-day free trial. Never any app-open ads, interstitial ads, or widget ads."
                                }
                                UiLang.JA -> if (entitlement.isInFreeTrial) {
                                    "✨ 7日間フル機能無料体験中（残り ${entitlement.trialDaysRemaining} 日）— 無料体験期間中は広告が一切表示されません！"
                                } else {
                                    "🛡️ 広告ポリシー：7日間の無料体験中は広告なし。起動時広告・全画面インタースティシャル広告・ウィジェット広告は一切ありません。"
                                }
                            },
                            color = tokens.primaryTextColor,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    }
                }

                // 激勵廣告 24 小時解鎖按鈕
                if (!entitlement.isProPurchased) {
                    OutlinedButton(
                        onClick = onWatchRewardedAd24h,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.PlayCircle, contentDescription = null, tint = tokens.accentColor, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when (lang) {
                                UiLang.ZH_TW -> "看 1 支短廣告 → 免費解鎖 24 小時無廣告＋全功能 Pro"
                                UiLang.ZH_CN -> "看 1 支短广告 → 免费解锁 24 小时无广告＋全功能 Pro"
                                UiLang.EN -> "Watch 1 Short Ad → Unlock 24h Ad-Free + All Pro Features"
                                UiLang.JA -> "短い動画を1回視聴 → 24時間広告なし＆全Pro機能を無料解放"
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                HorizontalDivider(color = tokens.gridLineColor)

                // Google Play 內購方案（台幣基準）
                Text(
                    text = when (lang) {
                        UiLang.ZH_TW -> "Google Play 官方內購方案（NT$ 新台幣基準）"
                        UiLang.ZH_CN -> "Google Play 官方内购方案（新台币基准）"
                        UiLang.EN -> "Google Play Official Plans (TWD Base Pricing)"
                        UiLang.JA -> "Google Play 公式プラン（台湾ドル基準）"
                    },
                    color = tokens.primaryTextColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                // 主力推薦：年訂閱 + 終身買斷
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { onPurchaseProduct(DxBillingProducts.SUB_YEARLY) },
                        colors = ButtonDefaults.buttonColors(containerColor = tokens.accentColor),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("⭐ 推薦年訂閱", fontSize = 11.sp, color = Color.White.copy(alpha = 0.9f))
                            Text(billingUi.yearlyPrice, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                    Button(
                        onClick = { onPurchaseProduct(DxBillingProducts.INAPP_LIFETIME) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("💎 一次買斷永久", fontSize = 11.sp, color = Color.White.copy(alpha = 0.9f))
                            Text(billingUi.lifetimePrice, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }

                // 彈性方案：月訂閱 + 單純去廣告包
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { onPurchaseProduct(DxBillingProducts.SUB_MONTHLY) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(billingUi.monthlyPrice, fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = { onPurchaseProduct(DxBillingProducts.INAPP_REMOVE_ADS) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(billingUi.removeAdsPrice, fontSize = 12.sp)
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = onRestorePurchases) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = when (lang) {
                                UiLang.ZH_TW -> "恢復已購買項目"
                                UiLang.ZH_CN -> "恢复已购买项目"
                                UiLang.EN -> "Restore Purchases"
                                UiLang.JA -> "購入を復元"
                            },
                            fontSize = 12.sp
                        )
                    }
                }

                HorizontalDivider(color = tokens.gridLineColor)

                // 序號輸入欄位解鎖 DX Pro 付費權限
                Text(
                    text = when (lang) {
                        UiLang.ZH_TW -> "🔑 序號解鎖 DX Pro 權限"
                        UiLang.ZH_CN -> "🔑 序列号解锁 DX Pro 权限"
                        UiLang.EN -> "🔑 Unlock DX Pro with License Code"
                        UiLang.JA -> "🔑 ライセンスコードで DX Pro を解放"
                    },
                    color = tokens.primaryTextColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = licenseInput,
                        onValueChange = { licenseInput = it.uppercase(Locale.ROOT) },
                        placeholder = {
                            Text(
                                text = when (lang) {
                                    UiLang.ZH_TW -> "輸入序號 (DXPRO-XXXX-XXXX-XXXX)"
                                    UiLang.ZH_CN -> "输入序列号 (DXPRO-XXXX-XXXX-XXXX)"
                                    UiLang.EN -> "Enter code (DXPRO-XXXX-XXXX-XXXX)"
                                    UiLang.JA -> "コード入力 (DXPRO-XXXX-XXXX-XXXX)"
                                },
                                fontSize = 12.sp
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            if (onRedeemLicenseCode(licenseInput)) {
                                licenseInput = ""
                            }
                        },
                        enabled = licenseInput.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = tokens.accentColor),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = when (lang) {
                                UiLang.ZH_TW -> "兌換"
                                UiLang.ZH_CN -> "兑换"
                                UiLang.EN -> "Redeem"
                                UiLang.JA -> "適用"
                            },
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // 2. 免費 vs 付費功能透明對照表（多裝置一律免費！）
        Surface(color = tokens.surfaceCardColor, shape = RoundedCornerShape(tokens.cardCorner)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = when (lang) {
                        UiLang.ZH_TW -> "免費版 vs DX Pro 功能對照（多裝置體驗一律免費）"
                        UiLang.ZH_CN -> "免费版 vs DX Pro 功能对照（多设备体验一律免费）"
                        UiLang.EN -> "Free vs DX Pro Comparison (Multi-Device Always Free)"
                        UiLang.JA -> "無料版 vs DX Pro 機能比較（マルチデバイス体験は完全無料）"
                    },
                    color = tokens.primaryTextColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )

                val rows = listOf(
                    Triple("多裝置全適配（手機／摺疊機雙欄／平板三欄／Chrome 插件）", "✅ 一律免費", "✅ 免費包含"),
                    Triple("年／月／週／日／時間表・多組 Gmail Google 日曆與 Tasks 同步", "✅ 永久免費", "✅ 永久免費"),
                    Triple("農曆節氣／台灣假日／動態日期圖示／基本 2 款桌面小工具", "✅ 永久免費", "✅ 永久免費"),
                    Triple("前 7 天全功能免費體驗（無廣告）＆ 看廣告解鎖 24h Pro", "✅ 免費提供", "✅ 無需看廣告"),
                    Triple("全部 5 款原生向量桌面小工具（含 7 天天氣）與 0–100% 透明度", "🎁 試用／24h解鎖", "⭐ 永久解鎖"),
                    Triple("全品牌風格主題（仿V/O/M/G/S）與自訂月曆背景底圖", "🎁 試用／24h解鎖", "⭐ 永久解鎖"),
                    Triple("多重連段追擊鬧鐘、全螢幕鬧鐘提醒、自訂幾天幾時幾分前", "🎁 試用／24h解鎖", "⭐ 永久解鎖"),
                    Triple("離線語音快記、輪班排班、番茄鐘、雙時區、LINE 智慧聯動", "🎁 試用／24h解鎖", "⭐ 永久解鎖"),
                    Triple("雙指紋加密隱私日曆、🌸 女性健康 8 大範本、雲端備份還原", "🎁 試用／24h解鎖", "⭐ 永久解鎖"),
                    Triple("廣告顯示政策（絕無開屏／插頁／小工具廣告）", "僅低干擾橫幅廣告", "🛡️ 100% 零廣告")
                )

                rows.forEachIndexed { idx, (feat, freeLabel, proLabel) ->
                    if (idx > 0) HorizontalDivider(color = tokens.gridLineColor.copy(alpha = 0.45f))
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(feat, color = tokens.primaryTextColor, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("免費：$freeLabel", color = tokens.secondaryTextColor, fontSize = 11.sp, modifier = Modifier.weight(1f))
                            Text("Pro：$proLabel", color = tokens.accentColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // 3. 關於我 / About（支援繁中／簡中／英文／日文四語系）
        Surface(color = tokens.surfaceCardColor, shape = RoundedCornerShape(tokens.cardCorner)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Verified, contentDescription = null, tint = tokens.accentColor, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when (lang) {
                            UiLang.ZH_TW -> "關於我 · DX日曆 (v$currentVersion)"
                            UiLang.ZH_CN -> "关于我 · DX日历 (v$currentVersion)"
                            UiLang.EN -> "About · DX-Calendar (v$currentVersion)"
                            UiLang.JA -> "アプリについて · DXカレンダー (v$currentVersion)"
                        },
                        color = tokens.primaryTextColor,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = when (lang) {
                        UiLang.ZH_TW -> "DX日曆由 DX Ecosystem 獨立開發，致力打造一款兼顧極致視覺美感、摺疊機／平板多裝置無縫體驗、在地農曆節氣、原生向量高清晰桌面小工具與強大雙向雲端同步的跨裝置日曆。您的隱私資料 100% 保留於本機與您自己的 Google 帳號，不經由任何第三方伺服器中轉。"
                        UiLang.ZH_CN -> "DX日历由 DX Ecosystem 独立开发，致力于打造一款兼顾极致视觉美感、折叠屏／平板多设备无缝体验、本地农历节气、原生矢量超清桌面小组件与强大双向云端同步的跨设备日历。您的隐私数据 100% 保存在本地与您自己的 Google 账号中，不经过任何第三方服务器中转。"
                        UiLang.EN -> "DX-Calendar is independently crafted by DX Ecosystem to deliver flagship aesthetics, seamless multi-device layouts across phones, foldables, tablets & Chrome, regional lunar calendars, crisp native vector widgets, and direct two-way Google Calendar & Tasks sync. Your data stays 100% on your device and your own Google account."
                        UiLang.JA -> "DXカレンダーは DX Ecosystem により独立開発されたマルチデバイス対応カレンダーアプリです。洗練されたデザイン、折りたたみ端末・タブレット・Chrome 拡張機能の快適な連携、高精細ネイティブウィジェット、Google カレンダー＆Tasks 双方向同期を実現しています。データはすべて端末とご自身の Google アカウントにのみ安全に保存されます。"
                    },
                    color = tokens.secondaryTextColor,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
                HorizontalDivider(color = tokens.gridLineColor.copy(alpha = 0.5f))
                val ctx = LocalContext.current
                Surface(
                    color = tokens.elevatedSurfaceColor,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = when (lang) {
                                UiLang.ZH_TW -> "💻 同步推出：DX日曆 Chrome / Edge 桌面擴充功能（免費跨裝置）"
                                UiLang.ZH_CN -> "💻 同步推出：DX日历 Chrome / Edge 桌面扩展插件（免费跨设备）"
                                UiLang.EN -> "💻 Companion: DX-Calendar Chrome / Edge Extension (Free Multi-Device)"
                                UiLang.JA -> "💻 連携ツール：DXカレンダー Chrome / Edge 拡張機能（マルチデバイス無料）"
                            },
                            color = tokens.primaryTextColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = when (lang) {
                                UiLang.ZH_TW -> "在 Windows / macOS / Linux / Chromebook 電腦上也享有與 Android App 同步的完整功能（雙欄桌面視窗、側邊欄、農曆、輪班排班、番茄鐘、女性健康、離線自然語言與 .dxbackup.json 無損互通）。"
                                UiLang.ZH_CN -> "在 Windows / macOS / Linux / Chromebook 电脑上同样享有与 Android App 同步的完整功能（双栏桌面窗口、侧边栏、农历、排班、番茄钟、女性健康、离线自然语言与 .dxbackup.json 无损互通）。"
                                UiLang.EN -> "Enjoy full DX-Calendar parity on Windows / macOS / Linux / Chromebook (dual-pane window, Side Panel, lunar calendar, shift stamps, Pomodoro, women's health, offline NLP & lossless .dxbackup.json sync with this Android app)."
                                UiLang.JA -> "Windows / macOS / Linux / Chromebook でも Android アプリと同等の全機能（2画面デスクトップ表示、サイドパネル、旧暦、シフト登録、ポモドーロ、ウィメンズヘルス、.dxbackup.json 完全互換）を無料で利用できます。"
                            },
                            color = tokens.secondaryTextColor,
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp
                        )
                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    ctx.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/dx9901mk5/DX-Calendar/releases/latest"))
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = when (lang) {
                                    UiLang.ZH_TW -> "前往取得 Chrome / Edge 擴充功能"
                                    UiLang.ZH_CN -> "前往获取 Chrome / Edge 扩展插件"
                                    UiLang.EN -> "Get Chrome / Edge Extension"
                                    UiLang.JA -> "Chrome / Edge 拡張機能を入手"
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}
