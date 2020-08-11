package com.userstar.phonekeybasicfunctiondemokotlin.utilities

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context

class BluetoothHelper() {

    private var adapter: BluetoothAdapter? = null
    fun getAdapter(context: Context): BluetoothAdapter {
        if (adapter==null) {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            adapter = bluetoothManager.adapter
        }
        return adapter!!
    }
}