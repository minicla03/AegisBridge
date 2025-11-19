package aegis.android.bridge.domain.interfaces

interface IBleScanner {
    fun startScan()
    fun stopScan()
    fun setScanCallback(callback: (BleDevice) -> Unit)
}
