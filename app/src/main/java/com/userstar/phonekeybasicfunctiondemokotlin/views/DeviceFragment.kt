package com.userstar.phonekeybasicfunctiondemokotlin.views

import android.Manifest
import android.bluetooth.le.ScanResult
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.observe
import com.google.zxing.BarcodeFormat
import com.google.zxing.ResultPoint
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import com.userstar.phonekeybasicfunctiondemokotlin.R
import com.userstar.phonekeybasicfunctiondemokotlin.services.BLEHelper
import com.userstar.phonekeybasicfunctiondemokotlin.services.PhonekeyBLELock
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

        zxing_BarcodeView.setDecoderFactory(DefaultDecoderFactory(listOf(BarcodeFormat.QR_CODE)))
        zxing_BarcodeView.decodeContinuous(object : BarcodeCallback {
            override fun barcodeResult(result: BarcodeResult?) {
                cardNoLiveData.postValue(result.toString())
                zxing_BarcodeView.pause()
                zxing_BarcodeView.visibility = View.INVISIBLE
            }
            override fun possibleResultPoints(resultPoints: MutableList<ResultPoint>?) {}
        })

        check_lock_status_Button.setOnClickListener {
            val masterPassword = "1111111111111111"
            PhonekeyBLELock.getInstance().restore(masterPassword) { status, incorrectTimes, needToWait ->
                if (status == PhonekeyBLELock.RestoreInstructionStatus.SUCCESS) {
                    Timber.i("Success")
                } else {
                    Timber.i("failed")
                }
            }
        }

        // Init your bluetooth class with PhonykeyBLELock first
        // Your bluetooth have to implement AstractPhonekeyBLEHelper
        PhonekeyBLELock.getInstance()
            .setBLEHelper(BLEHelper.getInstance())
            .showLog(true)
    }

    override fun onPause() {
        super.onPause()
        if (zxing_BarcodeView.visibility == View.VISIBLE) {
            zxing_BarcodeView.pause()
            zxing_BarcodeView.visibility = View.INVISIBLE
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            zxing_BarcodeView.visibility = View.VISIBLE
            zxing_BarcodeView.resume()
        } else {
            check(false) { "Camera permission is not allowed" }
        }
    }

    var cardNoLiveData = MutableLiveData<String>()
    fun changeMasterPassword() {
        PhonekeyBLELock.getInstance().getT1 { T1 ->
            Timber.i("T1: $T1")
            requireActivity().runOnUiThread {
                zxing_BarcodeView.resume()
                zxing_BarcodeView.visibility = View.VISIBLE
            }
        }

        cardNoLiveData.observe(viewLifecycleOwner) { cardNo ->
            PhonekeyBLELock.getInstance().sendCardID(cardNo) {

            }
        }
    }
}