package io.xconn.xconnble

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import io.xconn.wampproto.serializers.CBORSerializer
import io.xconn.xconn.Session
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 1

        private val REQUIRED_PERMISSIONS =
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION,
            )
    }

    private lateinit var mainTextView: TextView
    private lateinit var bleManager: BleManager
    private var blePeer: BlePeer? = null

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mainTextView = findViewById(R.id.mainText)

        if (allPermissionsGranted()) {
            startBluetoothFlow()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
    }

    private fun allPermissionsGranted(): Boolean =
        REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    private fun startBluetoothFlow() {
        bleManager = BleManager(this)

        bleManager.startScan()

        lifecycleScope.launch {
            mainTextView.text = "Connecting..."
            val peer = bleManager.awaitPeer()

            mainTextView.text = "Connected. Joining WAMP..."

            val base = join(peer, "realm1", CBORSerializer())
            val session = Session(base)

            mainTextView.text = "WAMP session ready: ${base.id()}"

            // Test call
            val result = session.call("test").await()
            mainTextView.text = "Call result: $result"

            blePeer = peer
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS && allPermissionsGranted()) {
            startBluetoothFlow()
        } else {
            Toast.makeText(this, "Permissions not granted", Toast.LENGTH_SHORT).show()
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onDestroy() {
        bleManager.cleanup()
        super.onDestroy()
    }
}
