package aegis.android.bridge.presentation.activity

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import aegis.android.bridge.service.BridgeServiceAndroid

class BluetoothSetupActivity : AppCompatActivity() {

    private val TAG = "BluetoothSetupActivity"
    private val PERMISSION_REQUEST_CODE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onStart(){
        super.onStart()
        checkBluetooth()
        onStop()
    }

    override fun onStop() {
        super.onStop()
        finish()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private val requestBluetoothEnable =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            checkBluetooth()
        }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun checkBluetooth() {
        val bluetoothManager: BluetoothManager = getSystemService(BluetoothManager::class.java)
        val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "BLE not supported", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "BLE not supported")
            finish()
            return
        }

        /**if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            requestBluetoothEnable.launch(enableBtIntent)
        } else {**/
            checkPermissions()
        //}
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
            Log.d(TAG, "Requesting missing permissions: $notGranted")
            ActivityCompat.requestPermissions(this, notGranted.toTypedArray(), PERMISSION_REQUEST_CODE)
        } else {
            Log.d(TAG, "All permissions granted")
            startBridgeService()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_REQUEST_CODE) {
            permissions.forEachIndexed { index, permission ->
                if (grantResults[index] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "Permission granted: $permission")
                } else {
                    Log.w(TAG, "Permission denied: $permission")
                }
            }

            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Log.d(TAG, "All permissions granted")
                startBridgeService()
            } else {
                Toast.makeText(
                    this,
                    "Some BLE permissions are missing, some features may not work",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        finish()
    }

    private fun startBridgeService() {
        Intent(this, BridgeServiceAndroid::class.java).also {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(it)
            } else {
                startService(it)
            }
        }
    }
}
