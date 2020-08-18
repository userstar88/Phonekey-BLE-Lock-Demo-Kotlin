package com.userstar.phonekeybasicfunctiondemokotlin.views

import android.Manifest
import android.bluetooth.le.ScanResult
import android.content.pm.PackageManager
import android.nfc.tech.NfcV
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.observe
import com.userstar.phonekeybasicfunctiondemokotlin.R
import com.userstar.phonekeybasicfunctiondemokotlin.services.BLEHelper
import com.userstar.phonekeybasicfunctiondemokotlin.services.PhonekeyBLELock
import kotlinx.android.synthetic.main.device_fragment.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import timber.log.Timber
import kotlin.math.E

class DeviceFragment : Fragment() {

    override fun onStart() {
        super.onStart()

        EventBus.getDefault().register(this)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.device_fragment, container, false)
    }


    lateinit var scanResult: ScanResult
    val defaultPassword ="1111111111111111"
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        scanResult = requireArguments()["scanResult"] as ScanResult

        zxing_BarcodeView.decodeSingle { barcodeResult ->
            qrCodeLiveData!!.postValue(barcodeResult.toString())
            zxing_BarcodeView.pause()
            zxing_BarcodeView.visibility = View.INVISIBLE
        }

        check_lock_status_Button.setOnClickListener {
//            activateByNfc()
            activateByQRCode()
//            PhonekeyBLELock.getInstance().deactivate(defaultPassword) { status, errorTimes, needToWait ->
//
//            }
        }

        // Init your bluetooth class with PhonykeyBLELock first
        // Your bluetooth have to implement AstractPhonekeyBLEHelper
        PhonekeyBLELock.getInstance()
            .setBLEHelper(BLEHelper.getInstance())
            .showLog(true)

        PhonekeyBLELock.getInstance().isActive {
            Timber.i("isActive: $it")
        }
    }

    override fun onPause() {
        super.onPause()
        if (zxing_BarcodeView.visibility == View.VISIBLE) {
            zxing_BarcodeView.pause()
            zxing_BarcodeView.visibility = View.INVISIBLE
        }
    }

    override fun onStop() {
        super.onStop()
        EventBus.getDefault().unregister(this)
        BLEHelper.getInstance().disConnectBLE()
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

    var tagLiveData: MutableLiveData<NfcV>? = null
    fun activateByNfc() {
        val editText = EditText(requireContext())
        var alertDialog: AlertDialog? = null
        var masterPassword: String? = null
        AlertDialog.Builder(requireContext())
                
            .setTitle("Set master password")
            .setView(getAlertDialogEditView(editText))
            .setCancelable(false)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Confirm") { _, _ ->
                masterPassword = editText.text.toString()
                if (masterPassword!!.length < 16) {
                    AlertDialog.Builder(requireContext())
                        .setMessage("Length must be > 16")
                        .setNegativeButton("Dismiss", null)
                        .show()
                } else {
                    alertDialog = AlertDialog.Builder(requireContext())
                        .setMessage("Scan NFC Tag")
                        .setOnCancelListener {
                            tagLiveData = null
                        }
                        .setPositiveButton("Cancel", null)
                        .show()
                }
            }
            .show()

        tagLiveData = MutableLiveData<NfcV>().apply {
            observe(viewLifecycleOwner) { nfcV ->
                tagLiveData = null
                alertDialog!!.dismiss()
                PhonekeyBLELock.getInstance().activity(nfcV, scanResult.device.name, masterPassword!!) { codeStatus, times, needToWait ->

                }
            }
        }
    }

    var qrCodeLiveData: MutableLiveData<String>? = null
    fun activateByQRCode() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 0)
        } else {
            var masterPassword: String?
            val editText = EditText(requireContext())
            AlertDialog.Builder(requireContext())
                .setTitle("Set master password")
                .setView(getAlertDialogEditView(editText))
                .setCancelable(false)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Confirm") { _, _ ->
                    masterPassword = editText.text.toString()
                    if (masterPassword!!.length < 16) {
                        AlertDialog.Builder(requireContext())
                            .setMessage("Length must be > 16")
                            .setNegativeButton("Dismiss", null)
                            .show()
                    } else {
                        zxing_BarcodeView.visibility = View.VISIBLE
                        zxing_BarcodeView.resume()
                    }
                }
                .show()

            qrCodeLiveData = MutableLiveData<String>().apply {
                observe(viewLifecycleOwner) { code ->
                    Timber.i("QR code: $code")
                    qrCodeLiveData = null
                    PhonekeyBLELock.getInstance().activate(code, defaultPassword, scanResult.device.name) { status, errorTimes, needToWait ->

                    }
                }
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onReceiveNFCTag(nfcV: NfcV) {
        Timber.i("receive NFC V tag")
        if (tagLiveData!=null) {
            tagLiveData!!.postValue(nfcV)
        }
    }

    private fun getAlertDialogEditView(editText: EditText): View {
        val linearLayout = LinearLayout(requireContext())
        val parms = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        )
        linearLayout.orientation = LinearLayout.VERTICAL
        linearLayout.layoutParams = parms
        linearLayout.gravity = Gravity.CENTER_HORIZONTAL;

        val layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        )
        layoutParams.marginStart = 30
        layoutParams.marginEnd = 30
        editText.layoutParams = layoutParams
        editText.gravity = Gravity.CENTER_HORIZONTAL
        editText.setText(defaultPassword)

        linearLayout.addView(editText)
        return linearLayout
    }
}