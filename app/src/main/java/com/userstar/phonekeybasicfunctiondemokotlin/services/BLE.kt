package com.userstar.phonekeybasicfunctiondemokotlin.services

import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.content.Context
import android.widget.Toast
import timber.log.Timber

class BLE {

    companion object {
        private var bluetoothAdapter: BluetoothAdapter? = null
        private fun getAdapter(context: Context) : BluetoothAdapter? {
            if (bluetoothAdapter==null) {
                bluetoothAdapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
            }
            return bluetoothAdapter
        }

        private lateinit var bluetoothLeScanner: BluetoothLeScanner
        private lateinit var scanCallback: ScanCallback
        fun startScan(context: Context, callback: ScanCallback) {
            val adapter = getAdapter(context)
            if (adapter==null || !adapter.isEnabled) {
                Toast.makeText(context, "DEVICE NOT SUPPORT BLE!!!", Toast.LENGTH_LONG).show()
                return
            } else {
                bluetoothLeScanner = adapter.bluetoothLeScanner
                bluetoothLeScanner.startScan(callback)
                scanCallback = callback
            }
        }

        fun stopScan() {
            bluetoothLeScanner.stopScan(scanCallback)
        }

        const val ACTION_GATT_CONNECTED = "ACTION_GATT_CONNECTED"
        const val ACTION_GATT_DISCONNECTED = "ACTION_GATT_DISCONNECTED"
        const val STATE_DISCONNECTED = 0
        const val STATE_CONNECTING = 1
        const val STATE_CONNECTED = 2
        private var connectionState = STATE_DISCONNECTED
        fun connectBLE(
            context: Context,
            device: BluetoothDevice,
            connectedCallback: ()->Unit,
            disconnectedCallback: ()->Unit
        ) : BluetoothGatt? {
            var bluetoothGatt: BluetoothGatt? = null
            bluetoothGatt = device.connectGatt(context, false, object : BluetoothGattCallback() {
                override fun onConnectionStateChange(
                    gatt: BluetoothGatt,
                    status: Int,
                    newState: Int
                ) {
                    val intentAction: String
                    when (newState) {
                        BluetoothProfile.STATE_CONNECTED -> {
                            stopScan()
                            intentAction = ACTION_GATT_CONNECTED
                            connectionState = STATE_CONNECTED
                            Timber.i("Connected to GATT server.")
                            Timber.i("Attempting to start service discovery: ${bluetoothGatt?.discoverServices()}")
                            connectedCallback()
                        }

                        BluetoothProfile.STATE_DISCONNECTED -> {
                            intentAction = ACTION_GATT_DISCONNECTED
                            connectionState = STATE_DISCONNECTED
                            Timber.i("Disconnected from GATT server.")
                            Timber.w("device disconnected!!!")
                            disconnectedCallback()
                        }
                    }
                }

                // New services discovered
                override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                    Timber.w("onServicesDiscovered received: $status")
                }

                // Result of a characteristic read operation
                override fun onCharacteristicRead(
                    gatt: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                    status: Int
                ) {
                    Timber.i("onCharacteristicRead")
                    when (status) {
                        BluetoothGatt.GATT_SUCCESS -> {
//                    broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic)
                        }
                    }
                }
            })
            return bluetoothGatt
        }

        const val ACTION_GATT_SERVICES_DISCOVERED = "com.example.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED"
        const val ACTION_DATA_AVAILABLE = "com.example.bluetooth.le.ACTION_DATA_AVAILABLE"
        const val EXTRA_DATA = "com.example.bluetooth.le.EXTRA_DATA"

        const val SERVICE = "0000fff0-0000-1000-8000-00805f9b34fb"
        const val DESCRIPTOR = "0000fff4-0000-1000-8000-00805f9b34fb"
        const val CHARACTERISTIC_WRITE = "0000fff3-0000-1000-8000-00805f9b34fb"
        const val CHARACTERISTIC_READ = "0000fff2-0000-1000-8000-00805f9b34fb"
    }
}