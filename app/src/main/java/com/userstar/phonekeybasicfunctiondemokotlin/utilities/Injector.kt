package com.userstar.phonekeybasicfunctiondemokotlin.utilities


import com.userstar.phonekeybasicfunctiondemokotlin.viewmodels.DeviceListViewModel

object Injector {

    fun provideDeviceListViewModelFactory() : DeviceListViewModel.DeviceListViewModeFactory {
        return DeviceListViewModel.DeviceListViewModeFactory()
    }
}