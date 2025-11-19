package aegis.android.bridge.data.ble

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.util.Log
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class BleScanner(private val bluetoothAdapter: BluetoothAdapter?) {

    private val TAG = "BleScanner"
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

        scanCallback = object : ScanCallback()
        {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                scope.launch { _scanResults.emit(result) }
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

        scanner.startScan(scanCallback)
        Log.d(TAG, "BLE scan started")
    }

    fun stopScan() {
        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: return
        scanCallback?.let {
            scanner.stopScan(it)
            Log.d(TAG, "BLE scan stopped")
        }
        scanCallback = null
    }
}
