package com.userstar.phonekeybasicfunctiondemokotlin.views

import android.bluetooth.le.ScanResult
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.userstar.phonekeybasicfunctiondemokotlin.R
import com.userstar.phonekeybasicfunctiondemokotlin.services.PhonekeyBLE
import kotlinx.android.synthetic.main.device_fragment.*
import timber.log.Timber

class DeviceFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.device_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val scanResult = requireArguments()["scanResult"] as ScanResult
        check_lock_status_Button.setOnClickListener {

            //
            PhonekeyBLE.getInstance().checkActivation {

            }
        }
    }
}