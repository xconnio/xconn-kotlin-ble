package io.xconn.xconnble

import android.Manifest
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresPermission
import io.xconn.wampproto.messages.Message
import io.xconn.wampproto.serializers.CBORSerializer
import kotlinx.coroutines.channels.Channel
import java.util.LinkedList
import java.util.Queue

class BlePeer(
    private val gatt: BluetoothGatt,
    private val writerChar: BluetoothGattCharacteristic,
    private val readerChar: BluetoothGattCharacteristic,
) : Peer {
    private val assembler = MessageAssembler()
    private val incoming = Channel<ByteArray>(Channel.UNLIMITED)
    private val messageQueue: Queue<ByteArray> = LinkedList()
    private var sending = false
    private val serializer = CBORSerializer()

    init {
        BleDispatcher.registerReaderCallback(readerChar.uuid) { bytes ->
            val msg = assembler.feed(bytes)
            msg?.let { incoming.trySend(it) }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override suspend fun send(data: ByteArray) {
        messageQueue.clear()
        messageQueue.addAll(assembler.chunkMessage(data))
        sendNextChunk()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override suspend fun sendMessage(message: Message) {
        send(serializer.serialize(message) as ByteArray)
    }

    override suspend fun receive(): Any = incoming.receive()

    override suspend fun receiveMessage(): Message = serializer.deserialize(incoming.receive())

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun close() {
        try {
            gatt.disconnect()
            gatt.close()
        } catch (e: Exception) {
            Log.e("BLE_PEER", "Error closing GATT: ${e.message}")
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun sendNextChunk() {
        if (sending) return
        val chunk = messageQueue.poll() ?: return

        val result =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(
                    writerChar,
                    chunk,
                    BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE,
                )
            } else {
                @Suppress("DEPRECATION")
                writerChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                @Suppress("DEPRECATION")
                writerChar.value = chunk
                @Suppress("DEPRECATION")
                gatt.writeCharacteristic(writerChar)
            }

        sending =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                result == BluetoothStatusCodes.SUCCESS
            } else {
                result as Boolean
            }

        if (!sending) {
            Log.e("BLE_PEER", "Failed to write chunk")
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun onWriteCompleted() {
        sending = false
        sendNextChunk()
    }
}
