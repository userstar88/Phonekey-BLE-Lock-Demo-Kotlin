package com.userstar.phonekeybasicfunctiondemokotlin.services

import android.bluetooth.BluetoothGattCharacteristic

abstract class AbstractPhonekeyBLEHelper {
    abstract var receiveListener: (BluetoothGattCharacteristic) -> Unit
    abstract fun write(data: ByteArray) : AbstractPhonekeyBLEHelper
    fun setOnReceiveListener(listener: (BluetoothGattCharacteristic) -> Unit) {
        receiveListener = listener
    }
}