package com.geggioman.uggiu.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import com.geggioman.uggiu.MainActivity
import com.geggioman.uggiu.R
import com.geggioman.uggiu.ble.BLEHeartRateClient
import com.geggioman.uggiu.data.CrisisRecord
import com.geggioman.uggiu.data.SessionDatabase
import com.geggioman.uggiu.data.WorkoutSession
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

data class CrisisEntry(
    val id: Int,
    val startTime: Instant,
    val endTime: Instant? = null,
    val maxBpm: Int = 0,
    val lastUpdate: Long = 0
) {
    val durationSeconds: Long
        get() = Duration.between(startTime, endTime ?: Instant.now()).seconds
}

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

    private val _rssi = MutableStateFlow(0)
    val rssi: StateFlow<Int> = _rssi.asStateFlow()

    private val _deviceAddress = MutableStateFlow("")
    val deviceAddress: StateFlow<String> = _deviceAddress.asStateFlow()

    private val _isSessionActive = MutableStateFlow(false)
    val isSessionActive: StateFlow<Boolean> = _isSessionActive.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _isBluetoothEnabled = MutableStateFlow(false)
    val isBluetoothEnabled: StateFlow<Boolean> = _isBluetoothEnabled.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<BluetoothDevice>> = _discoveredDevices.asStateFlow()

    private val _crises = MutableStateFlow<List<CrisisEntry>>(emptyList())
    val crises: StateFlow<List<CrisisEntry>> = _crises.asStateFlow()

    private var recordingJob: Job? = null
    val heartRatePoints = mutableListOf<Int>()
    private var sessionStartTime: Instant? = null
    private var currentSessionId: Int? = null

    // Alarm Settings (will be updated from Activity)
    var alarmBpmThreshold = 120
    var alarmDurationSeconds = 10
    var isAlarmSoundEnabled = true

    private var highHrStartTime: Instant? = null
    private var lastAlarmTime: Instant? = null
    private var activeCrisisDbId: Int? = null
    private var autoSaveJob: Job? = null

    companion object {
        // private const val TAG = "WorkoutService"
        private const val CHANNEL_ID = "workout_channel"
        private const val NOTIFICATION_ID = 1
        const val ACTION_STOP_SERVICE = "com.geggioman.uggiu.action.STOP_SERVICE"
        const val ACTION_CLOSE_APP = "com.geggioman.uggiu.action.CLOSE_APP"
    }

    inner class WorkoutBinder : Binder() {
        fun getService(): WorkoutService = this@WorkoutService
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                _isBluetoothEnabled.value = (state == BluetoothAdapter.STATE_ON)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        db = SessionDatabase.getDatabase(this)
        createNotificationChannel()

        val adapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
        _isBluetoothEnabled.value = adapter?.isEnabled == true
        registerReceiver(bluetoothReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
        
        bleClient = BLEHeartRateClient(
            context = this,
            onHeartRateUpdated = { bpm ->
                _currentBpm.value = bpm
                if (_isSessionActive.value) {
                    if (bpm > 0) checkAlarm(bpm)
                    updateNotification(bpm)
                }
            },
            onBatteryLevelUpdated = { level ->
                _batteryLevel.value = level
                updateNotification(_currentBpm.value)
            },
            onRssiUpdated = { valRssi ->
                _rssi.value = valRssi
            },
            onDeviceConnected = { _, address ->
                _deviceAddress.value = address
            },
            onDeviceFound = { device ->
                val currentList = _discoveredDevices.value
                if (currentList.none { it.address == device.address }) {
                    _discoveredDevices.value = currentList + device
                }
            },
            onStatusUpdated = { status ->
                _bleStatus.value = status
                _isConnected.value = (status == getString(R.string.ble_status_connected))
                _isScanning.value = bleClient.isScanning
                updateNotification(_currentBpm.value)
            },
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVICE) {
            stopWorkout()
            return START_NOT_STICKY
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, createNotification(0), ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
        } else {
            startForeground(NOTIFICATION_ID, createNotification(0))
        }
        return START_STICKY
    }

    fun startScan() {
        _discoveredDevices.value = emptyList()
        bleClient.startScan()
        _isScanning.value = bleClient.isScanning
    }

    fun stopScan() {
        bleClient.stopScan()
        _isScanning.value = bleClient.isScanning
    }

    fun connectToDevice(device: BluetoothDevice) {
        bleClient.stopScan()
        _isScanning.value = bleClient.isScanning
        bleClient.connectToDevice(device)
    }

    fun startWorkout() {
        _isSessionActive.value = true
        bleClient.setAutoReconnect(true)
        sessionStartTime = Instant.now()
        heartRatePoints.clear()
        _crises.value = emptyList()
        currentSessionId = null
        highHrStartTime = null
        lastAlarmTime = null
        startAutoSaveJob()
        startRecordingJob()
        updateNotification(_currentBpm.value)
    }

    private fun startRecordingJob() {
        recordingJob?.cancel()
        recordingJob = serviceScope.launch {
            var rssiTick = 0
            while (_isSessionActive.value) {
                val currentBpmVal = _currentBpm.value
                // Collect one point per second
                heartRatePoints.add(currentBpmVal)
                
                // Crisis logic: update UI flow if a crisis is active to refresh timer
                if (highHrStartTime != null) {
                    val currentList = _crises.value.toMutableList()
                    if (currentList.isNotEmpty() && currentList[0].endTime == null) {
                        // Copy the entry with a new timestamp to force Compose recomposition
                        currentList[0] = currentList[0].copy(lastUpdate = System.currentTimeMillis())
                        _crises.value = currentList
                    }
                }

                // Poll RSSI every 5 seconds
                if (rssiTick % 5 == 0) {
                    bleClient.readRssi()
                }
                rssiTick++

                delay(1000)
            }
        }
    }

    fun stopWorkout() {
        _isSessionActive.value = false
        
        // Immediate notification removal
        stopForeground(STOP_FOREGROUND_REMOVE)
        val manager = getSystemService(NotificationManager::class.java)
        manager.cancel(NOTIFICATION_ID)

        // Broadcast to close the Activity
        sendBroadcast(Intent(ACTION_CLOSE_APP).setPackage(packageName))
        
        autoSaveJob?.cancel()
        recordingJob?.cancel()
        
        serviceScope.launch {
            // ... cleanup ...
            if (highHrStartTime != null) {
                finalizeActiveCrisis()
            }
            if (heartRatePoints.isNotEmpty()) {
                autoSaveSession()
            }
            bleClient.setAutoReconnect(false)
            bleClient.disconnect()
            bleClient.close()
            _currentBpm.value = 0
            currentSessionId = null
            stopSelf()
        }
    }

    private suspend fun finalizeActiveCrisis() {
        val currentList = _crises.value.toMutableList()
        if (currentList.isNotEmpty() && currentList[0].endTime == null) {
            val endTime = Instant.now()
            val finishedCrisis = currentList[0].copy(endTime = endTime)
            currentList[0] = finishedCrisis
            _crises.value = currentList
            
            // Update DB
            val start = finishedCrisis.startTime.toEpochMilli()
            val end = finishedCrisis.endTime?.toEpochMilli() ?: System.currentTimeMillis()
            val record = CrisisRecord(
                id = activeCrisisDbId ?: 0,
                startTime = start,
                endTime = end,
                maxBpm = finishedCrisis.maxBpm,
                averageBpm = 0,
                sessionId = currentSessionId
            )
            db.sessionDao().updateCrisis(record)
        }
        highHrStartTime = null
        lastAlarmTime = null
        activeCrisisDbId = null
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

    private fun checkAlarm(bpm: Int) {
        if (bpm > alarmBpmThreshold) {
            if (highHrStartTime == null) {
                highHrStartTime = Instant.now()
            } else {
                val duration = Duration.between(highHrStartTime, Instant.now()).seconds
                if (duration >= alarmDurationSeconds) {
                    // Official Crisis Condition Met
                    if (activeCrisisDbId == null) {
                        val start = highHrStartTime!!
                        // Start a new crisis entry in UI
                        val newCrisis = CrisisEntry(id = _crises.value.size, startTime = start, maxBpm = bpm)
                        _crises.value = listOf(newCrisis) + _crises.value
                        
                        // Save to DB immediately as active
                        serviceScope.launch {
                            val record = CrisisRecord(
                                startTime = start.toEpochMilli(),
                                endTime = null,
                                maxBpm = bpm,
                                averageBpm = 0,
                                sessionId = currentSessionId
                            )
                            val id = db.sessionDao().insertCrisis(record)
                            activeCrisisDbId = id.toInt()
                        }
                    } else {
                        // Crisis already active, update max BPM
                        val currentList = _crises.value.toMutableList()
                        if (currentList.isNotEmpty() && currentList[0].endTime == null) {
                            if (bpm > currentList[0].maxBpm) {
                                val updatedCrisis = currentList[0].copy(maxBpm = bpm)
                                currentList[0] = updatedCrisis
                                _crises.value = currentList
                                
                                serviceScope.launch {
                                    val record = CrisisRecord(
                                        id = activeCrisisDbId ?: 0,
                                        startTime = updatedCrisis.startTime.toEpochMilli(),
                                        endTime = null,
                                        maxBpm = updatedCrisis.maxBpm,
                                        averageBpm = 0,
                                        sessionId = currentSessionId
                                    )
                                    db.sessionDao().updateCrisis(record)
                                }
                            }
                        }
                    }

                    // Trigger actual alarm feedback
                    if (lastAlarmTime == null || Duration.between(lastAlarmTime, Instant.now()).seconds >= 5) {
                        triggerAlarm(bpm)
                        lastAlarmTime = Instant.now()
                    }
                }
            }
        } else {
            if (activeCrisisDbId != null) {
                serviceScope.launch {
                    finalizeActiveCrisis()
                }
            } else {
                // Just reset the high HR timer if no official crisis was started
                highHrStartTime = null
                lastAlarmTime = null
            }
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
            Toast.makeText(this@WorkoutService, getString(R.string.alarm_toast, bpm), Toast.LENGTH_SHORT).show()
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(bpm: Int): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val stopIntent = Intent(this, WorkoutService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val isActive = _isSessionActive.value
        val battery = _batteryLevel.value
        val title = if (isActive) getString(R.string.notif_title_active) else getString(R.string.notif_title_ready)
        
        val batteryText = if (battery >= 0) getString(R.string.notif_battery, battery) else ""
        val content = when {
            isActive && bpm > 0 -> getString(R.string.notif_content_bpm, bpm, batteryText)
            isActive -> getString(R.string.notif_content_waiting, batteryText)
            else -> getString(R.string.notif_content_ready)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.btn_exit),
                stopPendingIntent
            )
            .build()
    }

    private fun updateNotification(bpm: Int) {
        if (!_isSessionActive.value && bpm == 0) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, createNotification(bpm))
    }

    override fun onDestroy() {
        super.onDestroy()
        val manager = getSystemService(NotificationManager::class.java)
        manager.cancel(NOTIFICATION_ID)
        unregisterReceiver(bluetoothReceiver)
        bleClient.disconnect()
        bleClient.close()
        serviceScope.launch {
            if (_isSessionActive.value) {
                autoSaveSession()
            }
        }
    }
}
