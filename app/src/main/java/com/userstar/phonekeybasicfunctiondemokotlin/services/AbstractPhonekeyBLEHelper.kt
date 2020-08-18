package com.userstar.phonekeybasicfunctiondemokotlin.services

import android.bluetooth.BluetoothGattCharacteristic


/* Implement this abstract class with you own Bluetooth class
*
*
*
*/
abstract class AbstractPhonekeyBLEHelper {
    abstract var callback: (BluetoothGattCharacteristic) -> Unit
    abstract fun write(data: ByteArray, callback: (BluetoothGattCharacteristic) -> Unit)
}