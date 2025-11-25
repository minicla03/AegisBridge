package aegis.android.bridge.data.ble

import android.Manifest
import android.bluetooth.*
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Queue
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue


class BleConnectorGATT(
        private val context: Context,
        private val bluetoothAdapter: BluetoothAdapter?
) {
    private val TAG = "BleConnector"
    private var gatt: BluetoothGatt? = null

    private val _connectionState = MutableStateFlow(ConnectionState.Disconnected)
    val connectionState = _connectionState.asStateFlow()

    private val _dataFlow = MutableSharedFlow<Pair<UUID, ByteArray>>()
    val dataFlow = _dataFlow.asSharedFlow()

    private val descriptorQueue: Queue<BluetoothGattDescriptor> = ConcurrentLinkedQueue()
    private var isWritingDescriptor = false

    private val gattCallback = object : BluetoothGattCallback()
    {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int)
        {
            when (newState)
            {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "Connected to GATT server")
                    _connectionState.value = ConnectionState.Connected
                    g.discoverServices()

                    g.requestMtu(517) // Max MTU

                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "Disconnected from GATT server")
                    _connectionState.value = ConnectionState.Disconnected
                    g.close()
                }

            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            Log.d(TAG, "MTU changed to $mtu")
            gatt.discoverServices()
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Service discovery failed: $status")
                return
            }

            Log.d(TAG, "Services discovered, setting up notifications")
            g.services.forEach { service ->
                service.characteristics.forEach { char ->
                    if (char.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
                        enableNotifications(g, char)
                    }
                }
            }

            processDescriptorQueue(g)
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connectToDevice(address: String) {
        val device: BluetoothDevice? = bluetoothAdapter?.getRemoteDevice(address)
        if (device == null) {
            Log.e(TAG, "Device with address $address not found")
            return
        }

        _connectionState.value = ConnectionState.Connecting
        gatt?.close()
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun disconnect() {
        try {
            gatt?.disconnect()
            gatt?.close()
        } catch (e: SecurityException) {
            Log.e(TAG, "disconnect(): SecurityException", e)
        } finally {
            gatt = null
            _connectionState.value = ConnectionState.Disconnected
            descriptorQueue.clear()
            isWritingDescriptor = false
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun enableNotifications(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic
    ) {
        val success = gatt.setCharacteristicNotification(characteristic, true)
        if (!success) {
            Log.e(TAG, "Failed to enable notifications for ${characteristic.uuid}")
            return
        }

        val descriptor = characteristic.getDescriptor(UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG))
        descriptor?.let {
            enqueueDescriptorWrite(it, gatt)
        } ?: Log.w(TAG, "CCCD descriptor not found for ${characteristic.uuid}")
    }

    private fun enqueueDescriptorWrite(descriptor: BluetoothGattDescriptor, gatt: BluetoothGatt) {
        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        descriptorQueue.offer(descriptor)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun processDescriptorQueue(gatt: BluetoothGatt) {
        if (isWritingDescriptor) return

        val next = descriptorQueue.poll() ?: return
        isWritingDescriptor = true

        val success = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(next, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        } else {
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(next)
        }

        if (success  != true) {
            Log.e(TAG, "Failed to start writing descriptor for ${next.characteristic.uuid}")
            isWritingDescriptor = false
            processDescriptorQueue(gatt)
        }
    }

    companion object {
        private const val CLIENT_CHARACTERISTIC_CONFIG =
            "00002902-0000-1000-8000-00805f9b34fb"
    }
}

enum class ConnectionState {
    Disconnected,
    Connecting,
    Connected
}