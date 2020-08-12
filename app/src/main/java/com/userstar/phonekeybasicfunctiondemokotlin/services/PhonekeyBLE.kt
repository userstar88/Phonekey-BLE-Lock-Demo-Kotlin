package com.userstar.phonekeybasicfunctiondemokotlin.services

import android.bluetooth.BluetoothGattCharacteristic
import timber.log.Timber

class PhonekeyBLE {

    companion object {
        private var instance: PhonekeyBLE? = null
        fun getInstance() : PhonekeyBLE {
            if (instance==null) {
                instance = PhonekeyBLE()
                instance!!.bluetoothHelper = BLEHelper.getInstance()
            }
            return instance as PhonekeyBLE
        }
    }

    private lateinit var bluetoothHelper: AbstractPhonekeyBLEHelper

//    fun setCustomBLEHelper(bleHelper: BLEHelper) {
//        val byteArrayData = characteristic.value as ByteArray
//
//        Timber.i("received: %s", byteArrayData.toHex())
//        bluetoothHelper = bleHelper
//    }

    fun checkActivation(callback: (Boolean) -> Unit) {
        bluetoothHelper.receiveCallback = { gattCharacteristic: BluetoothGattCharacteristic ->
            val byteArray = gattCharacteristic.value as ByteArray
            val data = byteArray.toHex()
            val isActive = data.substring(4, 6) == "01"
            callback(isActive)
        }
        Timber.i("write 0100")
        Timber.i("%s", "0100".toCustomHexByteArray())

        bluetoothHelper.write("0100".toCustomHexByteArray())
    }

//    fun checkLockerStatus(callback: (String, String, String) -> Unit) {
//        bluetoothHelper.write("0399")
//            .registerObserver { receivedString ->
//                val battery = receivedString.substring(6, 8)
//                val version = receivedString.substring(10, 12)
//                val door = receivedString.substring(14, 16)
//                callback(battery, version, door)
//            }
//    }

    private fun String.toCustomHexByteArray() : ByteArray {
        // Calculate data length and insert in head of data
        val length: Int = if (length % 2 != 0) {
            length / 2 + 1
        } else {
            length / 2
        }
        val stringBuilder = StringBuilder()
        stringBuilder.append(length)
        if (stringBuilder.length < 2) {
            stringBuilder.insert(0, '0')
        }
        val dataWithLength = stringBuilder.append(this).toString()
        Timber.i("dataWithLength: %s", dataWithLength)

        val HEX_CHARS = "0123456789ABCDEF"
        val result = ByteArray(dataWithLength.length / 2)
        for (i in dataWithLength.indices step 2) {
            val firstIndex = HEX_CHARS.indexOf(dataWithLength[i]);
            val secondIndex = HEX_CHARS.indexOf(dataWithLength[i + 1]);
            val octet = firstIndex.shl(4).or(secondIndex)
            result[i.shr(1)] = octet.toByte()
        }
        return result
    }

    private fun ByteArray.toHex(): String {
        return joinToString("") { "%02x".format(it) }
    }
}