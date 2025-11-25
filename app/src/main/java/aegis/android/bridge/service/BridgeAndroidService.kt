package aegis.android.bridge.service

import aegis.android.bridge.data.ble.BleBridgeManager
import aegis.android.bridge.data.ble.ConnectionState
import aegis.android.bridge.presentation.activity.BluetoothSetupActivity
import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class BridgeServiceAndroid : Service() {

    private val TAG = "BridgeServiceAndroid"
    private val CHANNEL_ID = "bridge_service_channel"

    private val binder = LocalBinder()
    private var bleManager = BleBridgeManager(this)
    private val scope = CoroutineScope(Job() + Dispatchers.IO)

    private val TARGET_DEVICE_NAME = "AegisBracelet"
    private val TARGET_COMPANY_ID = 0xFFF

    inner class LocalBinder : Binder() {
        fun getService(): BridgeServiceAndroid = this@BridgeServiceAndroid
    }

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT])
    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()
        startForeground(1, buildForegroundNotification("Initializing BLE service..."))

        // verifica supporto BLE
        if (!bleManager.verifyBluetoothSupport()) {
            Log.e(TAG, "BLE not supported, stopping service")
            stopSelf()
            return
        }

        // verifica se Bluetooth è attivo
        if (!bleManager.verifyBluetoothEnabled()) {
            Log.w(TAG, "Bluetooth not enabled, starting setup activity")
            val intent = Intent(this, BluetoothSetupActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }

        Log.d(TAG, "BLE ready")
        startBleFlow()
    }

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT])
    private fun startBleFlow() {
        bleManager.scanner.startScan()

        // Coroutine per raccogliere i risultati dello scan
        scope.launch {
            bleManager.scanner.scanResults.collectLatest { result ->
                val deviceName = result.device.name
                val deviceAddress = result.device.address
                val data = result.scanRecord?.manufacturerSpecificData
                val companyId = data?.keyAt(0)

                val isTarget = (TARGET_COMPANY_ID == companyId) ||
                        (deviceName?.contains(TARGET_DEVICE_NAME, ignoreCase = true) == true)

                if (!isTarget) {
                    Log.d(TAG, "Device ignored: $deviceName / $deviceAddress")
                    return@collectLatest
                }

                Log.d(TAG, "Target device found: $deviceName / $deviceAddress")

                // fermo lo scan e provo a connettermi
                try {
                    bleManager.scanner.stopScan()
                    bleManager.connectorGATT.connectToDevice(deviceAddress)
                } catch (e: SecurityException) {
                    Log.e(TAG, "Permission revoked or BLE error: ${e.message}", e)
                }
            }
        }

        // Coroutine per monitorare lo stato di connessione
        scope.launch {
            bleManager.connectorGATT.connectionState.collectLatest { state ->
                when (state) {
                    ConnectionState.Connected -> {
                        Log.d(TAG, "Connected to target device")
                        updateForegroundNotification("Connected to $TARGET_DEVICE_NAME")
                    }
                    ConnectionState.Disconnected -> {
                        Log.d(TAG, "Disconnected from target, restarting scan...")
                        bleManager.scanner.startScan()
                        updateForegroundNotification("Scanning for devices...")
                    }
                    ConnectionState.Connecting -> {
                        Log.d(TAG, "Connecting to device...")
                        updateForegroundNotification("Connecting to device...")
                    }
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Bridge Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(content: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Bridge Service")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .build()
    }

    private fun updateForegroundNotification(content: String) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(1, buildForegroundNotification(content))
    }

    override fun onBind(intent: Intent?): IBinder? {
        Log.d(TAG, "Activity bound on service")
        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "Activity unbound from service")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed, cleaning up resources")
        try {
            bleManager.scanner.stopScan()
            bleManager.connectorGATT.disconnect()
        } catch (_: Exception) {
        }
    }
}
