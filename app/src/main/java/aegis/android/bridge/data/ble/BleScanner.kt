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
    private var scanCallback: ScanCallback? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _scanResults = MutableSharedFlow<ScanResult>()
    val scanResults = _scanResults.asSharedFlow()

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun startScan()
    {
        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: run {
            Log.e(TAG, "BLE scanner not available")
            return
        }

        if (scanCallback != null) {
            Log.w(TAG, "Scan already running")
            return
        }

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                scope.launch {
                    _scanResults.emit(result)

                    val data = result.scanRecord?.manufacturerSpecificData
                    val companyId = data?.keyAt(0)
                    val payload = data?.valueAt(0)

                    Log.d(TAG, "CompanyID: $companyId Data: ${payload?.joinToString()}") }
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { r ->
                    scope.launch { _scanResults.emit(r) }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "BLE scan failed with code $errorCode")
            }
        }


        val filters = mutableListOf<ScanFilter>(
            ScanFilter.Builder()
            .setServiceUuid(ParcelUuid.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e"))
            .setManufacturerData(MY_COMPANY_ID, byteArrayOf(0x01, 0x02))
            .build()
        )

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_MATCH_LOST)
            .setMatchMode(ScanSettings.MATCH_MODE_STICKY)
            .setNumOfMatches(ScanSettings.MATCH_MODE_STICKY)
            .setReportDelay(0L)
            .build()

        scanner.startScan(filters, scanSettings, scanCallback)
        Log.d(TAG, "BLE scan started")
    }

    @RequiresApi(Build.VERSION_CODES.S)
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun stopScan() {
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
                Log.d("BleScanner", "BLE scan stopped")
            }
        } catch (e: SecurityException) {
            Log.e("BleScanner", "stopScan(): SecurityException, permissions revoked?", e)
        }

        scanCallback = null
    }
}
