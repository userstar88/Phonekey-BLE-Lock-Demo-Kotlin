package com.userstar.phonekeyblelock

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.util.Log
import androidx.annotation.CallSuper
import org.jetbrains.annotations.NotNull
import java.util.*

private const val TAG = "PhonekeyBLEHelper"

open class PhonekeyBLEHelper {

    open var bluetoothGatt: BluetoothGatt? = null
    private var gattCharacteristicWrite: BluetoothGattCharacteristic? = null

    fun findCharacteristic(@NotNull gatt: BluetoothGatt): Boolean {
        val gattService = gatt.getService(UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb"))
        if (gattService == null) {
            Log.e(TAG, "Can't not find PhonekeyBLE services.")
            return false
        }

        gattCharacteristicWrite = gattService.getCharacteristic(UUID.fromString("0000fff3-0000-1000-8000-00805f9b34fb"))
        if (gattCharacteristicWrite==null) {
            Log.e(TAG, "Can't not get characteristic for writing.")
            return false
        }

        val gattCharacteristicNotify = gattService.getCharacteristic(UUID.fromString("0000fff4-0000-1000-8000-00805f9b34fb"))
        if (gattCharacteristicNotify==null) {
            Log.e(TAG, "Can't not get characteristic for notifying.")
            return false
        }

        if (!gatt.setCharacteristicNotification(gattCharacteristicNotify, true)) {
            Log.e(TAG, "Can't not set notifying characteristic failed.")
            return false
        }
        return true
    }

    @CallSuper
    open fun write(data: ByteArray, callback: (ByteArray) -> Unit) {
        this.listener = callback
        gattCharacteristicWrite!!.value = data
        Timer().schedule(object : TimerTask() {
            override fun run() {
                bluetoothGatt?.writeCharacteristic(gattCharacteristicWrite!!)
            }
        }, 100)
    }

    private lateinit var listener: (ByteArray) -> Unit
    fun read(data: ByteArray) {
        listener(data)
    }
}