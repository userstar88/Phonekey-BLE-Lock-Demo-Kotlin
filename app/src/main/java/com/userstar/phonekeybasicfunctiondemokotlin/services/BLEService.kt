package com.userstar.phonekeybasicfunctiondemokotlin.services

import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_LATENCY
import android.content.Context
import android.widget.Toast
import timber.log.Timber
import java.util.*

class BLEHelper {

    companion object {
        @JvmStatic
        private var instance: BLEHelper? = null
        fun getInstance() : BLEHelper{
            if (instance==null) {
                instance = BLEHelper()
            }
            return instance as BLEHelper
        }

        private val UUID_SERVICE: UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
        private val UUID_CHARACTERISTIC_WRITE: UUID = UUID.fromString("0000fff3-0000-1000-8000-00805f9b34fb")
        private val UUID_CHARACTERISTIC_NOTIFY: UUID = UUID.fromString("0000fff4-0000-1000-8000-00805f9b34fb")
    }

    private var bluetoothAdapter: BluetoothAdapter? = null
    private fun getAdapter(context: Context) : BluetoothAdapter? {
        if (bluetoothAdapter==null) {
            bluetoothAdapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        }
        return bluetoothAdapter
    }

    private lateinit var bluetoothLeScanner: BluetoothLeScanner
    private lateinit var scanCallback: ScanCallback

    fun startScan(
        context: Context,
        nameFilter: Array<String>?,
        callback: ScanCallback
    ) {
        val adapter = getAdapter(context)
        if (adapter==null || !adapter.isEnabled) {
            Toast.makeText(context, "DEVICE NOT SUPPORT BLE!!!", Toast.LENGTH_LONG).show()
            return
        } else {
            bluetoothLeScanner = adapter.bluetoothLeScanner
            scanCallback = callback
            if (nameFilter==null) {
                Timber.i("start scan")
                bluetoothLeScanner.startScan(scanCallback)
            } else {
                val scanFilterArray = List<ScanFilter>(nameFilter.size) {
                    ScanFilter.Builder().apply {
                        setDeviceName(nameFilter[it])
                    }.build()
                }
                val setting = ScanSettings.Builder().apply {
                    setReportDelay(0)
                    setScanMode(SCAN_MODE_LOW_LATENCY)
                }.build()
                Timber.i("start scan with filter")
                bluetoothLeScanner.startScan(scanFilterArray, setting, callback)
            }
        }
    }

    fun stopScan() {
        bluetoothLeScanner.stopScan(scanCallback)
    }

    var bluetoothGatt: BluetoothGatt? = null
    var gattCharacteristicWrite: BluetoothGattCharacteristic? = null
    var gattCharacteristicNotify: BluetoothGattCharacteristic? = null
    fun connectBLE(
        context: Context,
        device: BluetoothDevice,
        connectedCallback: ()->Unit,
        disconnectedCallback: ()->Unit
    ) {
        bluetoothGatt = device.connectGatt(context, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(
                gatt: BluetoothGatt,
                status: Int,
                newState: Int
            ) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        stopScan()
                        Timber.i("Connected to GATT server.")
                        Timber.i("Attempting to start service discovery: ${bluetoothGatt?.discoverServices()}")
                    }

                    BluetoothProfile.STATE_DISCONNECTED -> {
                        Timber.w("Disconnected from GATT server.")
                        Timber.w("device disconnected!!!")
                        disconnectedCallback()
                    }
                }
            }

            // New services discovered
            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                Timber.i("services discovered: $status")
                bluetoothGatt = gatt

                val gattService = bluetoothGatt!!.getService(UUID_SERVICE)
                if (gattService == null) {
                    Timber.e("UUID_SERVICE service didn't find")
                    return
                }

                gattCharacteristicWrite = gattService.getCharacteristic(UUID_CHARACTERISTIC_WRITE)
                if (gattCharacteristicWrite==null) {
                    Timber.e("Characteristic for writing didn't find")
                    return
                }

                gattCharacteristicNotify = gattService.getCharacteristic(UUID_CHARACTERISTIC_NOTIFY)
                if (gattCharacteristicNotify==null) {
                    Timber.e("Characteristic for notifying didn't find")
                    return
                }

                if (!bluetoothGatt!!.setCharacteristicNotification(gattCharacteristicNotify, true)) {
                    Timber.e("Notification error")
                    return
                }

                connectedCallback()
            }

//            override fun onCharacteristicWrite(
//                gatt: BluetoothGatt?,
//                characteristic: BluetoothGattCharacteristic?,
//                status: Int
//            ) {
//                super.onCharacteristicWrite(gatt, characteristic, status)
//                if (characteristic != null) {
//                    Timber.i("characteristic uuid: %s", characteristic.uuid)
//                    Timber.i("characteristic uuid: %s", String(characteristic.value))
//                }
//            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic?
            ) {
                super.onCharacteristicChanged(gatt, characteristic)
                Timber.i("characteristic notified")
                if (characteristic!=null) {
                    read(characteristic)
                } else {
                    Timber.w("characteristic null")
                }
            }
        })
    }

    fun write(data: String) {
        // Calculate data length and insert in head of data
        val length: Int = if (data.length % 2 != 0) {
            data.length / 2 + 1
        } else {
            data.length / 2
        }
        val stringBuilder = StringBuilder()
        stringBuilder.append(length)
        if (stringBuilder.length < 2) {
            stringBuilder.insert(0, '0')
        }

        val finalData = stringBuilder.append(data).toString()
        gattCharacteristicWrite!!.value = finalData.toHexByteArray()
        val isSuccess = if (bluetoothGatt!!.writeCharacteristic(gattCharacteristicWrite!!)) "Successfully" else "Failed to"
        Timber.i("$isSuccess write %s", finalData)
    }

    private fun String.toHexByteArray() : ByteArray {
        val HEX_CHARS = "0123456789ABCDEF"
        val result = ByteArray(length / 2)
        for (i in 0 until length step 2) {
            val firstIndex = HEX_CHARS.indexOf(this[i]);
            val secondIndex = HEX_CHARS.indexOf(this[i + 1]);
            val octet = firstIndex.shl(4).or(secondIndex)
            result[i.shr(1)] = octet.toByte()
        }
        return result
    }

    private fun read(characteristic: BluetoothGattCharacteristic) {
        val byteArrayData = characteristic.value as ByteArray
        Timber.i("%s", byteArrayData.toHex())
    }


    private fun ByteArray.toHex(): String {
        return joinToString("") { "%02x".format(it) }
    }
}