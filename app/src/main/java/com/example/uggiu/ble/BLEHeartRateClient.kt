package com.example.uggiu.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import com.example.uggiu.R
import java.util.UUID

@SuppressLint("MissingPermission")
class BLEHeartRateClient(
    private val context: Context,
    private val onHeartRateUpdated: (Int) -> Unit,
    private val onBatteryLevelUpdated: (Int) -> Unit = {},
    private val onRssiUpdated: (Int) -> Unit = {},
    private val onDeviceConnected: (String, String) -> Unit = { _, _ -> },
    private val onDeviceFound: (BluetoothDevice) -> Unit = {},
    private val onStatusUpdated: (String) -> Unit
) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private var bluetoothGatt: BluetoothGatt? = null
    
    private val handler = Handler(Looper.getMainLooper())
    var isScanning = false
        private set
    private var scanTimeoutRunnable: Runnable? = null
    private var lastConnectedDevice: BluetoothDevice? = null
    private var shouldAutoReconnect = false

    private var reconnectRunnable: Runnable? = null
    private var sessionSetupTimer: Runnable? = null
    private var heartbeatTimer: Runnable? = null
    private var dataTimeoutTimer: Runnable? = null
    private var isCurrentlyConnected = false
    private var hasReceivedFirstPacket = false

    private fun cancelReconnectionTasks() {
        reconnectRunnable?.let { handler.removeCallbacks(it) }
        reconnectRunnable = null
        sessionSetupTimer?.let { handler.removeCallbacks(it) }
        sessionSetupTimer = null
        heartbeatTimer?.let { handler.removeCallbacks(it) }
        heartbeatTimer = null
        dataTimeoutTimer?.let { handler.removeCallbacks(it) }
        dataTimeoutTimer = null
    }

    private fun startSessionSetupTimeout() {
        sessionSetupTimer?.let { handler.removeCallbacks(it) }
        sessionSetupTimer = Runnable {
            if (shouldAutoReconnect && !hasReceivedFirstPacket) {
                Log.w(TAG, "Timeout inizializzazione sessione (15s). Riconnessione...")
                onStatusUpdated(context.getString(R.string.ble_status_timeout))
                close()
                isCurrentlyConnected = false
                scheduleReconnection()
            }
        }
        handler.postDelayed(sessionSetupTimer!!, 15000)
    }

    private fun resetHeartbeatTimeout() {
        heartbeatTimer?.let { handler.removeCallbacks(it) }
        heartbeatTimer = Runnable {
            if (shouldAutoReconnect && isCurrentlyConnected) {
                Log.w(TAG, "Assenza di dati cardio da 10s. Riconnessione...")
                onStatusUpdated(context.getString(R.string.ble_status_no_data))
                onHeartRateUpdated(0)
                close()
                isCurrentlyConnected = false
                scheduleReconnection()
            }
        }
        handler.postDelayed(heartbeatTimer!!, 10000)

        // Reset BPM to 0 if no data for 3 seconds (but don't reconnect yet)
        dataTimeoutTimer?.let { handler.removeCallbacks(it) }
        dataTimeoutTimer = Runnable {
            if (isCurrentlyConnected) {
                Log.d(TAG, "Dati non rilevati (timeout 3s).")
                onHeartRateUpdated(0)
            }
        }
        handler.postDelayed(dataTimeoutTimer!!, 3000)
    }

    private fun scheduleReconnection() {
        cancelReconnectionTasks()
        if (!shouldAutoReconnect || lastConnectedDevice == null) return

        reconnectRunnable = Runnable {
            if (shouldAutoReconnect && lastConnectedDevice != null) {
                Log.i(TAG, "Tentativo di riconnessione automatica...")
                onStatusUpdated(context.getString(R.string.ble_status_reconnecting))
                close()
                connectToDevice(lastConnectedDevice!!)
            }
        }
        handler.postDelayed(reconnectRunnable!!, 3000)
    }

    companion object {
        private const val TAG = "BLEHeartRateClient"
        
        // Standard Heart Rate Service UUID
        val HEART_RATE_SERVICE_UUID: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        // Standard Heart Rate Measurement Characteristic UUID
        val HEART_RATE_MEASUREMENT_CHAR_UUID: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        // Client Characteristic Configuration Descriptor (CCCD) UUID
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        // Standard Battery Service UUID
        val BATTERY_SERVICE_UUID: UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
        // Standard Battery Level Characteristic UUID
        val BATTERY_LEVEL_CHAR_UUID: UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
        
        private const val SCAN_PERIOD: Long = 10000 // 10 seconds scan window
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val deviceName = device.name ?: "Dispositivo Sconosciuto"
            Log.d(TAG, "Dispositivo trovato: $deviceName - ${device.address}")
            onDeviceFound(device)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { result ->
                onDeviceFound(result.device)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scansione fallita con codice: $errorCode")
            onStatusUpdated(context.getString(R.string.ble_status_scan_error, errorCode))
            stopScan()
        }
    }

    fun startScan() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            onStatusUpdated(context.getString(R.string.ble_status_bluetooth_off))
            return
        }

        if (isScanning) return

        onStatusUpdated(context.getString(R.string.scan_searching))
        isScanning = true

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(HEART_RATE_SERVICE_UUID))
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        // Scan only for heart rate services
        bluetoothAdapter.bluetoothLeScanner?.startScan(listOf(filter), settings, scanCallback)

        // Stop scan after time limit
        scanTimeoutRunnable = Runnable {
            if (isScanning) {
                stopScan()
                onStatusUpdated(context.getString(R.string.ble_status_not_found))
            }
        }
        handler.postDelayed(scanTimeoutRunnable!!, SCAN_PERIOD)
    }

    fun stopScan() {
        if (!isScanning) return
        bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        isScanning = false
        scanTimeoutRunnable?.let { handler.removeCallbacks(it) }
        scanTimeoutRunnable = null
    }

    fun setAutoReconnect(enabled: Boolean) {
        shouldAutoReconnect = enabled
    }

    fun connectToDevice(device: BluetoothDevice) {
        cancelReconnectionTasks()
        onStatusUpdated(context.getString(R.string.ble_status_initializing))
        lastConnectedDevice = device
        isCurrentlyConnected = false
        hasReceivedFirstPacket = false
        
        try {
            bluetoothGatt = device.connectGatt(context, false, gattCallback)
            if (bluetoothGatt == null) {
                Log.e(TAG, "connectGatt error.")
                scheduleReconnection()
                return
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gatt Error: ${e.message}")
            scheduleReconnection()
            return
        }
        
        startSessionSetupTimeout()
    }

    fun disconnect() {
        shouldAutoReconnect = false
        isCurrentlyConnected = false
        hasReceivedFirstPacket = false
        cancelReconnectionTasks()
        stopScan()
        bluetoothGatt?.let { gatt ->
            gatt.disconnect()
            onStatusUpdated(context.getString(R.string.ble_status_disconnected))
        }
    }

    fun close() {
        bluetoothGatt?.close()
        bluetoothGatt = null
    }

    fun readRssi() {
        bluetoothGatt?.readRemoteRssi()
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "Connesso al GATT server. Avvio scoperta servizi...")
                isCurrentlyConnected = true
                cancelReconnectionTasks()
                val deviceName = gatt.device.name ?: "---"
                val deviceAddress = gatt.device.address
                handler.post { onDeviceConnected(deviceName, deviceAddress) }
                onStatusUpdated(context.getString(R.string.ble_status_connected))
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "Disconnesso dal GATT server. Status: $status")
                isCurrentlyConnected = false
                onStatusUpdated(context.getString(R.string.ble_status_disconnected))
                onHeartRateUpdated(0) // Reset BPM on disconnect
                close()
                
                if (shouldAutoReconnect && lastConnectedDevice != null) {
                    scheduleReconnection()
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Servizi scoperti con successo.")
                setupHeartRateNotification(gatt)
                readBatteryLevel(gatt)
            } else {
                Log.w(TAG, "Scoperta servizi fallita con stato: $status")
                onStatusUpdated(context.getString(R.string.ble_status_service_error, status))
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid == HEART_RATE_MEASUREMENT_CHAR_UUID) {
                parseHeartRate(characteristic)
            }
        }

        // For Android 13+ support
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            when (characteristic.uuid) {
                HEART_RATE_MEASUREMENT_CHAR_UUID -> parseHeartRate(value)
                BATTERY_LEVEL_CHAR_UUID -> parseBatteryLevel(value)
            }
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (characteristic.uuid == BATTERY_LEVEL_CHAR_UUID) {
                    val value = characteristic.value
                    parseBatteryLevel(value)
                }
            }
        }

        // Android 13+ read callback
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (characteristic.uuid == BATTERY_LEVEL_CHAR_UUID) {
                    parseBatteryLevel(value)
                }
            }
        }

        override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                handler.post { onRssiUpdated(rssi) }
            }
        }
    }

    private fun readBatteryLevel(gatt: BluetoothGatt) {
        val service = gatt.getService(BATTERY_SERVICE_UUID) ?: return
        val characteristic = service.getCharacteristic(BATTERY_LEVEL_CHAR_UUID) ?: return
        
        // Try to enable notifications for battery as well if supported
        if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
            gatt.setCharacteristicNotification(characteristic, true)
            characteristic.getDescriptor(CCCD_UUID)?.let { descriptor ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                } else {
                    @Suppress("DEPRECATION")
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    @Suppress("DEPRECATION")
                    gatt.writeDescriptor(descriptor)
                }
            }
        }
        
        // Always do an initial read
        gatt.readCharacteristic(characteristic)
    }

    private fun setupHeartRateNotification(gatt: BluetoothGatt) {
        val service = gatt.getService(HEART_RATE_SERVICE_UUID)
        if (service == null) {
            Log.e(TAG, "Servizio frequenza cardiaca non trovato!")
            onStatusUpdated(context.getString(R.string.ble_status_hr_not_supported))
            return
        }

        val characteristic = service.getCharacteristic(HEART_RATE_MEASUREMENT_CHAR_UUID)
        if (characteristic == null) {
            Log.e(TAG, "Caratteristica di misura non trovata!")
            onStatusUpdated(context.getString(R.string.ble_status_hr_read_error))
            return
        }

        // Enable local notifications
        gatt.setCharacteristicNotification(characteristic, true)

        // Write descriptor to notify BLE device to start sending measurements
        val descriptor = characteristic.getDescriptor(CCCD_UUID)
        if (descriptor != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                @Suppress("DEPRECATION")
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(descriptor)
            }
            onStatusUpdated(context.getString(R.string.ble_status_connected))
        } else {
            Log.e(TAG, "Descrittore CCCD non trovato!")
            onStatusUpdated(context.getString(R.string.ble_status_notify_error))
        }
    }

    private fun parseBatteryLevel(value: ByteArray) {
        if (value.isEmpty()) return
        val batteryLevel = value[0].toInt() and 0xFF
        Log.d(TAG, "Livello batteria ricevuto: $batteryLevel%")
        handler.post {
            onBatteryLevelUpdated(batteryLevel)
        }
    }

    private fun parseHeartRate(characteristic: BluetoothGattCharacteristic) {
        val value = characteristic.value ?: return
        parseHeartRate(value)
    }

    private fun parseHeartRate(value: ByteArray) {
        if (value.isEmpty()) return
        
        // Byte 0 contains flags
        val flag = value[0].toInt()
        
        // Bits 1 & 2 indicate sensor contact status:
        // 0 & 1: Sensor Contact feature is not supported
        // 2: Sensor Contact feature is supported, but contact is not detected
        // 3: Sensor Contact feature is supported and contact is detected
        val sensorContactDetected = if ((flag and 0x02) != 0) {
            (flag and 0x04) != 0
        } else {
            true // Feature not supported, assume contact to avoid blocking
        }

        if (!sensorContactDetected) {
            Log.d(TAG, "Contatto sensore non rilevato. Invio 0 BPM.")
            handler.post { onHeartRateUpdated(0) }
            return
        }

        // Bit 0 specifies format: 0 = 8-bit, 1 = 16-bit
        val hrValue = if ((flag and 0x01) == 0) {
            value[1].toInt() and 0xFF
        } else {
            ((value[2].toInt() and 0xFF) shl 8) or (value[1].toInt() and 0xFF)
        }
        
        Log.d(TAG, "BPM ricevuto: $hrValue")
        
        // Cancel session setup timer on first packet, start heartbeat timer
        if (shouldAutoReconnect) {
            if (!hasReceivedFirstPacket) {
                hasReceivedFirstPacket = true
                sessionSetupTimer?.let { handler.removeCallbacks(it) }
                sessionSetupTimer = null
            }
            resetHeartbeatTimeout()
        }
        
        // Post to main thread
        handler.post {
            onHeartRateUpdated(hrValue)
        }
    }
}
