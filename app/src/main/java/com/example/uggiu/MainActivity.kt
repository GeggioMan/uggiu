package com.example.uggiu

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Paint
import android.os.Build
import android.os.Bundle
import android.os.IBinder
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
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
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)

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

        // Service States
        val currentService = workoutService
        val isConnected by currentService?.isConnected?.collectAsState(initial = false) ?: remember { mutableStateOf(false) }
        val bleStatus by currentService?.bleStatus?.collectAsState(initial = "Scollegato") ?: remember { mutableStateOf("Scollegato") }
        val currentBpm by currentService?.currentBpm?.collectAsState(initial = 0) ?: remember { mutableIntStateOf(0) }
        val isSessionActive by currentService?.isSessionActive?.collectAsState(initial = false) ?: remember { mutableStateOf(false) }
        val batteryLevel by currentService?.batteryLevel?.collectAsState(initial = -1) ?: remember { mutableIntStateOf(-1) }

        // Alarm States (Synced with Service)
        val alarmBpmThreshold = remember { mutableIntStateOf(currentService?.alarmBpmThreshold ?: 120) }
        val alarmDurationSeconds = remember { mutableIntStateOf(currentService?.alarmDurationSeconds ?: 0) }
        val isAlarmSoundEnabled = remember { mutableStateOf(currentService?.isAlarmSoundEnabled ?: true) }

        // Update service settings when UI changes
        currentService?.let { service ->
            service.alarmBpmThreshold = alarmBpmThreshold.intValue
            service.alarmDurationSeconds = alarmDurationSeconds.intValue
            service.isAlarmSoundEnabled = isAlarmSoundEnabled.value
        }

        val requestPermissionsLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            if (permissions.values.all { it }) {
                workoutService?.startScan()
            } else {
                Toast.makeText(context, "Permessi necessari non concessi.", Toast.LENGTH_SHORT).show()
            }
        }

        fun checkAndStart() {
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

            if (missing.isNotEmpty()) {
                requestPermissionsLauncher.launch(missing.toTypedArray())
            } else {
                if (!isConnected) {
                    workoutService?.startScan()
                }
                workoutService?.startWorkout()
            }
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
                    IconButton(onClick = { showHelpDialog = true }) {
                        Icon(imageVector = Icons.Default.Info, contentDescription = "Help", tint = Color.Gray)
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
                            contentDescription = "Cronologia",
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
                    sessions = sessions,
                    onDelete = { lifecycleScope.launch { db.sessionDao().deleteSession(it) } },
                    onDeleteMultiple = { selectedIds ->
                        lifecycleScope.launch {
                            sessions.filter { it.id in selectedIds }.forEach { db.sessionDao().deleteSession(it) }
                        }
                    },
                    onShare = { shareSessionCsv(it) },
                    currentSessionId = if (isSessionActive) -1 else null // Simplification for badge in history
                )
            } else {
                DashboardScreen(
                    activeSession = isSessionActive,
                    isConnected = isConnected,
                    statusText = bleStatus,
                    bpm = currentBpm,
                    onStartSession = { checkAndStart() },
                    onFinishSession = { workoutService?.stopWorkout() },
                    alarmBpmThreshold = alarmBpmThreshold,
                    alarmDurationSeconds = alarmDurationSeconds,
                    isAlarmSoundEnabled = isAlarmSoundEnabled,
                    heartRatePoints = workoutService?.heartRatePoints ?: emptyList()
                )
            }
        }
    }

    @Composable
    fun HelpDialog(onDismiss: () -> Unit) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = Color(0xFF141418),
            title = { Text("Guida e Risoluzione Problemi", color = Color(0xFF00E5FF), fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HelpItem("Band non connessa?", "Verifica che sulla band sia attivo 'Condividi FC' (Settings > Heart Rate Broadcast). Assicurati che il Bluetooth sia attivo.")
                    HelpItem("Batteria non visibile?", "Non tutte le band supportano la lettura della batteria via Bluetooth standard. Se disponibile, apparirà automaticamente dopo la connessione.")
                    HelpItem("Permessi", "Assicurati di aver concesso i permessi Bluetooth, Posizione e Sensori del corpo nelle impostazioni dello smartphone.")
                    HelpItem("Background", "Per evitare disconnessioni, imposta l'app 'uggiu' come 'Senza restrizioni' nelle impostazioni batteria dello smartphone.")
                }
            },
            confirmButton = {
                Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F1F24))) {
                    Text("Ho capito", color = Color.White)
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
        statusText: String,
        bpm: Int,
        onStartSession: () -> Unit,
        onFinishSession: () -> Unit,
        alarmBpmThreshold: MutableState<Int>,
        alarmDurationSeconds: MutableState<Int>,
        isAlarmSoundEnabled: MutableState<Boolean>,
        heartRatePoints: List<Int>
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF141418)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = if (activeSession) "Sessione in Corso" else "Nuova Sessione",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = statusText, fontSize = 14.sp, color = Color.LightGray)
                        Spacer(modifier = Modifier.height(12.dp))
                        val isEnabled: Boolean
                        val buttonText: String
                        val buttonColor: Color
                        
                        if (activeSession) {
                            if (isConnected) {
                                buttonText = "TERMINA"
                                buttonColor = Color(0xFFFF3D00)
                                isEnabled = true
                            } else {
                                buttonText = "CONNETTENDO..."
                                buttonColor = Color.Gray
                                isEnabled = false
                            }
                        } else {
                            buttonText = "AVVIA"
                            buttonColor = Color(0xFF00E5FF)
                            isEnabled = true
                        }

                        Button(
                            onClick = { if (activeSession) onFinishSession() else onStartSession() },
                            enabled = isEnabled,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = buttonColor,
                                disabledContainerColor = Color.DarkGray
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = buttonText,
                                color = if (isEnabled && !activeSession) Color.Black else Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            if (!activeSession) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF141418)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(text = "Configurazione Allarme", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Soglia Battiti (X)", color = Color.Gray, fontSize = 12.sp)
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
                                    Text("Durata (Y sec)", color = Color.Gray, fontSize = 12.sp)
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
                                    text = if (isAlarmSoundEnabled.value) "Allarme: SUONO" else "Allarme: NOTIFICA/VIBRA",
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
                            Text("BATTITI CARDIO", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(10.dp))
                            HeartPulseIcon(bpm = bpm)
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = if (bpm > 0) "$bpm BPM" else "--",
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

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF141418)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = "Andamento Cardio", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(modifier = Modifier.height(10.dp))
                        RealTimeChart(
                            points = downsample(heartRatePoints),
                            minVal = 50,
                            maxVal = 170,
                            lineColor = Color(0xFFFF3D00),
                            modifier = Modifier.fillMaxWidth().height(130.dp)
                        )
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
            startActivity(Intent.createChooser(intent, "Esporta dati sessione"))
        } catch (e: Exception) {
            Toast.makeText(this, "Errore durante l'esportazione CSV", Toast.LENGTH_SHORT).show()
        }
    }

    @Composable
    fun HistoryScreen(
        sessions: List<WorkoutSession>,
        onDelete: (WorkoutSession) -> Unit,
        onDeleteMultiple: (Set<Int>) -> Unit,
        onShare: (WorkoutSession) -> Unit,
        currentSessionId: Int?
    ) {
        var selectedIds by remember { mutableStateOf(setOf<Int>()) }

        Column(modifier = Modifier.fillMaxSize()) {
            if (selectedIds.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "${selectedIds.size} selezionate", color = Color.White, fontWeight = FontWeight.Bold)
                    Button(
                        onClick = {
                            onDeleteMultiple(selectedIds)
                            selectedIds = emptySet()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3D00)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Elimina Selezionate", color = Color.White)
                    }
                }
            }

            if (sessions.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                    Text(text = "Nessuna sessione salvata.", color = Color.Gray, fontSize = 16.sp)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(sessions) { session ->
                        val isSelected = session.id in selectedIds
                        val isActive = session.id == currentSessionId
                        SessionHistoryCard(
                            session = session,
                            isSelected = isSelected,
                            isActive = isActive,
                            onToggleSelection = {
                                selectedIds = if (isSelected) selectedIds - session.id else selectedIds + session.id
                            },
                            onDelete = { onDelete(session) },
                            onShare = { onShare(session) }
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun SessionHistoryCard(
        session: WorkoutSession,
        isSelected: Boolean,
        isActive: Boolean,
        onToggleSelection: () -> Unit,
        onDelete: () -> Unit,
        onShare: () -> Unit
    ) {
        var expanded by remember { mutableStateOf(false) }
        val dateString = SimpleDateFormat("EEEE, dd MMM yyyy - HH:mm", Locale.getDefault()).format(Date(session.startTime))
        val durationText = "${((session.endTime - session.startTime) / 1000) / 60}m ${((session.endTime - session.startTime) / 1000) % 60}s"

        Card(
            modifier = Modifier.fillMaxWidth().clickable { onToggleSelection() },
            colors = CardDefaults.cardColors(containerColor = if (isSelected) Color(0xFF2C2C35) else Color(0xFF141418)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Box {
                if (isActive) Box(modifier = Modifier.fillMaxWidth().height(4.dp).background(Color(0xFF00E5FF)))
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { onToggleSelection() },
                                colors = CheckboxDefaults.colors(checkedColor = Color(0xFF00E5FF), uncheckedColor = Color.Gray)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = dateString, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                                    if (isActive) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Box(modifier = Modifier.background(Color(0xFF00E5FF), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 2.dp)) {
                                            Text("ATTIVA", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                Text(text = "Durata: $durationText", fontSize = 12.sp, color = Color.LightGray)
                            }
                        }
                        Text(text = "${session.averageHeartRate} BPM", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E5FF))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        modifier = Modifier.align(Alignment.End).clickable { expanded = !expanded },
                        text = if (expanded) "Nascondi dettagli ▲" else "Mostra dettagli ▼",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                    if (expanded) {
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = Color(0xFF2C2C35))
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Massimo: ${session.maxHeartRate}", fontSize = 13.sp, color = Color.White)
                            Text("Minimo: ${session.getHeartRateHistory().minOrNull() ?: 0}", fontSize = 13.sp, color = Color.White)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        RealTimeChart(
                            points = downsample(session.getHeartRateHistory()),
                            minVal = 50,
                            maxVal = 170,
                            lineColor = Color(0xFF00E5FF),
                            modifier = Modifier.fillMaxWidth().height(100.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = onShare, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F1F24)), shape = RoundedCornerShape(8.dp)) {
                                Text("CSV", color = Color.White)
                            }
                            Button(onClick = onDelete, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3D00)), shape = RoundedCornerShape(8.dp)) {
                                Text("Elimina", color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun HeartPulseIcon(bpm: Int) {
        val infiniteTransition = rememberInfiniteTransition(label = "heartPulse")
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
        Box(modifier = Modifier.size(70.dp).background(Color(0xFF2C1010), RoundedCornerShape(35.dp)), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(36.dp * scale)) {
                val path = Path().apply {
                    moveTo(size.width / 2f, size.height * 0.25f)
                    cubicTo(size.width * 0.15f, size.height * 0.05f, 0f, size.height * 0.4f, size.width / 2f, size.height * 0.9f)
                    cubicTo(size.width, size.height * 0.4f, size.width * 0.85f, size.height * 0.05f, size.width / 2f, size.height * 0.25f)
                }
                drawPath(path = path, color = if (bpm > 140) Color(0xFFFF3D00) else Color(0xFFE91E63))
            }
        }
    }

    @Composable
    fun RealTimeChart(points: List<Int>, minVal: Int, maxVal: Int, lineColor: Color, modifier: Modifier = Modifier) {
        Canvas(modifier = modifier.clip(RoundedCornerShape(8.dp))) {
            val width = size.width
            val height = size.height
            val paddingLeft = 35.dp.toPx()
            val graphWidth = width - paddingLeft
            val gridLines = 4
            val range = (maxVal - minVal).toFloat()
            for (i in 0..gridLines) {
                val fraction = i.toFloat() / gridLines
                val y = height * (1f - fraction)
                drawLine(color = Color(0xFF24242B), start = Offset(paddingLeft, y), end = Offset(width, y), strokeWidth = 1.dp.toPx())
                val bpmLabel = (minVal + (fraction * range)).toInt().toString()
                drawContext.canvas.nativeCanvas.drawText(bpmLabel, 5.dp.toPx(), y - 5.dp.toPx(), Paint().apply { color = android.graphics.Color.LTGRAY; textSize = 10.sp.toPx(); isAntiAlias = true })
            }
            if (points.size < 2) return@Canvas
            val dataList = points
            val maxPointsCapacity = 300
            val xStep = graphWidth / (maxPointsCapacity - 1)
            val path = Path()
            val fillPath = Path()
            dataList.forEachIndexed { index, value ->
                val coercedVal = value.coerceIn(minVal, maxVal)
                val x = paddingLeft + index * xStep
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
            drawPath(path = fillPath, brush = Brush.verticalGradient(colors = listOf(lineColor.copy(alpha = 0.25f), Color.Transparent), startY = 0f, endY = height))
            drawPath(path = path, color = lineColor, style = Stroke(width = 2.dp.toPx()))
        }
    }

    fun getBpmZoneText(bpm: Int): String {
        if (bpm == 0) return "Sconosciuto"
        val age = 30
        val maxHr = 220 - age
        val percentage = (bpm.toFloat() / maxHr) * 100
        return when {
            percentage < 50 -> "Riposo"
            percentage < 60 -> "Riscaldamento"
            percentage < 70 -> "Brucia Grassi"
            percentage < 80 -> "Aerobico"
            percentage < 90 -> "Anaerobico"
            else -> "Pericolo"
        }
    }

    fun getBpmZoneColor(bpm: Int): Color {
        if (bpm == 0) return Color.Gray
        val age = 30
        val maxHr = 220 - age
        val percentage = (bpm.toFloat() / maxHr) * 100
        return when {
            percentage < 50 -> Color.Gray
            percentage < 60 -> Color(0xFF9E9E9E)
            percentage < 70 -> Color(0xFF4CAF50)
            percentage < 80 -> Color(0xFFFFEB3B)
            percentage < 90 -> Color(0xFFFF9800)
            else -> Color(0xFFFF3D00)
        }
    }

    fun downsample(points: List<Int>, maxTargetSize: Int = 300, totalPossiblePoints: Int = 300): List<Int> {
        val effectiveTargetSize = if (points.size >= totalPossiblePoints) maxTargetSize else ((points.size.toFloat() / totalPossiblePoints) * maxTargetSize).toInt().coerceAtLeast(1)
        if (points.size <= effectiveTargetSize) return points
        val step = points.size.toFloat() / effectiveTargetSize
        return List(effectiveTargetSize) { i -> points[(i * step).toInt().coerceIn(0, points.lastIndex)] }
    }
}
