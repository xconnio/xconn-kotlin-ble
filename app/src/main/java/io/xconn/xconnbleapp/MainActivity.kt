package io.xconn.xconnbleapp

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import io.xconn.wampproto.serializers.CBORSerializer
import io.xconn.xconnble.BleServiceConfig
import io.xconn.xconnble.WampSession
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.UUID

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

    private lateinit var wampSession: WampSession

    private lateinit var enableBtIntent: Intent

    private val enableBtLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                if (allPermissionsGranted()) {
                    connect()
                } else {
                    Toast.makeText(this, "Permissions not granted", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Bluetooth not enabled", Toast.LENGTH_SHORT).show()
            }
        }

    private lateinit var mainTextView: TextView
    private val bleConfig =
        BleServiceConfig(
            serviceUuid = UUID.fromString("6bb39355-45d9-419e-a678-774a7fa9b51c"),
            readerCharUuid = UUID.fromString("4212049d-573e-48ae-9ffa-ddce066e36c8"),
            writerCharUuid = UUID.fromString("661119d9-9996-44f1-a19d-42abe4b47f4f"),
        )

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mainTextView = findViewById(R.id.mainText)
        enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        val requestEnableBluetooth: () -> Unit = {
            enableBtLauncher.launch(enableBtIntent)
        }

        wampSession = WampSession(this, bleConfig, requestEnableBluetooth)

        if (allPermissionsGranted()) {
            if (isBluetoothEnabled()) {
                connect()
            } else {
                enableBtLauncher.launch(enableBtIntent)
            }
        } else {
            ActivityCompat.requestPermissions(
                this@MainActivity,
                REQUIRED_PERMISSIONS,
                REQUEST_CODE_PERMISSIONS,
            )
        }
    }

    private fun connect() {
        if (!allPermissionsGranted()) {
            Toast.makeText(this, "Permissions not granted", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val session = wampSession.connect("realm1", CBORSerializer())
            val result = session.call("test").await()
            mainTextView.text = "Call result: $result"
        }
    }

    private fun allPermissionsGranted(): Boolean =
        REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

    private fun isBluetoothEnabled(): Boolean {
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter

        return bluetoothAdapter?.isEnabled == true
    }

    override fun onDestroy() {
        super.onDestroy()
        runBlocking {
            wampSession.cleanup()
        }
    }
}
