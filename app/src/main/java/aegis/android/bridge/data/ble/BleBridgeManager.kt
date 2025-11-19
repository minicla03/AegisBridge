package aegis.android.bridge.data.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.util.Log

class BleBridgeManager(context: Context)
{
    private val TAG = "BleBridgeManager"

    private val bluetoothManager: BluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    val scanner = BleScanner(bluetoothAdapter)
    val connector = BleConnector(context, bluetoothAdapter)

    fun verifyBluetoothSupport(): Boolean
    {
        if (bluetoothAdapter == null)
        {
            Log.e(TAG, "BLE not supported")
            return false
        }
        return true
    }

    fun verifyBluetoothEnabled(): Boolean
    {
        if (bluetoothAdapter?.isEnabled != true)
        {
            Log.d(TAG, "Bluetooth not enabled")
            return false
        }
        return true
    }
}
