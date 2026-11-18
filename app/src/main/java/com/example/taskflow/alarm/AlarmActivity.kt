package com.example.taskflow.alarm

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.taskflow.R
import com.example.taskflow.data.repositories.SettingsRepository
import com.example.taskflow.scheduling.AlarmContract
import com.example.taskflow.scheduling.AlarmScheduler
import com.example.taskflow.ui.theme.TaskFlowTheme
import com.example.taskflow.ui.theme.ThemeViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class AlarmActivity : ComponentActivity() {

    companion object {
        const val EXTRA_EVENT_ID = "alarm_event_id"
        const val EXTRA_OCC_DATE = "alarm_occ_date"
        const val EXTRA_TITLE = "alarm_title"
        const val EXTRA_TIME = "alarm_time"
        const val EXTRA_NOTIFICATION_ID = "alarm_notification_id"
    }

    @Inject lateinit var alarmScheduler: AlarmScheduler
    @Inject lateinit var settingsRepo: SettingsRepository

    private val themeViewModel: ThemeViewModel by viewModels()
    private var player: MediaPlayer? = null
    private var eventId = -1L
    private var occDate = -1L
    private var notificationId = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        eventId = intent.getLongExtra(EXTRA_EVENT_ID, -1L)
        occDate = intent.getLongExtra(EXTRA_OCC_DATE, -1L)
        notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: ""
        val time = intent.getStringExtra(EXTRA_TIME) ?: ""

        startSound()
        startVibration()

        setContent {
            val themeMode by themeViewModel.themeMode.collectAsState()
            val dynamic by themeViewModel.dynamicColor.collectAsState()
            val accent by themeViewModel.accent.collectAsState()
            val snoozeMinutes by settingsRepo.alarmSnoozeFlow.collectAsState(initial = 10)
            TaskFlowTheme(themeMode = themeMode, dynamicColor = dynamic, accentArgb = accent) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Alarm,
                            contentDescription = null,
                            modifier = Modifier.size(96.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            title,
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(time, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(48.dp))
                        Button(onClick = { snooze(snoozeMinutes) }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.s3_alarm_snooze, snoozeMinutes))
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(onClick = { closeAlarm() }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.s3_alarm_dismiss))
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }

    override fun onDestroy() {
        stopAlarm()
        super.onDestroy()
    }

    private fun startSound() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val mp = MediaPlayer()
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            mp.setDataSource(this, uri)
            mp.isLooping = true
            mp.prepare()
            mp.start()
            player = mp
        } catch (e: Exception) {
            player = null
        }
    }

    @Suppress("DEPRECATION")
    private fun startVibration() {
        try {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 600), 0))
        } catch (e: Exception) {
            // sin vibracion
        }
    }

    @Suppress("DEPRECATION")
    private fun stopAlarm() {
        try {
            player?.stop()
            player?.release()
        } catch (e: Exception) {
            // ya detenido
        }
        player = null
        try {
            (getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)?.cancel()
        } catch (e: Exception) {
            // sin vibracion
        }
    }

    private fun closeAlarm() {
        stopAlarm()
        if (notificationId != -1) {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(notificationId)
        }
        finish()
    }

    private fun snooze(minutes: Int) {
        if (eventId != -1L && occDate != -1L) {
            val extras = Bundle().apply {
                putLong(AlarmContract.EXTRA_EVENT_ID, eventId)
                putLong(AlarmContract.EXTRA_OCCURRENCE_DATE, occDate)
            }
            alarmScheduler.schedule(
                "alarm_snooze:" + eventId + ":" + occDate,
                AlarmContract.ACTION_EVENT_REMINDER,
                extras,
                System.currentTimeMillis() + minutes * 60_000L
            )
        }
        closeAlarm()
    }
}
