package aegis.android.bridge.domain.usecase

import aegis.android.bridge.domain.model.BleDevice

class ScanDevicesUseCase(private val bleRepository: BleRepository) {
    fun invoke(callback: (BleDevice) -> Unit) {
        bleRepository.startScan(callback)
    }
}
