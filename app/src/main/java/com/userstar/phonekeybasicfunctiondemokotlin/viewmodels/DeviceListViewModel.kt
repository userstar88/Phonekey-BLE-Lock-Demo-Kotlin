package com.userstar.phonekeybasicfunctiondemokotlin.viewmodels


import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.le.ScanResult
import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.userstar.phonekeybasicfunctiondemokotlin.repositories.DeviceListRepository

class DeviceListViewModel : ViewModel() {

    private val repository = DeviceListRepository()

    val bleDevice = MutableLiveData<ScanResult>()
    fun getDevice(context: Context) {
        repository.getDevice(context) { scanResult ->
            bleDevice.postValue(scanResult)
        }
    }

    fun connectBLE(
        context: Context,
        device: BluetoothDevice,
        callbackConnected: () -> Unit,
        callbackDisConnected: () -> Unit
    ) {
        repository.connectBLE(context, device, callbackConnected, callbackDisConnected)
    }

    class DeviceListViewModeFactory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel?> create(modelClass: Class<T>): T {
            return DeviceListViewModel() as T
        }
    }
}