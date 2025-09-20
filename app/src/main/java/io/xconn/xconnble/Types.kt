package io.xconn.xconnble

import io.xconn.wampproto.SessionDetails
import io.xconn.wampproto.messages.Message
import io.xconn.wampproto.serializers.Serializer
import io.xconn.xconn.IBaseSession

interface Peer {
    suspend fun send(data: ByteArray)

    suspend fun sendMessage(message: Message)

    suspend fun receive(): Any

    suspend fun receiveMessage(): Message

    fun close()
}

class PeerBaseSession(
    private val peer: Peer,
    private val sessionDetails: SessionDetails,
    private val serializer: Serializer,
) : IBaseSession {
    override fun id(): Long = sessionDetails.sessionID

    override fun realm(): String = sessionDetails.realm

    override fun authid(): String = sessionDetails.authid

    override fun authrole(): String = sessionDetails.authrole

    override fun serializer(): Serializer = serializer

    override suspend fun send(data: Any) {
        peer.send(data as ByteArray)
    }

    override suspend fun receive(): Any = peer.receive()

    override suspend fun sendMessage(msg: Message) {
        peer.sendMessage(msg)
    }

    override suspend fun receiveMessage(): Message = peer.receiveMessage()

    override suspend fun close() = peer.close()
}
