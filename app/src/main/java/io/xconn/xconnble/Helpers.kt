package io.xconn.xconnble

import android.bluetooth.BluetoothGattCharacteristic
import io.xconn.wampproto.Joiner
import io.xconn.wampproto.serializers.Serializer
import java.util.UUID
import kotlin.collections.set

suspend fun join(
    peer: Peer,
    realm: String,
    serializer: Serializer,
): PeerBaseSession {
    val joiner = Joiner(realm, serializer)
    val hello = joiner.sendHello()

    peer.send(hello as ByteArray)

    while (true) {
        val msg = peer.receive()
        val toSend = joiner.receive(msg)

        if (toSend == null) {
            val sessionDetails = joiner.getSessionDetails()
            val base = PeerBaseSession(peer, sessionDetails, serializer)
            return base
        }

        peer.send(toSend as ByteArray)
    }
}

object BleDispatcher {
    private val readers = mutableMapOf<UUID, (ByteArray) -> Unit>()

    fun registerReaderCallback(
        uuid: UUID,
        callback: (ByteArray) -> Unit,
    ) {
        readers[uuid] = callback
    }

    fun onCharacteristicChanged(characteristic: BluetoothGattCharacteristic) {
        readers[characteristic.uuid]?.invoke(characteristic.value)
    }
}
