package com.userstar.phonekeybasicfunctiondemokotlin.services

import android.bluetooth.BluetoothGattCharacteristic


/* Implement this abstract class with you own Bluetooth class
*
*  Your BLE class must contain this two part
*    function write is for send data to Lock
*    variable callback is for receive data from Lock
*/
abstract class AbstractPhonekeyBLEHelper {
    /*
    *   In your write function, you have to at least implement these three lines
    *       1. assign the callback
    *       2. set the gatt characteristic
    *       3. write gatt characteristic with BluetoothGatt instance
    *
    *   this.callback = callback
    *   gattCharacteristicWrite!!.value = data
    *   bluetoothGatt!!.writeCharacteristic(gattCharacteristicWrite!!)
    */
    abstract fun write(data: ByteArray, callback: (BluetoothGattCharacteristic) -> Unit)

    /*
    *   Put this callback in your function where usually will read characteristic
    *   The service of lock for receiving data is under notify mode.
    *   So mostly, you can put it in onCharacteristicChanged()
    * */
    abstract var callback: (BluetoothGattCharacteristic) -> Unit
}