package aegis.android.bridge.data.mqtt

import aegis.android.bridge.domain.interfaces.IMQTTClient
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.*

class PahoMqtt(
    private val context: Context,
    private val serverUri: String,
    private val clientId: String
) : IMQTTClient {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = MqttAndroidClient(context, serverUri, clientId)

    private val _messagesFlow = MutableSharedFlow<Pair<String,String>>()
    override val messagesFlow = _messagesFlow.asSharedFlow()

    override fun connect(onConnected: () -> Unit) {
        val options = MqttConnectOptions().apply { isCleanSession = true }

        client.connect(options, null, object : IMqttActionListener {
            override fun onSuccess(asyncActionToken: IMqttToken?) {
                Log.d("MQTT", "Connected to broker")
                onConnected()
            }

            override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                Log.e("MQTT", "Failed to connect", exception)
            }
        })

        // Gestione messaggi in arrivo
        client.setCallback(object : MqttCallback {
            override fun connectionLost(cause: Throwable?) {
                Log.w("MQTT", "Connection lost", cause)
            }

            override fun messageArrived(topic: String, message: MqttMessage) {
                scope.launch { _messagesFlow.emit(topic to message.toString()) }
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) { }
        })
    }

    override fun disconnect() {
        try {
            client.disconnect()
            Log.d("MQTT", "Disconnected")
        } catch (e: MqttException) {
            Log.e("MQTT", "Disconnect failed", e)
        }
    }

    override fun publish(topic: String, payload: String, qos: Int) {
        try {
            val message = MqttMessage(payload.toByteArray()).apply { this.qos = qos }
            client.publish(topic, message)
        } catch (e: MqttException) {
            Log.e("MQTT", "Publish failed", e)
        }
    }

    override fun subscribe(topic: String) {
        try {
            client.subscribe(topic, 0)
        } catch (e: MqttException) {
            Log.e("MQTT", "Subscribe failed", e)
        }
    }
}
