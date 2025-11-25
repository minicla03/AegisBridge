package aegis.android.bridge.data.ble

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch


class BleScanner(
    private val context: Context,
    private val bluetoothAdapter: BluetoothAdapter?
) {
    private val TAG = "BleScanner"
    private val MY_COMPANY_ID= 0xFFFF
    private val SERVICE_UUID = "6e400001-b5a3-f393-e0a9-e50e24dcca9e"

    private var isScanning = false
    private var scanRetryDelayMs = 1500L
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _scanResults = MutableSharedFlow<ScanResult>()
    val scanResults = _scanResults.asSharedFlow()

    private var scanCallback: ScanCallback? = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            scope.launch {
                _scanResults.emit(result)

                val data = result.scanRecord?.manufacturerSpecificData

                data?.let { map ->
                    for (i in 0 until map.size()) {
                        val companyId = map.keyAt(i)
                        val payload = map.valueAt(i)

                        Log.d(TAG, "CompanyID: $companyId Data: ${payload?.joinToString()}") }
                }
            }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { r ->
                scope.launch { _scanResults.emit(r) }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "BLE scan failed with code $errorCode")

            stopScan()

            // Backoff max 10 sec
            scanRetryDelayMs = (scanRetryDelayMs * 2).coerceAtMost(10_000L)

            handler.postDelayed({
                startScan()
            }, scanRetryDelayMs)
        }
    }


    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun startScan()
    {
        if (isScanning) {
            Log.d(TAG, "Scan already in progress")
            return
        }

        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: run {
            Log.e(TAG, "BLE scanner not available")
            return
        }

        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid.fromString(SERVICE_UUID))
                .build(),
            ScanFilter.Builder()
                .setManufacturerData(MY_COMPANY_ID, byteArrayOf())
                .build()
        )

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setMatchMode(ScanSettings.MATCH_MODE_STICKY)
            .setNumOfMatches(ScanSettings.MATCH_NUM_FEW_ADVERTISEMENT)
            .setReportDelay(0L)
            .build()

        try {
            scanner.startScan(filters, scanSettings, scanCallback)
            isScanning = true
            scanRetryDelayMs = 1500L // Reset retry delay on successful start
            Log.d(TAG, "BLE scan started")
        } catch (e: SecurityException) {
            Log.e(TAG, "startScan(): SecurityException, permissions not granted", e)
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun stopScan() {
        if (!isScanning) return

        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: return

        // Controllo permesso
        val hasPermission =
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            Log.w("BleScanner", "stopScan(): BLUETOOTH_SCAN permission not granted")
            return
        }

        try {
            scanCallback?.let {
                scanner.stopScan(it)
                isScanning = false
                Log.d(TAG, "BLE scan stopped")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "stopScan(): SecurityException, permissions revoked?", e)
        }
    }
}
