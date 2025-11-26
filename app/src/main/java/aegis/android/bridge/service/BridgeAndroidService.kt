package aegis.android.bridge.service

import aegis.android.bridge.data.ble.BleBridgeManager
import aegis.android.bridge.data.ble.ConnectionState
import aegis.android.bridge.presentation.activity.BluetoothSetupActivity
import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
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
    private lateinit var bleManager: BleBridgeManager
    private val scope = CoroutineScope(Job() + Dispatchers.IO)

    private val TARGET_DEVICE_NAME = "AegisBracelet"
    private val TARGET_COMPANY_ID = 0xFFF
    private var shouldAutoReconnect = true
    private var isUserDisconnected = false

    inner class LocalBinder : Binder() {
        fun getService(): BridgeServiceAndroid = this@BridgeServiceAndroid
    }

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT])
    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "BridgeServiceAndroid onCreate called")
        bleManager = BleBridgeManager(this)
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
            //val intent = Intent(this, BluetoothSetupActivity::class.java)
            //intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            //startActivity(intent)
        }

        Log.d(TAG, "BLE ready")
        startBleFlow()
    }

    // --- Gestione della connessione BLE
    @RequiresApi(Build.VERSION_CODES.S)
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
                        updateForegroundNotification("Connected to $TARGET_DEVICE_NAME", connected = true)
                    }
                    ConnectionState.Disconnected -> {
                        Log.d(TAG, "Disconnected from target device")
                        if (shouldAutoReconnect && !isUserDisconnected) {
                            Log.d(TAG, "Restarting scan...")
                            bleManager.scanner.startScan()
                            updateForegroundNotification("Scanning for devices...", connected = false)
                        } else {
                            updateForegroundNotification("Disconnected", connected = false, showReconnect = true)
                        }
                    }
                    ConnectionState.Connecting -> {
                        Log.d(TAG, "Connecting to device...")
                        updateForegroundNotification("Connecting to device...", connected = false)
                    }
                }
            }
        }
    }

    private fun stopBridge() {
        shouldAutoReconnect = false
        try {
            bleManager.scanner.stopScan()
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                bleManager.connectorGATT.disconnect()
            }
        } catch (_: Exception) { }

        stopForeground(true)
        stopSelf()
    }

    // --- Gestione Notifiche
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

    private fun buildForegroundNotification(
        content: String,
        connected: Boolean = false,
        showReconnect: Boolean = false
    ): Notification {

        val disconnectIntent = Intent(this, BridgeServiceAndroid::class.java).apply {
            action = "ACTION_DISCONNECT"
        }
        val disconnectPendingIntent = PendingIntent.getService(
            this, 0, disconnectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val reconnectIntent = Intent(this, BridgeServiceAndroid::class.java).apply {
            action = "ACTION_RECONNECT"
        }
        val reconnectPendingIntent = PendingIntent.getService(
            this, 1, reconnectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Bridge Service")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)

        if (connected) {
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Disconnetti", disconnectPendingIntent)
        }

        if (showReconnect) {
            builder.addAction(android.R.drawable.ic_menu_rotate, "Riconnetti", reconnectPendingIntent)
        }

        return builder.build()
    }

    private fun updateForegroundNotification(content: String, connected: Boolean = false, showReconnect: Boolean = false) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(1, buildForegroundNotification(content, connected, showReconnect))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "ACTION_DISCONNECT" -> {
                Log.d(TAG, "User requested disconnect")
                stopBridge()
            }
            "ACTION_RECONNECT" -> {
                Log.d(TAG, "User requested reconnect")
                isUserDisconnected = false
                shouldAutoReconnect = true
                bleManager.scanner.startScan()
                updateForegroundNotification("Scanning for devices...", connected = false)
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    // -- Gestione ciclo di vita del Service
    override fun onBind(intent: Intent?): IBinder {
        Log.d(TAG, "Activity bound on service")
        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "Activity unbound from service")
        return super.onUnbind(intent)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed, cleaning up resources")
        try {
            bleManager.scanner.stopScan()
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) return
            bleManager.connectorGATT.disconnect()
        } catch (_: Exception) { }
    }
}
