package aegis.android.bridge.service

import aegis.android.bridge.data.ble.BleBridgeManager
import aegis.android.bridge.data.ble.ConnectionState
import aegis.android.bridge.presentation.activity.BluetoothSetupActivity
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
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch


class BridgeServiceAndroid: Service()
{
    private val TAG = "BridgeServiceAndroid"
    private val CHANNEL_ID = "bridge_service_channel"

    private val binder = LocalBinder()
    private var bleManager = BleBridgeManager(this)

    private val scope = CoroutineScope(Job() + Dispatchers.IO)

    private val TARGET_DEVICE_NAME = ""
    private val TARGET_DEVICE_MAC: String? = null

    inner class LocalBinder : Binder() {
        fun getService(): BridgeServiceAndroid = this@BridgeServiceAndroid
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()
        startForeground(1, buildForegroundNotification("Initializing BLE service..."))

        // verify if BLE is supported
        if (!bleManager.verifyBluetoothSupport()) stopSelf()

        // Check if Bluetooth is enabled
        if (!bleManager.verifyBluetoothSupport()) {
            val intent = Intent(this, BluetoothSetupActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }

        Log.d(TAG, "BLE pronto per l'uso")
        startBleFlow()
    }

    private fun startBleFlow()
    {
        bleManager.scanner.startScan()

        scope.launch {
            bleManager.scanner.scanResults.collectLatest { result ->
                val deviceName = result.device.name
                val deviceAddress = result.device.address

                val isTarget = (TARGET_DEVICE_MAC?.equals(deviceAddress) == true) ||
                        (deviceName?.contains(TARGET_DEVICE_NAME, ignoreCase = true) == true)

                if (isTarget) {
                    Log.d(TAG, "Target device found: $deviceName / $deviceAddress")
                    bleManager.scanner.stopScan()
                    bleManager.connector.connect(deviceAddress)
                }
            }
        }

        scope.launch {
            bleManager.connector.connectionState.collectLatest { state ->
                when (state) {
                    ConnectionState.Connected -> Log.d(TAG, "Connected to target device")
                    ConnectionState.Disconnected -> {
                        Log.d(TAG, "Disconnected from target, retrying scan...")
                        bleManager.scanner.startScan()
                    }
                    ConnectionState.Connecting -> Log.d(TAG, "Connecting to device...")
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
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? {
        Log.d(TAG, "Activity bound on service")
        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "Activity unbounded from service")
        return super.onUnbind(intent)
    }
}