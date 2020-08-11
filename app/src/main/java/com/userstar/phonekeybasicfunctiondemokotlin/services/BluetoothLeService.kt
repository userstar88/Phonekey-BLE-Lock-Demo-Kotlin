package com.userstar.phonekeybasicfunctiondemokotlin.services

import android.app.Service
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import timber.log.Timber

private const val STATE_DISCONNECTED = 0
private const val STATE_CONNECTING = 1
private const val STATE_CONNECTED = 2
const val ACTION_GATT_CONNECTED = "com.example.bluetooth.le.ACTION_GATT_CONNECTED"
const val ACTION_GATT_DISCONNECTED = "com.example.bluetooth.le.ACTION_GATT_DISCONNECTED"
const val ACTION_GATT_SERVICES_DISCOVERED = "com.example.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED"
const val ACTION_DATA_AVAILABLE = "com.example.bluetooth.le.ACTION_DATA_AVAILABLE"
const val EXTRA_DATA = "com.example.bluetooth.le.EXTRA_DATA"

const val USERSTAR_SERVICE = "0000fff0-0000-1000-8000-00805f9b34fb"
const val USERSTAR_DESCRIPTOR = "0000fff4-0000-1000-8000-00805f9b34fb"
const val USERSTAR_CHARACTERISTIC_WRITE = "0000fff3-0000-1000-8000-00805f9b34fb"
const val USERSTAR_CHARACTERISTIC_READ = "0000fff2-0000-1000-8000-00805f9b34fb"

// A service that interacts with the BLE device via the Android BLE API.
class BluetoothLeService(private var bluetoothGatt: BluetoothGatt?) : Service() {

    private var connectionState = STATE_DISCONNECTED

    // Various callback methods defined by the BLE API.
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(
            gatt: BluetoothGatt,
            status: Int,
            newState: Int
        ) {
            val intentAction: String
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    intentAction = ACTION_GATT_CONNECTED
                    connectionState = STATE_CONNECTED
                    Timber.i("Connected to GATT server.")
                    Timber.i("Attempting to start service discovery: ${bluetoothGatt?.discoverServices()}")
//                    broadcastUpdate(intentAction)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    intentAction = ACTION_GATT_DISCONNECTED
                    connectionState = STATE_DISCONNECTED
                    Timber.i("Disconnected from GATT server.")
//                    broadcastUpdate(intentAction)
                }
            }
        }

        // New services discovered
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            Timber.w("onServicesDiscovered received: $status")
//            when (status) {
//                BluetoothGatt.GATT_SUCCESS -> broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED)
//                else -> Log.w(TAG, "onServicesDiscovered received: $status")
//            }
        }

        // Result of a characteristic read operation
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            when (status) {
                BluetoothGatt.GATT_SUCCESS -> {
//                    broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic)
                }
            }
        }
    }

    inner class LocalBinder : Binder() {
        val service: BluetoothLeService
            get() = this@BluetoothLeService
    }

    private val mBinder: IBinder = LocalBinder()
    override fun onBind(p0: Intent?): IBinder? {
        return  mBinder
    }
}