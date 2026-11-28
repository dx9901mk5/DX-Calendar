package com.dx.calendar.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dx.calendar.data.sync.CustomOAuthUiState
import com.dx.calendar.data.sync.GoogleTasksCustomOAuthManager
import com.dx.calendar.ui.theme.LocalBrandTokens

/**
 * Google Tasks 自訂 OAuth（BYO 憑證）彈出教學說明與憑證輸入視窗：
 * - 非開發者本人帳號進入 Tasks 或點擊同步時彈出，提供免費 4 步驟 Google Cloud Desktop OAuth 申請教學
 * - 支援直接貼上 Client ID / Client Secret，亦支援直接貼上 Google 下載的 client_secret_*.json 全文自動拆解
 */
@Composable
fun GoogleTasksByoDialog(
    oauthState: CustomOAuthUiState,
    initialClientSecret: String,
    onDismiss: () -> Unit,
    onStartAuthorize: (clientId: String, clientSecret: String) -> Unit,
    onCancelAuthorize: () -> Unit,
    onClearCredentials: () -> Unit
) {
    val tokens = LocalBrandTokens.current
    val context = LocalContext.current
    val googleBlue = Color(0xFF1A73E8)

    var clientIdInput by remember(oauthState.clientId) { mutableStateOf(oauthState.clientId) }
    var clientSecretInput by remember(initialClientSecret) { mutableStateOf(initialClientSecret) }
    var showSecret by remember { mutableStateOf(false) }
    var jsonAutoParsedHint by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = {
            if (oauthState.isAuthorizing) onCancelAuthorize()
            onDismiss()
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Key,
                    contentDescription = null,
                    tint = googleBlue,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Google Tasks 同步教學與憑證設定",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // 說明為何需自填（隱私保護＋100% 免費）
                Surface(
                    color = googleBlue.copy(alpha = 0.10f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "為保護個人資料並避免共用額度受限，Google Tasks 雲端同步採「自帶免費 Google Cloud 憑證」模式：您的資料僅直連自己的 Google 專案，憑證以 Android Keystore 硬體加密保存於本機（若只需快速檢視，也可直接按「開啟 Google Tasks」使用官方 App）。",
                        color = tokens.primaryTextColor,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                if (oauthState.isAuthorized) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1E8E3E).copy(alpha = 0.14f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF1E8E3E),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "已授權同步：${oauthState.authorizedEmail.ifBlank { "自訂 OAuth 憑證" }}",
                            color = tokens.primaryTextColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // 4 步驟免費申請教學卡片
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(tokens.elevatedSurfaceColor.copy(alpha = 0.65f), RoundedCornerShape(12.dp))
                        .border(1.dp, tokens.gridLineColor, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "📖 4 步驟免費申請 Google Tasks OAuth 憑證",
                        color = tokens.accentColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    TutorialStepRow(
                        step = "1",
                        text = "開啟 Google Cloud Console 建立免費新專案（例如命名 My-Tasks），前往「API 和服務 → 程式庫」搜尋並啟用「Google Tasks API」。"
                    )
                    TutorialStepRow(
                        step = "2",
                        text = "前往「Google Auth Platform（OAuth 同意畫面）」→ 對象選「外部 (External)」→ 於「測試使用者」加入您自己的 Gmail（建議點「發布應用程式」維持未送審正式狀態，可避免測試模式每 7 天過期）。"
                    )
                    TutorialStepRow(
                        step = "3",
                        text = "於「資料存取 (Data Access)」加入權限範圍：https://www.googleapis.com/auth/tasks。"
                    )
                    TutorialStepRow(
                        step = "4",
                        text = "前往「用戶端 (憑證) → 建立用戶端」→ 應用程式類型務必選「電腦版應用程式 (Desktop app)」（⚠ 請勿選 Android 類型），建立後複製「用戶端 ID」與「用戶端密鑰」（或直接貼上整個下載的 JSON 檔內容）即可！"
                    )

                    OutlinedButton(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse(GoogleTasksCustomOAuthManager.GOOGLE_CLOUD_TASKS_API_URL)
                                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.OpenInBrowser,
                            contentDescription = null,
                            tint = googleBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "前往 Google Cloud Console（啟用 Tasks API）",
                            color = googleBlue,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                HorizontalDivider(color = tokens.gridLineColor.copy(alpha = 0.6f))

                Text(
                    text = "貼入您申請的 Desktop OAuth 憑證（支援直接貼入整份 client_secret JSON）",
                    color = tokens.primaryTextColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )

                OutlinedTextField(
                    value = clientIdInput,
                    onValueChange = { raw ->
                        val parsed = GoogleTasksCustomOAuthManager.parseGoogleClientJson(raw)
                        if (parsed != null) {
                            clientIdInput = parsed.first
                            if (parsed.second.isNotBlank()) {
                                clientSecretInput = parsed.second
                            }
                            jsonAutoParsedHint = true
                        } else {
                            clientIdInput = raw
                            jsonAutoParsedHint = false
                        }
                    },
                    label = { Text("用戶端 ID (Client ID)", fontSize = 12.sp) },
                    placeholder = { Text("xxxx.apps.googleusercontent.com 或貼上 JSON", fontSize = 12.sp) },
                    singleLine = true,
                    enabled = !oauthState.isAuthorizing,
                    modifier = Modifier.fillMaxWidth()
                )

                if (jsonAutoParsedHint) {
                    Text(
                        text = "✓ 已自動從 JSON 解析出 Client ID 與 Client Secret！",
                        color = Color(0xFF1E8E3E),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                OutlinedTextField(
                    value = clientSecretInput,
                    onValueChange = { clientSecretInput = it },
                    label = { Text("用戶端密鑰 (Client Secret，Desktop 憑證必填)", fontSize = 12.sp) },
                    placeholder = { Text("GOCSPX-...", fontSize = 12.sp) },
                    singleLine = true,
                    enabled = !oauthState.isAuthorizing,
                    visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showSecret = !showSecret }) {
                            Icon(
                                imageVector = if (showSecret) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = "切換顯示密鑰",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                if (oauthState.isAuthorizing) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(googleBlue.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        CircularProgressIndicator(
                            color = googleBlue,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "等待瀏覽器完成 Google 授權中…\n登入並點「允許」後將自動返回 DX日曆並同步 Tasks。",
                            color = tokens.primaryTextColor,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (oauthState.lastError.isNotBlank()) {
                    Text(
                        text = "⚠️ ${oauthState.lastError}",
                        color = Color(0xFFD93025),
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }

                if (oauthState.clientId.isNotBlank() && !oauthState.isAuthorizing) {
                    TextButton(
                        onClick = {
                            onClearCredentials()
                            clientIdInput = ""
                            clientSecretInput = ""
                            jsonAutoParsedHint = false
                        },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = null,
                            tint = Color(0xFFD93025),
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("清除已儲存的自訂憑證與授權", color = Color(0xFFD93025), fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            if (oauthState.isAuthorizing) {
                TextButton(onClick = onCancelAuthorize) {
                    Text("取消等待授權", color = Color(0xFFD93025))
                }
            } else {
                Button(
                    onClick = { onStartAuthorize(clientIdInput.trim(), clientSecretInput.trim()) },
                    enabled = clientIdInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = googleBlue)
                ) {
                    Text("儲存並前往授權同步", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    if (oauthState.isAuthorizing) onCancelAuthorize()
                    onDismiss()
                }
            ) {
                Text("稍後再說")
            }
        }
    )
}

@Composable
private fun TutorialStepRow(step: String, text: String) {
    val tokens = LocalBrandTokens.current
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "$step.",
            color = tokens.accentColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            text = text,
            color = tokens.primaryTextColor,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            modifier = Modifier.weight(1f)
        )
    }
}
