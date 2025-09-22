package io.xconn.xconnble

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import java.util.UUID

class BleManager(
    private val context: Context,
    private val bleConfig: BleServiceConfig,
    private val onRequestEnableBluetooth: (() -> Unit)? = null,
) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter
    private val bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner
    private var bluetoothGatt: BluetoothGatt? = null
    private val peerDeferred = CompletableDeferred<BlePeer>()

    suspend fun awaitPeer(): BlePeer = peerDeferred.await()

    fun startScan() {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w("BLE", "Missing BLUETOOTH_SCAN permission")
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            onRequestEnableBluetooth?.invoke() ?: Toast
                .makeText(context, "Bluetooth is disabled", Toast.LENGTH_SHORT)
                .show()
            return
        }

        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(bleConfig.serviceUuid)).build()

        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()

        bluetoothLeScanner.startScan(listOf(filter), settings, scanCallback)
        Toast.makeText(context, "Scanning for devices...", Toast.LENGTH_SHORT).show()
    }

    private val scanCallback =
        object : ScanCallback() {
            override fun onScanResult(
                callbackType: Int,
                result: ScanResult?,
            ) {
                result?.device?.let { device ->

                    val hasPermission =
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.BLUETOOTH_SCAN,
                        ) == PackageManager.PERMISSION_GRANTED

                    if (hasPermission) {
                        bluetoothLeScanner.stopScan(this)
                        connectToDevice(device)
                    } else {
                        Log.w(
                            "BLE_SCAN",
                            "Missing BLUETOOTH_SCAN permission; cannot stop scan or connect.",
                        )
                    }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                peerDeferred.completeExceptionally(RuntimeException("Scan failed: $errorCode"))
                Log.e("BLE_SCAN", "Scan failed: $errorCode")
            }

            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            private fun connectToDevice(device: BluetoothDevice) {
                bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
                Toast.makeText(context, "Connecting to ${device.name}", Toast.LENGTH_SHORT).show()
            }

            private val gattCallback =
                object : BluetoothGattCallback() {
                    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
                    override fun onConnectionStateChange(
                        gatt: BluetoothGatt,
                        status: Int,
                        newState: Int,
                    ) {
                        if (newState == BluetoothProfile.STATE_CONNECTED) {
                            gatt.discoverServices()
                        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                            peerDeferred.completeExceptionally(RuntimeException("Disconnected"))
                            Log.w("BLE_GATT", "Disconnected from GATT")
                        }
                    }

                    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
                    override fun onServicesDiscovered(
                        gatt: BluetoothGatt,
                        status: Int,
                    ) {
                        if (status != BluetoothGatt.GATT_SUCCESS) return

                        val service = gatt.getService(bleConfig.serviceUuid)
                        val reader = service?.getCharacteristic(bleConfig.readerCharUuid)
                        val writer = service?.getCharacteristic(bleConfig.writerCharUuid)

                        if (reader != null && writer != null) {
                            gatt.setCharacteristicNotification(reader, true)

                            val descriptor =
                                reader.getDescriptor(
                                    UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"),
                                )
                            descriptor?.let {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    gatt.writeDescriptor(
                                        it,
                                        BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE,
                                    )
                                } else {
                                    it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                    gatt.writeDescriptor(it)
                                }
                            }

                            val peer = BlePeer(gatt, writer, reader)
                            peerDeferred.complete(peer)
                        } else {
                            peerDeferred.completeExceptionally(
                                RuntimeException("Missing reader or writer characteristic"),
                            )
                            Log.e("BLE_GATT", "Missing reader or writer characteristic")
                        }
                    }

                    override fun onCharacteristicChanged(
                        gatt: BluetoothGatt,
                        characteristic: BluetoothGattCharacteristic,
                    ) {
                        BleDispatcher.onCharacteristicChanged(characteristic)
                    }

                    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
                    override fun onCharacteristicWrite(
                        gatt: BluetoothGatt,
                        characteristic: BluetoothGattCharacteristic,
                        status: Int,
                    ) {
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            peerDeferred.getCompleted().onWriteCompleted()
                        } else {
                            Log.e("BLE_MANAGER", "Write failed with status $status")
                        }
                    }
                }
        }

    fun cleanup() {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        bluetoothGatt?.close()
        bluetoothGatt = null
    }
}
