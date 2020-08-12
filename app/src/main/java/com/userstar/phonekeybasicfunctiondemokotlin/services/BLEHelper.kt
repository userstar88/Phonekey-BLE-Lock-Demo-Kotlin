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

class BLEHelper : AbstractPhonekeyBLEHelper() {

    companion object {
        @JvmStatic
        private var instance: BLEHelper? = null
        fun getInstance() : BLEHelper{
            if (instance==null) {
                instance = BLEHelper()
            }
            return instance as BLEHelper
        }

        private val UUID_LOST_SERVICE = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
        private val UUID_LOST_ENABLE = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")
        private val UUID_LOST_WRITE = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")

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

                val gattService = bluetoothGatt!!.getService(UUID_LOST_SERVICE)
                if (gattService == null) {
                    Timber.e("UUID_SERVICE service didn't find")
                    return
                }

                gattCharacteristicWrite = gattService.getCharacteristic(UUID_LOST_WRITE)
                if (gattCharacteristicWrite==null) {
                    Timber.e("Characteristic for writing didn't find")
                    return
                }

                gattCharacteristicNotify = gattService.getCharacteristic(UUID_LOST_ENABLE)
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

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic?
            ) {
                super.onCharacteristicChanged(gatt, characteristic)
                Timber.i("characteristic notified")
                if (characteristic!=null) {
                    receiveCallback(characteristic)
                } else {
                    Timber.w("characteristic null")
                }
            }
        })
    }

    override lateinit var receiveCallback: (BluetoothGattCharacteristic) -> Unit
    override fun write(data: ByteArray) :Boolean {
        gattCharacteristicWrite!!.value = data
        return bluetoothGatt!!.writeCharacteristic(gattCharacteristicWrite!!)

    }
}