package com.example.uggiu

import android.annotation.SuppressLint
import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Paint
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import com.example.uggiu.data.SessionDatabase
import com.example.uggiu.data.WorkoutSession
import com.example.uggiu.service.WorkoutService
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private lateinit var db: SessionDatabase
    private var workoutService by mutableStateOf<WorkoutService?>(null)
    private var isBound by mutableStateOf(false)

    companion object {
        private const val TAG = "MainActivity"
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as WorkoutService.WorkoutBinder
            workoutService = binder.getService()
            isBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            workoutService = null
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = SessionDatabase.getDatabase(this)

        val intent = Intent(this, WorkoutService::class.java)
        startService(intent)
        bindService(intent, serviceConnection, BIND_AUTO_CREATE)

        setContent {
            MaterialTheme(
                colorScheme = MaterialTheme.colorScheme.copy(
                    background = Color(0xFF0A0A0C),
                    surface = Color(0xFF141418),
                    primary = Color(0xFF00E5FF),
                    secondary = Color(0xFF4CAF50),
                    error = Color(0xFFFF3D00)
                )
            ) {
                UggiuAppScreen()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }

    @Composable
    fun UggiuAppScreen() {
        val context = LocalContext.current
        var showHistoryScreen by remember { mutableStateOf(false) }
        var showHelpDialog by remember { mutableStateOf(false) }
        val sessions by db.sessionDao().getAllSessions().collectAsState(initial = emptyList())
        val crisesHistory by db.sessionDao().getAllCrises().collectAsState(initial = emptyList())

        // Service States
        val currentService = workoutService
        val isConnected by currentService?.isConnected?.collectAsState(initial = false) ?: remember { mutableStateOf(false) }
        val bleStatus by currentService?.bleStatus?.collectAsState(initial = "Scollegato") ?: remember { mutableStateOf("Scollegato") }
        val currentBpm by currentService?.currentBpm?.collectAsState(initial = 0) ?: remember { mutableIntStateOf(0) }
        val isSessionActive by currentService?.isSessionActive?.collectAsState(initial = false) ?: remember { mutableStateOf(false) }
        val batteryLevel by currentService?.batteryLevel?.collectAsState(initial = -1) ?: remember { mutableIntStateOf(-1) }
        val rssi by currentService?.rssi?.collectAsState(initial = 0) ?: remember { mutableIntStateOf(0) }
        val deviceAddress by currentService?.deviceAddress?.collectAsState(initial = "") ?: remember { mutableStateOf("") }
        val isScanning by currentService?.isScanning?.collectAsState(initial = false) ?: remember { mutableStateOf(false) }
        val discoveredDevices by currentService?.discoveredDevices?.collectAsState(initial = emptyList()) ?: remember { mutableStateOf(emptyList()) }
        val crises by currentService?.crises?.collectAsState(initial = emptyList()) ?: remember { mutableStateOf(emptyList()) }

        val requestPermissionsLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            if (permissions.values.all { it }) {
                currentService?.startScan()
            } else {
                Toast.makeText(context, context.getString(R.string.permissions_denied), Toast.LENGTH_SHORT).show()
            }
        }

        fun checkPermissions(): Boolean {
            val requiredPermissions = mutableListOf<String>()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                requiredPermissions.add(Manifest.permission.BLUETOOTH_SCAN)
                requiredPermissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                requiredPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requiredPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            requiredPermissions.add(Manifest.permission.BODY_SENSORS)

            val missing = requiredPermissions.filter {
                ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
            }

            return if (missing.isNotEmpty()) {
                requestPermissionsLauncher.launch(missing.toTypedArray())
                false
            } else {
                true
            }
        }

        // Lifecycle observer to start/stop scanning automatically
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner, currentService, isSessionActive) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    if (!isSessionActive && checkPermissions()) {
                        currentService?.startScan()
                    }
                } else if (event == Lifecycle.Event.ON_PAUSE) {
                    currentService?.stopScan()
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }

        // Stop scanning immediately when a session starts
        LaunchedEffect(isSessionActive) {
            if (isSessionActive) {
                currentService?.stopScan()
            }
        }

        // Alarm States (Synced with Service)
        val alarmBpmThreshold = remember { mutableIntStateOf(currentService?.alarmBpmThreshold ?: 120) }
        val alarmDurationSeconds = remember { mutableIntStateOf(currentService?.alarmDurationSeconds ?: 10) }
        val isAlarmSoundEnabled = remember { mutableStateOf(currentService?.isAlarmSoundEnabled ?: true) }

        // Update service settings when UI changes
        currentService?.let { service ->
            service.alarmBpmThreshold = alarmBpmThreshold.intValue
            service.alarmDurationSeconds = alarmDurationSeconds.intValue
            service.isAlarmSoundEnabled = isAlarmSoundEnabled.value
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0A0A0C))
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "uggiu", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E5FF))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!isSessionActive && !showHistoryScreen) {
                        IconButton(onClick = { if (checkPermissions()) currentService?.startScan() }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Aggiorna",
                                tint = if (isScanning) Color(0xFF00E5FF) else Color.Gray
                            )
                        }
                    }

                    IconButton(onClick = { showHelpDialog = true }) {
                        Icon(imageVector = Icons.Default.Info, contentDescription = stringResource(R.string.help_title), tint = Color.Gray)
                    }

                    if (isConnected && rssi < 0) {
                        ProximityBadge(rssi = rssi)
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    if (batteryLevel >= 0) {
                        BatteryBadge(level = batteryLevel)
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    IconButton(
                        onClick = { showHistoryScreen = !showHistoryScreen },
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (showHistoryScreen) Color(0xFF00E5FF) else Color(0xFF141418))
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.List,
                            contentDescription = stringResource(R.string.scan_found_title),
                            tint = if (showHistoryScreen) Color.Black else Color.White
                        )
                    }
                }
            }

            if (showHelpDialog) {
                HelpDialog(onDismiss = { showHelpDialog = false })
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (showHistoryScreen) {
                HistoryScreen(
                    crises = crisesHistory,
                    onDelete = { lifecycleScope.launch { db.sessionDao().deleteCrisis(it) } },
                    onDeleteMultiple = { selectedIds ->
                        lifecycleScope.launch {
                            crisesHistory.filter { it.id in selectedIds }.forEach { db.sessionDao().deleteCrisis(it) }
                        }
                    }
                )
            } else {
                DashboardScreen(
                    activeSession = isSessionActive,
                    isConnected = isConnected,
                    isScanning = isScanning,
                    discoveredDevices = discoveredDevices,
                    statusText = bleStatus,
                    deviceAddress = deviceAddress,
                    bpm = currentBpm,
                    onFinishSession = { workoutService?.stopWorkout() },
                    onSelectDevice = { device ->
                        workoutService?.connectToDevice(device)
                        workoutService?.startWorkout()
                    },
                    alarmBpmThreshold = alarmBpmThreshold,
                    alarmDurationSeconds = alarmDurationSeconds,
                    isAlarmSoundEnabled = isAlarmSoundEnabled,
                    heartRatePoints = workoutService?.heartRatePoints ?: emptyList(),
                    crises = crises.take(1) // Only show the last one of the current session
                )
            }
        }
    }

    @Composable
    fun HelpDialog(onDismiss: () -> Unit) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = Color(0xFF141418),
            title = { Text(stringResource(R.string.help_title), color = Color(0xFF00E5FF), fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HelpItem(stringResource(R.string.help_band_not_connected_title), stringResource(R.string.help_band_not_connected_desc))
                    HelpItem(stringResource(R.string.help_battery_not_visible_title), stringResource(R.string.help_battery_not_visible_desc))
                    HelpItem(stringResource(R.string.help_permissions_title), stringResource(R.string.help_permissions_desc))
                    HelpItem(stringResource(R.string.help_background_title), stringResource(R.string.help_background_desc))
                }
            },
            confirmButton = {
                Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F1F24))) {
                    Text(stringResource(R.string.help_confirm), color = Color.White)
                }
            }
        )
    }

    @Composable
    fun HelpItem(title: String, description: String) {
        Column {
            Text(text = title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(text = description, color = Color.Gray, fontSize = 12.sp, lineHeight = 16.sp)
        }
    }

    @Composable
    fun ProximityBadge(rssi: Int) {
        val label = when {
            rssi > -60 -> stringResource(R.string.rssi_immediate)
            rssi > -75 -> stringResource(R.string.rssi_near)
            rssi > -90 -> stringResource(R.string.rssi_medium)
            else -> stringResource(R.string.rssi_far)
        }
        val color = when {
            rssi > -75 -> Color(0xFF4CAF50)
            rssi > -90 -> Color(0xFFFFEB3B)
            else -> Color(0xFFFF3D00)
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF141418))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = label,
                color = color,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }

    @Composable
    fun BatteryBadge(level: Int) {
        val icon = Icons.Default.Favorite
        val color = if (level > 20) Color(0xFF4CAF50) else Color(0xFFFF3D00)

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF141418))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "$level%",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }

    @Composable
    fun DashboardScreen(
        activeSession: Boolean,
        isConnected: Boolean,
        isScanning: Boolean,
        discoveredDevices: List<android.bluetooth.BluetoothDevice>,
        statusText: String,
        deviceAddress: String,
        bpm: Int,
        onFinishSession: () -> Unit,
        onSelectDevice: (android.bluetooth.BluetoothDevice) -> Unit,
        alarmBpmThreshold: MutableState<Int>,
        alarmDurationSeconds: MutableState<Int>,
        isAlarmSoundEnabled: MutableState<Boolean>,
        heartRatePoints: List<Int>,
        crises: List<com.example.uggiu.service.CrisisEntry>
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // Scan Mode
            if (!activeSession) {
                item {
                    Column(modifier = Modifier.padding(horizontal = 8.dp)) {
                        Text(
                            text = if (isScanning) "Ricerca dispositivi..." else "Ricerca completata",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        if (isScanning) {
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = Color(0xFF00E5FF),
                                trackColor = Color.DarkGray
                            )
                        }
                    }
                }
            }

            // Device List Selection
            if (!activeSession && discoveredDevices.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.scan_found_title),
                        color = Color.Gray,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
                items(discoveredDevices) { device ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectDevice(device) },
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1F1F24)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                @SuppressLint("MissingPermission")
                                val deviceName = device.name ?: stringResource(R.string.unknown_device)
                                Text(
                                    text = deviceName,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = device.address,
                                    color = Color.LightGray,
                                    fontSize = 12.sp
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.List, // Reusing list icon for "select"
                                contentDescription = stringResource(R.string.btn_terminate),
                                tint = Color(0xFF00E5FF)
                            )
                        }
                    }
                }
            }

            // Settings
            if (!activeSession) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF141418)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(text = stringResource(R.string.settings_alarm_config), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.settings_bpm_threshold), color = Color.Gray, fontSize = 12.sp)
                                    OutlinedTextField(
                                        value = alarmBpmThreshold.value.toString(),
                                        onValueChange = { alarmBpmThreshold.value = it.toIntOrNull() ?: 0 },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White,
                                            focusedBorderColor = Color(0xFF00E5FF),
                                            unfocusedBorderColor = Color.DarkGray
                                        )
                                    )
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.settings_duration), color = Color.Gray, fontSize = 12.sp)
                                    OutlinedTextField(
                                        value = alarmDurationSeconds.value.toString(),
                                        onValueChange = { alarmDurationSeconds.value = it.toIntOrNull() ?: 0 },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White,
                                            focusedBorderColor = Color(0xFF00E5FF),
                                            unfocusedBorderColor = Color.DarkGray
                                        )
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isAlarmSoundEnabled.value) stringResource(R.string.settings_alarm_sound) else stringResource(R.string.settings_alarm_notify),
                                    color = Color.White,
                                    fontSize = 14.sp
                                )
                                Switch(
                                    checked = isAlarmSoundEnabled.value,
                                    onCheckedChange = { isAlarmSoundEnabled.value = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color(0xFF00E5FF),
                                        checkedTrackColor = Color(0xFF00E5FF).copy(alpha = 0.5f)
                                    )
                                )
                            }
                        }
                    }
                }
            }

            //Connection status and actions
            if (activeSession) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF141418)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {

                            Text(text = statusText, fontSize = 14.sp, color = Color.LightGray)
                            if (isConnected && deviceAddress.isNotEmpty()) {
                                Text(
                                    text = "ID: $deviceAddress",
                                    fontSize = 11.sp,
                                    color = Color.Gray,
                                    fontWeight = FontWeight.Normal
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))

                            val buttonText = if (isConnected) stringResource(R.string.btn_terminate) else stringResource(R.string.btn_cancel)

                            Button(
                                onClick = onFinishSession,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFFF3D00)
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = buttonText,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Crisis List
            if (activeSession && crises.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.crisis_detection_title, alarmBpmThreshold.value),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                    )
                }
                items(crises) { crisis ->
                    CrisisItem(crisis)
                }
            }

            //Heart Rate Indicator
            if (activeSession) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF141418)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(stringResource(R.string.heart_rate_label), fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(10.dp))
                            HeartPulseIcon(bpm = bpm)
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = if (bpm > 0) stringResource(R.string.bpm_unit, bpm) else "--",
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (bpm > alarmBpmThreshold.value) Color(0xFFFF3D00) else Color(0xFF00E5FF)
                            )
                            Text(
                                text = getBpmZoneText(bpm),
                                fontSize = 13.sp,
                                color = getBpmZoneColor(bpm),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            //Chart
            if (activeSession){
                item {
                var selectedPeriod by remember { mutableIntStateOf(1) } // 1, 4, 8, 12 hours
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF141418)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.chart_title),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf(1, 4, 8, 12).forEach { hours ->
                                    val isSelected = selectedPeriod == hours
                                    Text(
                                        text = stringResource(R.string.chart_hours, hours),
                                        color = if (isSelected) Color(0xFF00E5FF) else Color.Gray,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(if (isSelected) Color(0xFF1F2E35) else Color.Transparent)
                                            .clickable { selectedPeriod = hours }
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))

                        val maxPointsToKeep = selectedPeriod * 3600
                        val filteredPoints = if (heartRatePoints.size > maxPointsToKeep) {
                            heartRatePoints.takeLast(maxPointsToKeep)
                        } else {
                            heartRatePoints
                        }
                        val downsampledPoints = remember(filteredPoints.size, selectedPeriod) {
                            downsample(filteredPoints, maxTargetSize = 300, totalPossiblePoints = maxPointsToKeep)
                        }

                        RealTimeChart(
                            points = downsampledPoints,
                            minVal = 50,
                            maxVal = 170,
                            lineColor = Color(0xFFFF3D00),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                        )
                    }
                }
            }
            }
        }
    }

    private fun shareSessionCsv(session: WorkoutSession) {
        val dateString = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date(session.startTime))
        val filename = "uggiu_Session_$dateString.csv"
        val csvFile = File(cacheDir, filename)
        try {
            csvFile.bufferedWriter().use { writer ->
                writer.write("Timestamp,BPM\n")
                val start = session.startTime
                val interval = 1000L
                session.getHeartRateHistory().forEachIndexed { index, bpm ->
                    val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(start + (index * interval)))
                    writer.write("$time,$bpm\n")
                }
            }
            val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.fileprovider", csvFile)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_SUBJECT, "Dati Sessione uggiu")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.csv_export_chooser)))
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.csv_export_error), Toast.LENGTH_SHORT).show()
            Log.e(TAG, "CSV Export error", e)
        }
    }

    @Composable
    fun HistoryScreen(
        crises: List<com.example.uggiu.data.CrisisRecord>,
        onDelete: (com.example.uggiu.data.CrisisRecord) -> Unit,
        onDeleteMultiple: (Set<Int>) -> Unit
    ) {
        var selectedIds by remember { mutableStateOf(setOf<Int>()) }

        Column(modifier = Modifier.fillMaxSize()) {
            if (selectedIds.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = stringResource(R.string.history_selected, selectedIds.size), color = Color.White, fontWeight = FontWeight.Bold)
                    Button(
                        onClick = {
                            onDeleteMultiple(selectedIds)
                            selectedIds = emptySet()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3D00)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(stringResource(R.string.history_delete_selected), color = Color.White)
                    }
                }
            }

            if (crises.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                    Text(text = stringResource(R.string.history_empty), color = Color.Gray, fontSize = 16.sp)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(crises) { crisis ->
                        val isSelected = crisis.id in selectedIds
                        CrisisHistoryCard(
                            crisis = crisis,
                            isSelected = isSelected,
                            onToggleSelection = {
                                selectedIds = if (isSelected) selectedIds - crisis.id else selectedIds + crisis.id
                            },
                            onDelete = { onDelete(crisis) }
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun CrisisHistoryCard(
        crisis: com.example.uggiu.data.CrisisRecord,
        isSelected: Boolean,
        onToggleSelection: () -> Unit,
        onDelete: () -> Unit
    ) {
        val startTime = Date(crisis.startTime)
        val dateString = SimpleDateFormat("EEEE, dd MMM - HH:mm:ss", Locale.getDefault()).format(startTime)
        val durationSeconds = if (crisis.endTime != null) (crisis.endTime - crisis.startTime) / 1000 else 0L
        val minutes = durationSeconds / 60
        val seconds = durationSeconds % 60
        val durationFormatted = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)

        Card(
            modifier = Modifier.fillMaxWidth().clickable { onToggleSelection() },
            colors = CardDefaults.cardColors(containerColor = if (isSelected) Color(0xFF2C2C35) else Color(0xFF141418)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelection() },
                    colors = CheckboxDefaults.colors(checkedColor = Color(0xFF00E5FF), uncheckedColor = Color.Gray)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = dateString, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                    Text(text = stringResource(R.string.history_duration, durationFormatted, crisis.maxBpm), fontSize = 12.sp, color = Color.LightGray)
                }
                IconButton(onClick = onDelete) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = stringResource(R.string.history_delete), tint = Color(0xFFFF3D00))
                }
            }
        }
    }

    @Composable
    fun CrisisItem(crisis: com.example.uggiu.service.CrisisEntry) {
        val isRunning = crisis.endTime == null
        val timeFormatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val startTimeStr = timeFormatter.format(Date.from(crisis.startTime))
        
        Column {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isRunning) Color(0xFF2C1010) else Color(0xFF1F1F24)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(stringResource(R.string.crisis_start, startTimeStr), color = Color.LightGray, fontSize = 12.sp)
                        Text(
                            text = if (isRunning) stringResource(R.string.crisis_running) else stringResource(R.string.crisis_finished),
                            color = if (isRunning) Color(0xFFFF3D00) else Color.Gray,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                    val minutes = crisis.durationSeconds / 60
                    val seconds = crisis.durationSeconds % 60
                    val durationFormatted = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
                    
                    Text(
                        text = durationFormatted,
                        color = if (isRunning) Color(0xFFFF3D00) else Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (isRunning) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1B1F)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.safety_position),
                            color = Color(0xFF00E5FF),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Image(
                            painter = painterResource(id = R.drawable.correct_position),
                            contentDescription = stringResource(R.string.safety_position),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(16.dp))
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.safety_position_desc),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun HeartPulseIcon(bpm: Int) {
        val infiniteTransition = rememberInfiniteTransition(label = "heartPulse")
        
        // Dynamic duration based on actual heart rate
        val durationMs = if (bpm > 0) (60000 / bpm) else 1000
        
        val scale by infiniteTransition.animateFloat(
            initialValue = 0.9f,
            targetValue = 1.15f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = durationMs / 2, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "heartScale"
        )

        Box(
            modifier = Modifier
                .size(70.dp)
                .background(Color(0xFF2C1010), RoundedCornerShape(35.dp)),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(36.dp * scale)) {
                val path = Path().apply {
                    // SVG Heart Draw Formula
                    moveTo(size.width / 2f, size.height * 0.25f)
                    cubicTo(
                        size.width * 0.15f, size.height * 0.05f,
                        0f, size.height * 0.4f,
                        size.width / 2f, size.height * 0.9f
                    )
                    cubicTo(
                        size.width, size.height * 0.4f,
                        size.width * 0.85f, size.height * 0.05f,
                        size.width / 2f, size.height * 0.25f
                    )
                }
                drawPath(
                    path = path,
                    color = if (bpm > 140) Color(0xFFFF3D00) else Color(0xFFE91E63)
                )
            }
        }
    }

    @Composable
    fun RealTimeChart(
        points: List<Int>,
        minVal: Int,
        maxVal: Int,
        lineColor: Color,
        modifier: Modifier = Modifier
    ) {
        Canvas(modifier = modifier.clip(RoundedCornerShape(8.dp))) {
            val width = size.width
            val height = size.height
            val paddingLeft = 35.dp.toPx()
            val graphWidth = width - paddingLeft
            
            // Draw background grid lines and Y labels
            val gridLines = 4
            val range = (maxVal - minVal).toFloat()
            
            for (i in 0..gridLines) {
                val fraction = i.toFloat() / gridLines
                val y = height * (1f - fraction)
                
                // Grid line
                drawLine(
                    color = Color(0xFF24242B),
                    start = Offset(paddingLeft, y),
                    end = Offset(width, y),
                    strokeWidth = 1.dp.toPx()
                )
                
                // BPM Label
                val bpmLabel = (minVal + (fraction * range)).toInt().toString()
                drawContext.canvas.nativeCanvas.drawText(
                    bpmLabel,
                    5.dp.toPx(),
                    y - 5.dp.toPx(),
                    Paint().apply {
                        color = android.graphics.Color.LTGRAY
                        textSize = 10.sp.toPx()
                        isAntiAlias = true
                    }
                )
            }

            if (points.size < 2) return@Canvas

            // Use all points provided (they are already filtered/downsampled externally)
            // We scale X based on a fixed capacity of 300 points for a consistent look
            val dataList = points
            val maxPointsCapacity = 300
            val xStep = graphWidth / (maxPointsCapacity - 1)

            val path = Path()
            val fillPath = Path()

            dataList.forEachIndexed { index, value ->
                val coercedVal = value.coerceIn(minVal, maxVal)
                val x = paddingLeft + index * xStep
                // Invert Y coordinate because canvas y-axis goes downwards
                val y = height - ((coercedVal - minVal) / range * height)

                if (index == 0) {
                    path.moveTo(x, y)
                    fillPath.moveTo(x, height)
                    fillPath.lineTo(x, y)
                } else {
                    path.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }
                
                if (index == dataList.size - 1) {
                    fillPath.lineTo(x, height)
                    fillPath.close()
                }
            }

            // Draw area gradient fill
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(lineColor.copy(alpha = 0.25f), Color.Transparent),
                    startY = 0f,
                    endY = height
                )
            )

            // Draw line
            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(width = 2.dp.toPx())
            )
        }
    }

    @Composable
    private fun getBpmZoneText(bpm: Int): String {
        if (bpm == 0) return stringResource(R.string.zone_unknown)
        val age = 30 // standard baseline fallback
        val maxHr = 220 - age
        val percentage = (bpm.toFloat() / maxHr) * 100

        return when {
            percentage < 50 -> stringResource(R.string.zone_rest)
            percentage < 60 -> stringResource(R.string.zone_warmup)
            percentage < 70 -> stringResource(R.string.zone_fat_burn)
            percentage < 80 -> stringResource(R.string.zone_aerobic)
            percentage < 90 -> stringResource(R.string.zone_anaerobic)
            else -> stringResource(R.string.zone_danger)
        }
    }

    private fun getBpmZoneColor(bpm: Int): Color {
        if (bpm == 0) return Color.Gray
        val age = 30
        val maxHr = 220 - age
        val percentage = (bpm.toFloat() / maxHr) * 100

        return when {
            percentage < 50 -> Color.Gray
            percentage < 60 -> Color(0xFF9E9E9E)
            percentage < 70 -> Color(0xFF4CAF50) // Green
            percentage < 80 -> Color(0xFFFFEB3B) // Yellow
            percentage < 90 -> Color(0xFFFF9800) // Orange
            else -> Color(0xFFFF3D00) // Neon Red
        }
    }

    private fun downsample(points: List<Int>, maxTargetSize: Int = 300, totalPossiblePoints: Int = 300): List<Int> {
        val effectiveTargetSize = if (points.size >= totalPossiblePoints) {
            maxTargetSize
        } else {
            ((points.size.toFloat() / totalPossiblePoints) * maxTargetSize).toInt().coerceAtLeast(1)
        }
        
        if (points.size <= effectiveTargetSize) return points
        val step = points.size.toFloat() / effectiveTargetSize
        val result = ArrayList<Int>(effectiveTargetSize)
        for (i in 0 until effectiveTargetSize) {
            val index = (i * step).toInt().coerceIn(0, points.lastIndex)
            result.add(points[index])
        }
        return result
    }
}
