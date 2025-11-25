package aegis.android.bridge.data.ble

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanResult
import android.content.Context
import android.util.Log
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BleBridgeManager(context: Context)
{
    private val TAG = "BleBridgeManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)


    private val bluetoothManager: BluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    val scanner = BleScanner(context, bluetoothAdapter)
    val connectorGATT = BleConnectorGATT(context, bluetoothAdapter)

    private val _discoveredDevices = MutableStateFlow<Map<String, ScanResult>>(emptyMap())
    val discoveredDevices: StateFlow<Map<String, ScanResult>> = _discoveredDevices.asStateFlow()

    init {
        observeScanResults()
    }

    private fun observeScanResults() {
        scope.launch {
            scanner.scanResults.collect { scanResult ->
                val currentDevices = _discoveredDevices.value.toMutableMap()
                currentDevices[scanResult.device.address] = scanResult
                _discoveredDevices.value = currentDevices

                Log.d(TAG, "Device found: ${scanResult.device.name ?: "Unknown"} - ${scanResult.device.address}")
            }
        }
    }

    fun verifyBluetoothSupport(): Boolean {
        if (bluetoothAdapter == null) {
            Log.e(TAG, "BLE not supported")
            return false
        }
        return true
    }

    fun verifyBluetoothEnabled(): Boolean {
        if (bluetoothAdapter?.isEnabled != true) {
            Log.d(TAG, "Bluetooth not enabled")
            return false
        }
        return true
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun scanForDevices()
    {
        if (!verifyBluetoothSupport() || !verifyBluetoothEnabled()) {
            Log.w(TAG, "Cannot scan: Bluetooth not supported or disabled")
            return
        }
        return scanner.startScan()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun stopScan() {
        scanner.stopScan()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connectToDevice(address: String): Boolean {
        if (!verifyBluetoothSupport() || !verifyBluetoothEnabled()) {
            Log.w(TAG, "Cannot connect: Bluetooth not supported or disabled")
            return false
        }

        stopScan()

        connectorGATT.connectToDevice(address)
        return true
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun disconnect() {
        connectorGATT.disconnect()
    }

}
