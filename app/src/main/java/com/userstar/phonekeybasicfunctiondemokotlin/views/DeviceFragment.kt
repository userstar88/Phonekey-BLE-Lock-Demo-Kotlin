package com.userstar.phonekeybasicfunctiondemokotlin.views

import androidx.lifecycle.ViewModelProviders
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.userstar.phonekeybasicfunctiondemokotlin.viewmodels.DeviceViewModel
import com.userstar.phonekeybasicfunctiondemokotlin.R

class DeviceFragment : Fragment() {

    companion object {
        @JvmStatic
        fun newInstance() = DeviceFragment()
    }

    private lateinit var viewModel: DeviceViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.device_fragment, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel = ViewModelProviders.of(this).get(DeviceViewModel::class.java)
        // TODO: Use the ViewModel
    }

}