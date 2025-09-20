package io.xconn.xconnble

import java.nio.ByteBuffer

class MessageAssembler {
    private val builder = ByteBuffer.allocate(514)

    fun feed(data: ByteArray): ByteArray? {
        builder.put(data, 1, data.size - 1)
        val isFinal = data[0]
        return if (isFinal == 1.toByte()) {
            val result = ByteArray(builder.position())
            builder.flip()
            builder.get(result)
            builder.clear()
            result
        } else {
            null
        }
    }

    fun chunkMessage(message: ByteArray): Sequence<ByteArray> =
        sequence {
            val chunkSize = 514
            val totalChunks = (message.size + chunkSize - 1) / chunkSize

            for (i in 0 until totalChunks) {
                val start = i * chunkSize
                val end = if (i == totalChunks - 1) message.size else start + chunkSize
                val chunk = message.copyOfRange(start, end)

                val isFinal = if (i == totalChunks - 1) 1.toByte() else 0.toByte()
                yield(byteArrayOf(isFinal) + chunk)
            }
        }
}
