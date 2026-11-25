package com.dx.calendar.alarm

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * 全螢幕多重連段鬧鐘呼叫畫面（支援鎖屏點亮、鬧鐘音軌循環響鈴與一鍵中止後續連段追擊）
 */
class AlarmRingingActivity : ComponentActivity() {

    companion object {
        private var activeRingtone: Ringtone? = null
        private var activeVibrator: Vibrator? = null

        fun stopActiveRinging() {
            runCatching { activeRingtone?.stop() }
            activeRingtone = null
            runCatching { activeVibrator?.cancel() }
            activeVibrator = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val sourceType = intent.getStringExtra(MultiCallAlarmScheduler.EXTRA_SOURCE_TYPE) ?: "EVENT"
        val itemId = intent.getLongExtra(MultiCallAlarmScheduler.EXTRA_ITEM_ID, 0L)
        val title = intent.getStringExtra(MultiCallAlarmScheduler.EXTRA_TITLE) ?: "DX日曆強提醒"
        val subtitle = intent.getStringExtra(MultiCallAlarmScheduler.EXTRA_SUBTITLE) ?: ""
        val burstIndex = intent.getIntExtra(MultiCallAlarmScheduler.EXTRA_BURST_INDEX, 1)
        val burstTotal = intent.getIntExtra(MultiCallAlarmScheduler.EXTRA_BURST_TOTAL, 1)
        val offsetMin = intent.getIntExtra(MultiCallAlarmScheduler.EXTRA_OFFSET_MINUTES, 0)
        val notifId = intent.getIntExtra("notif_id", 0)

        startAlarmAudioAndVibration()

        val nowClock = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))

        setContent {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF1A0907), Color(0xFF0A0A0D), Color(0xFF000000))
                        )
                    )
                    .padding(28.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(92.dp)
                            .background(Color(0xFFFF5238).copy(alpha = 0.22f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Alarm,
                            contentDescription = "鬧鐘響鈴",
                            tint = Color(0xFFFF5238),
                            modifier = Modifier.size(52.dp)
                        )
                    }

                    Text(
                        text = nowClock,
                        color = Color.White,
                        fontSize = 56.sp,
                        fontWeight = FontWeight.Bold
                    )

                    if (burstTotal > 1) {
                        Text(
                            text = "多重連段追擊呼叫 · 第 $burstIndex / $burstTotal 段",
                            color = Color(0xFFFF8A65),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            color = Color(0xFFB0B0B8),
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = {
                                stopActiveRinging()
                                cancelNotification(notifId)
                                MultiCallAlarmScheduler.scheduleNextBurstCall(
                                    context = this@AlarmRingingActivity,
                                    sourceType = sourceType,
                                    itemId = itemId,
                                    title = title,
                                    subtitle = "貪睡延後 5 分鐘提醒",
                                    isPrivacy = false,
                                    useAlarmStream = true,
                                    nextBurstIndex = 1,
                                    burstTotal = 1,
                                    burstIntervalMin = 5,
                                    offsetMinutes = offsetMin
                                )
                                finish()
                            },
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.weight(1f).height(56.dp)
                        ) {
                            Icon(Icons.Filled.Snooze, contentDescription = null, tint = Color.White)
                            Text(" 貪睡 5 分鐘", color = Color.White, fontSize = 15.sp)
                        }

                        Button(
                            onClick = {
                                stopActiveRinging()
                                cancelNotification(notifId)
                                MultiCallAlarmScheduler.cancelRemainingBurstCalls(
                                    context = this@AlarmRingingActivity,
                                    sourceType = sourceType,
                                    itemId = itemId,
                                    offsetMinutes = offsetMin,
                                    burstTotal = burstTotal
                                )
                                finish()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5238)),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.weight(1f).height(56.dp)
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White)
                            Text(" 停止連段呼叫", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    private fun startAlarmAudioAndVibration() {
        stopActiveRinging()
        runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            val ringtone = RingtoneManager.getRingtone(applicationContext, uri)
            ringtone.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ringtone.isLooping = true
            }
            ringtone.play()
            activeRingtone = ringtone
        }

        runCatching {
            val vib = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            vib.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 400, 600, 400), 0))
            activeVibrator = vib
        }
    }

    private fun cancelNotification(notifId: Int) {
        if (notifId != 0) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(notifId)
        }
    }

    override fun onDestroy() {
        stopActiveRinging()
        super.onDestroy()
    }
}
