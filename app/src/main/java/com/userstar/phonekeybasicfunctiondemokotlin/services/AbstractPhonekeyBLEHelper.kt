package com.userstar.phonekeybasicfunctiondemokotlin.services

import android.bluetooth.BluetoothGattCharacteristic

abstract class AbstractPhonekeyBLEHelper {
    abstract var receiveCallback: (BluetoothGattCharacteristic) -> Unit
    abstract fun write(data: ByteArray) : Boolean
}