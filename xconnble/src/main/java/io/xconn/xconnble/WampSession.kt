package io.xconn.xconnble

import android.content.Context
import io.xconn.wampproto.serializers.Serializer
import io.xconn.xconn.Session

class WampSession(
    private val context: Context,
    private val bleConfig: BleServiceConfig,
    private val onRequestEnableBluetooth: (() -> Unit)? = null,
) {
    private lateinit var bleManager: BleManager
    private lateinit var session: Session

    suspend fun connect(
        realm: String,
        serializer: Serializer,
    ): Session {
        bleManager = BleManager(context, bleConfig, onRequestEnableBluetooth)
        bleManager.startScan()

        val peer = bleManager.awaitPeer()
        val baseSession = join(peer, realm, serializer)

        session = Session(baseSession)
        return session
    }

    suspend fun cleanup() {
        bleManager.cleanup()
        session.close()
    }
}
