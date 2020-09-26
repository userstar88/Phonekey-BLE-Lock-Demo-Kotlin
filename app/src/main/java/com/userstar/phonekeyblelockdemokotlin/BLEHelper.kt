package com.userstar.phonekeyblelockdemokotlin

import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.bluetooth.le.ScanSettings.*
import android.content.Context
import android.os.Build
import android.widget.Toast
import com.userstar.phonekeyblelock.AbstractPhonekeyBLEHelper
import com.userstar.phonekeyblelock.PhonekeyBLEHelper
import org.greenrobot.eventbus.EventBus
import timber.log.Timber
import java.util.*

/**
 * This is a simple BLEHelper example class
 *
 * @see AbstractPhonekeyBLEHelper
 * */
class BLEHelper : PhonekeyBLEHelper() {

    companion object {
        @JvmStatic
        private var instance: BLEHelper? = null
        fun getInstance() : BLEHelper {
            if (instance == null) {
                instance = BLEHelper()
            }
            return instance as BLEHelper
        }
    }

    private var adapter: BluetoothAdapter? = null
    private lateinit var bluetoothLeScanner: BluetoothLeScanner
    private lateinit var scanCallback: ScanCallback
    var isScanning = false
    fun startScan(
        context: Context,
        nameFilter: Array<String>?,
        callback: ScanCallback
    ) {
        adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (adapter==null || !adapter!!.isEnabled) {
            Toast.makeText(context, "THIS DEVICE DOES NOT SUPPORT BLE!!!", Toast.LENGTH_LONG).show()
        } else {
            isScanning = true
            bluetoothLeScanner = adapter!!.bluetoothLeScanner
            scanCallback = callback
            if (nameFilter==null) {
                Timber.i("start scan")
                bluetoothLeScanner.startScan(scanCallback)
            } else {
                val scanFilterArray = List<ScanFilter>(nameFilter.size) { index ->
                    ScanFilter.Builder().apply {
                        setDeviceName(nameFilter[index])
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
        isScanning = false
    }

    override var bluetoothGatt: BluetoothGatt? = null
    fun connectBLE(
        context: Context,
        lock: BluetoothDevice,
        connectedCallback: ()->Unit
    ) {
        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(
                gatt: BluetoothGatt,
                status: Int,
                newState: Int
            ) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        Timber.i("Connected to GATT server.")
                        Timber.i("Attempting to start service discovery: ${bluetoothGatt?.discoverServices()}")
                        stopScan()
                    }

                    BluetoothProfile.STATE_DISCONNECTED -> {
                        Timber.w("Disconnected from GATT server.")
                        Timber.w("Lock disconnected!!!")
                        EventBus.getDefault().post("Disconnected.")
                    }
                }
            }

            // New services discovered
            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                Timber.i("services discovered: $status")
                bluetoothGatt = gatt
                if (findCharacteristic(bluetoothGatt!!)) {
                    connectedCallback()
                }
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic?
            ) {
                super.onCharacteristicChanged(gatt, characteristic)
                if (characteristic!=null) {
                    read(characteristic.value)
                } else {
                    Timber.w("characteristic null")
                }
            }
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            bluetoothGatt = lock.connectGatt(context, false, callback)
        } else {
            bluetoothGatt = lock.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        }
    }

    override fun write(data: ByteArray, callback: (ByteArray) -> Unit) {
        Thread.sleep(10)
        super.write(data, callback)
    }

    fun disConnectBLE() {
        bluetoothGatt!!.disconnect()
        bluetoothGatt!!.close()
        bluetoothGatt = null
    }
}