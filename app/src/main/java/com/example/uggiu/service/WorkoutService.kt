package com.example.uggiu.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.uggiu.MainActivity
import com.example.uggiu.ble.BLEHeartRateClient
import com.example.uggiu.data.SessionDatabase
import com.example.uggiu.data.WorkoutSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

class WorkoutService : Service() {

    private val binder = WorkoutBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var db: SessionDatabase
    private lateinit var bleClient: BLEHeartRateClient

    // Observables for UI
    private val _isConnected = MutableStateFlow(value = false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _bleStatus = MutableStateFlow("Scollegato")
    val bleStatus: StateFlow<String> = _bleStatus.asStateFlow()

    private val _currentBpm = MutableStateFlow(0)
    val currentBpm: StateFlow<Int> = _currentBpm.asStateFlow()

    private val _batteryLevel = MutableStateFlow(-1)
    val batteryLevel: StateFlow<Int> = _batteryLevel.asStateFlow()

    private val _isSessionActive = MutableStateFlow(false)
    val isSessionActive: StateFlow<Boolean> = _isSessionActive.asStateFlow()

    val heartRatePoints = mutableListOf<Int>()
    private var sessionStartTime: Instant? = null
    private var currentSessionId: Int? = null

    // Alarm Settings (will be updated from Activity)
    var alarmBpmThreshold = 120
    var alarmDurationSeconds = 0
    var isAlarmSoundEnabled = true

    private var highHrStartTime: Instant? = null
    private var lastAlarmTime: Instant? = null
    private var autoSaveJob: Job? = null

    companion object {
        private const val TAG = "WorkoutService"
        private const val CHANNEL_ID = "workout_channel"
        private const val NOTIFICATION_ID = 1
    }

    inner class WorkoutBinder : Binder() {
        fun getService(): WorkoutService = this@WorkoutService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        db = SessionDatabase.getDatabase(this)
        createNotificationChannel()
        
        bleClient = BLEHeartRateClient(
            context = this,
            onHeartRateUpdated = { bpm ->
                _currentBpm.value = bpm
                if (_isSessionActive.value) {
                    heartRatePoints.add(bpm)
                    checkAlarm(bpm)
                    updateNotification(bpm)
                }
            },
            onBatteryLevelUpdated = { level ->
                _batteryLevel.value = level
                updateNotification(_currentBpm.value)
            },
            onStatusUpdated = { status ->
                _bleStatus.value = status
                _isConnected.value = (status == "Connesso") || status.contains("Cardio Connesso") || status.contains("Ricezione dati")
                updateNotification(_currentBpm.value)
            },
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, createNotification(0), ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
        } else {
            startForeground(NOTIFICATION_ID, createNotification(0))
        }
        return START_STICKY
    }

    fun startScan() {
        bleClient.startScan()
    }

    fun startWorkout() {
        _isSessionActive.value = true
        bleClient.setAutoReconnect(true)
        sessionStartTime = Instant.now()
        heartRatePoints.clear()
        currentSessionId = null
        highHrStartTime = null
        lastAlarmTime = null
        startAutoSaveJob()
        updateNotification(_currentBpm.value)
    }

    fun stopWorkout() {
        autoSaveJob?.cancel()
        bleClient.setAutoReconnect(false)
        if (heartRatePoints.isNotEmpty()) {
            saveWorkoutSessionToDb()
        }
        _isSessionActive.value = false
        updateNotification(0)
        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    private fun startAutoSaveJob() {
        autoSaveJob?.cancel()
        autoSaveJob = serviceScope.launch {
            while (_isSessionActive.value) {
                delay(30000)
                if (_isSessionActive.value && heartRatePoints.isNotEmpty()) {
                    autoSaveSession()
                }
            }
        }
    }

    private suspend fun autoSaveSession() {
        val endTime = Instant.now()
        val start = sessionStartTime?.toEpochMilli() ?: System.currentTimeMillis()
        val end = endTime.toEpochMilli()
        val avgHr = heartRatePoints.average().toInt()
        val maxHr = heartRatePoints.maxOrNull() ?: 0
        val hrHistoryString = heartRatePoints.joinToString(",")

        val session = WorkoutSession(
            id = currentSessionId ?: 0,
            startTime = start,
            endTime = end,
            averageHeartRate = avgHr,
            maxHeartRate = maxHr,
            heartRateHistoryString = hrHistoryString
        )

        if (currentSessionId == null) {
            val id = db.sessionDao().insertSession(session)
            currentSessionId = id.toInt()
        } else {
            db.sessionDao().updateSession(session)
        }
    }

    private fun saveWorkoutSessionToDb() {
        serviceScope.launch {
            autoSaveSession()
            _currentBpm.value = 0
            currentSessionId = null
            _isConnected.value = false
            bleClient.disconnect()
            _bleStatus.value = "Scollegato"
        }
    }

    private fun checkAlarm(bpm: Int) {
        if (bpm > alarmBpmThreshold) {
            if (highHrStartTime == null) {
                highHrStartTime = Instant.now()
            } else {
                val duration = Duration.between(highHrStartTime, Instant.now()).seconds
                if (duration >= alarmDurationSeconds) {
                    if (lastAlarmTime == null || Duration.between(lastAlarmTime, Instant.now()).seconds >= 5) {
                        triggerAlarm(bpm)
                        lastAlarmTime = Instant.now()
                    }
                }
            }
        } else {
            highHrStartTime = null
            lastAlarmTime = null
        }
    }

    private fun triggerAlarm(bpm: Int) {
        if (isAlarmSoundEnabled) {
            val toneGen = ToneGenerator(AudioManager.STREAM_ALARM, 100)
            toneGen.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 1000)
        } else {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as Vibrator
            }
            vibrator.vibrate(VibrationEffect.createOneShot(1000, VibrationEffect.DEFAULT_AMPLITUDE))
        }
        serviceScope.launch(Dispatchers.Main) {
            Toast.makeText(this@WorkoutService, "ATTENZIONE! Battito elevato: $bpm BPM", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Registrazione Allenamento",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(bpm: Int): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val isActive = _isSessionActive.value
        val battery = _batteryLevel.value
        val title = if (isActive) "uggiu - Sessione Attiva" else "uggiu - Pronto"
        
        val batteryText = if (battery >= 0) " (Batteria: $battery%)" else ""
        val content = when {
            isActive && bpm > 0 -> "Registrazione in corso: $bpm BPM$batteryText"
            isActive -> "Registrazione avviata. In attesa di dati...$batteryText"
            else -> "App in esecuzione. Premi AVVIA per iniziare."
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(com.example.uggiu.R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(bpm: Int) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, createNotification(bpm))
    }

    override fun onDestroy() {
        super.onDestroy()
        bleClient.disconnect()
        bleClient.close()
        serviceScope.launch {
            if (_isSessionActive.value) {
                autoSaveSession()
            }
        }
    }
}
