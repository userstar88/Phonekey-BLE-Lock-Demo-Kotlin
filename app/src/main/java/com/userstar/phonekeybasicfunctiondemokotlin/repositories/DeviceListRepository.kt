package com.userstar.phonekeybasicfunctiondemokotlin.repositories

import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import com.userstar.phonekeybasicfunctiondemokotlin.services.BLEHelper
import timber.log.Timber

class DeviceListRepository {

    fun getDevice(context: Context, callback: (result: ScanResult)->Unit) {

        BLEHelper.getInstance().startScan(context, arrayOf("BKBFMLNAFBI") ,object : ScanCallback() {
            override fun onScanFailed(errorCode: Int) {
                super.onScanFailed(errorCode)
                Timber.i("failed: $errorCode")
            }

            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                super.onScanResult(callbackType, result)
                if (result != null && result.device.name!=null) {
                    Timber.i("discover name=${result.device.name}, address=${result.device.address}, rssi=${result.rssi}")
                    callback(result)
                }
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>?) {
                super.onBatchScanResults(results)
                Timber.i(results.toString())
            }
        })

    }

    fun connectBLE(
        context: Context,
        device: BluetoothDevice,
        callbackConnected: () -> Unit,
        callbackDisConnected: () -> Unit
    ) {
        BLEHelper.getInstance().connectBLE(context, device, callbackConnected, callbackDisConnected)
    }

}