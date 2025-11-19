package aegis.android.bridge.presentation.activity

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity

class BluetoothSetupActivity : AppCompatActivity() {

    private val TAG = "BluetoothSetupActivity"

    @RequiresApi(Build.VERSION_CODES.S)
    private val requestBluetoothEnable =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            checkBluetooth()
        }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkBluetooth()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun checkBluetooth() {
        val bluetoothManager: BluetoothManager = getSystemService(BluetoothManager::class.java)
        val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "BLE doesn't supported", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "BLE doesn't supported")
            finish()
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            requestBluetoothEnable.launch(enableBtIntent)
        } else {
            checkPermissions()
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun checkPermissions() {
        val neededPermissions = arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        val notGranted = neededPermissions.filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (notGranted.isNotEmpty()) {
            Log.d(TAG, "Request missing permission: $notGranted")
            ActivityCompat.requestPermissions(this, notGranted.toTypedArray(), 100)
        } else {
            Log.d(TAG, "All permissions granted")
            finish()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        permissions.forEachIndexed { index, permission ->
            if (grantResults[index] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Permission granted: $permission")
            } else {
                Log.w(TAG, "Permission denied: $permission")
            }
        }

        if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            Log.d(TAG, "All permissions granted")
        } else {
            Toast.makeText(this,
                "Some BLE permissions are missing, some features may not work",
                Toast.LENGTH_LONG
            ).show()
        }

        finish()
    }
}
