package com.userstar.phonekeyblelock

import android.bluetooth.BluetoothGattCharacteristic

/**
 * Implement this abstract class with you own Bluetooth class
 * Your BLE class must contain this two part:
 *
 *    write(ByteArray, (BluetoothGattCharacteristic) -> Unit), which is for send data to Lock
 *    callback, which is for receive data from Lock
 */
abstract class AbstractPhonekeyBLEHelper {

    /**
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

    /**
     *   This callback must be put in where bluetooth gatt characteristic notified
     *   The lock receives data through notify mode service
     *   Normally, it is BluetoothGattCallback.onCharacteristicChanged(BluetoothGatt?, BluetoothGattCharacteristic?)
     */
    abstract var callback: (BluetoothGattCharacteristic) -> Unit
}