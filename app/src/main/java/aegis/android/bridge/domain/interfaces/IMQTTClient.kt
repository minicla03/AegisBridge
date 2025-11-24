package aegis.android.bridge.domain.interfaces

interface IMQTTClient {
    fun connect(onConnected: () -> Unit)
    fun disconnect()
    fun publish(topic: String, payload: String, qos: Int = 0)
    fun subscribe(topic: String)
    val messagesFlow: kotlinx.coroutines.flow.SharedFlow<Pair<String, String>>
}
