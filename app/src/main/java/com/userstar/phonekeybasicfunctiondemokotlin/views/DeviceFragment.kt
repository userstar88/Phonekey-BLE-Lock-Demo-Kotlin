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
import androidx.navigation.fragment.findNavController
import com.squareup.okhttp.Callback
import com.squareup.okhttp.OkHttpClient
import com.squareup.okhttp.Request
import com.squareup.okhttp.Response
import com.userstar.phonekeybasicfunctiondemokotlin.R
import com.userstar.phonekeybasicfunctiondemokotlin.services.BLEHelper
import com.userstar.phonekeybasicfunctiondemokotlin.services.PhonekeyBLELock
import kotlinx.android.synthetic.main.device_fragment.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.json.JSONObject
import timber.log.Timber
import java.io.IOException
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


    private lateinit var scanResult: ScanResult
    private lateinit var deviceName: String
    private var alertDialog: AlertDialog? = null
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        scanResult = requireArguments()["scanResult"] as ScanResult
        deviceName = scanResult.device.name
        device_name_TextView.text = "Device Name: $deviceName"

        // Init your bluetooth class with PhonykeyBLELock first
        // Your bluetooth have to implement AstractPhonekeyBLEHelper
        PhonekeyBLELock.getInstance()
            .setBLEHelper(BLEHelper.getInstance())
            .showLog(true)

        updateLockStatus()

        activate_Button.setOnClickListener {
            alertDialog = AlertDialog.Builder(requireContext())
                .setNegativeButton("QR Code") { _, _ ->
                    getEnterMasterPasswordAlertDialog("Set your master password") { masterPassword ->
                        activateByQRCode(masterPassword)
                    }
                }
                .setPositiveButton("NFC") { _, _ ->
                    getEnterMasterPasswordAlertDialog("Set your master password") { masterPassword ->
                        activateByNfc(masterPassword)
                    }
                }
                .show()
        }

        deactivate_Button.setOnClickListener {
            alertDialog = AlertDialog.Builder(requireContext())
                .setMessage("This action will reset the lock and clear lock's memory")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Confirm") { _, _ ->
                    getEnterMasterPasswordAlertDialog("Master password") { masterPassword ->
                        PhonekeyBLELock.getInstance().deactivate(masterPassword, object : PhonekeyBLELock.DeactivateListener {
                            override fun onFailed(
                                status: PhonekeyBLELock.MasterPasswordStatus,
                                errorTimes: Int,
                                needToWait: Int
                            ) {
//                                TODO("Not yet implemented")
                            }

                            override fun onSuccess() {
//                                TODO("Not yet implemented")
                            }
                        })

                    }
                }
                .show()
        }

        establish_key_Button.setOnClickListener {
            establishKey()
        }

        open_Button.setOnClickListener {
            openLock()
        }

        reset_master_password_Button.setOnClickListener {
            getEnterMasterPasswordAlertDialog("New master password") { newMasterPassword ->
                resetMasterPassword(newMasterPassword)
            }
        }

        zxing_BarcodeView.decodeSingle { barcodeResult ->
            qrCodeLiveData!!.postValue(barcodeResult.toString())
            zxing_BarcodeView.pause()
            zxing_BarcodeView.visibility = View.INVISIBLE
        }
    }

    override fun onPause() {
        super.onPause()
        if (zxing_BarcodeView.visibility == View.VISIBLE) {
            zxing_BarcodeView.pause()
            zxing_BarcodeView.visibility = View.INVISIBLE
        }
        if (alertDialog!=null) {
            alertDialog!!.dismiss()
        }
    }

    override fun onStop() {
        super.onStop()
        BLEHelper.getInstance().disConnectBLE()
        EventBus.getDefault().unregister(this)
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

    private fun updateLockStatus() {
        // Update lock's status
        PhonekeyBLELock.getInstance().isActive {
            active_status_TextView.text = "isActive: $it"
            PhonekeyBLELock.getInstance().getStatus(object : PhonekeyBLELock.StatusListener {
                override fun onReceived(battery: String, version: String, isClosing: Boolean) {
                    battery_TextView.text = "Battery: $battery"
                    version_TextView.text = "Version: $version"
                    open_close_status_TextView.text = "isClosing: $isClosing"
                }
            })
        }
    }

    private var tagLiveData: MutableLiveData<NfcV>? = null
    private fun activateByNfc(masterPassword: String) {
        alertDialog = AlertDialog.Builder(requireContext())
            .setMessage("Scan NFC Tag")
                .setOnCancelListener {
                    tagLiveData = null
                }
            .setPositiveButton("Cancel", null)
            .show()

        tagLiveData = MutableLiveData<NfcV>().apply {
            observe(viewLifecycleOwner) { nfcV ->
                tagLiveData = null
                alertDialog!!.dismiss()
                PhonekeyBLELock.getInstance().activate(nfcV, scanResult.device.name, masterPassword, object : PhonekeyBLELock.ActivateListener {
                    override fun onFailed(
                        status: PhonekeyBLELock.ActivationCodeStatus,
                        errorTimes: Int,
                        needToWait: Int
                    ) {
//                        TODO("Not yet implemented")
                    }

                    override fun onSuccess() {
                        updateLockStatus()
                    }
                })
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

    private var qrCodeLiveData: MutableLiveData<String>? = null
    private fun activateByQRCode(masterPassword: String) {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 0)
        } else {
            zxing_BarcodeView.visibility = View.VISIBLE
            zxing_BarcodeView.resume()
            qrCodeLiveData = MutableLiveData<String>().apply {
                observe(viewLifecycleOwner) { code ->
                    Timber.i("QR code: $code")
                    qrCodeLiveData = null
                    PhonekeyBLELock.getInstance().activate(code, scanResult.device.name, masterPassword, object : PhonekeyBLELock.ActivateListener {
                        override fun onFailed(
                            status: PhonekeyBLELock.ActivationCodeStatus,
                            errorTimes: Int,
                            needToWait: Int
                        ) {
//                        TODO("Not yet implemented")
                        }

                        override fun onSuccess() {
                            updateLockStatus()
                        }
                    })
                }
            }
        }
    }

    private fun establishKey() {
        getEnterMasterPasswordAlertDialog("Master password") { masterPassword ->
            PhonekeyBLELock.getInstance().createKey(masterPassword, object : PhonekeyBLELock.CreateKeyListener {
                override fun onFailed(
                    status: PhonekeyBLELock.MasterPasswordStatus,
                    errorTimes: Int,
                    needToWait: Int
                ) {
                    // Do error handling
                }

                override fun onSuccess(keyA: String, keyB: String) {
                    // Store KeyA and KeyB and calculate AC3
                    // Example: http://210.65.11.172:8080/demo/Basketball/DetailLattices_Set.jsp?id=uscabpandroid&pw=userstar&lockid=&a=&b=

                    OkHttpClient().newCall(Request.Builder()
                        .url("http://210.65.11.172:8080/demo/Basketball/DetailLattices_Set.jsp?id=uscabpandroid&pw=userstar&lockid=${deviceName.substring(0, 3)}&a=$keyA&b=$keyB")
                        .build())
                        .enqueue(object : Callback {
                            override fun onFailure(request: Request?, e: IOException?) {
                                Timber.e("Save KeyA, KeyB to server error")
                            }

                            override fun onResponse(response: Response?) {
                                if (response!=null) {
                                    val jsonObject = JSONObject(response.body().string())
                                    Timber.i("response: $jsonObject")
                                    val status = jsonObject.getString("s")
                                    if (status == "01") {
                                        Timber.i("Save KeyA, KeyB to server SUCCESSFULLY")
                                    } else {
                                        Timber.e("Save KeyA, KeyB to server error")
                                    }
                                }
                            }
                        })
                }
            })
        }
    }

    private fun openLock() {
        PhonekeyBLELock.getInstance().getT1 {  T1 ->
            OkHttpClient().newCall(Request.Builder()
                .url("http://210.65.11.172:8080/demo/Basketball/Netkey_keydata_demo.jsp?id=uscabpandroid&pw=userstar&lockid=${deviceName.substring(3)}&t1=$T1")
                .build())
                .enqueue(object : Callback {
                    override fun onFailure(request: Request?, e: IOException?) {
                        Timber.e("Get AC3 error")
                    }

                    override fun onResponse(response: Response?) {
                        if (response!=null) {
                            val jsonObject = JSONObject(response.body().string())
                            Timber.i("response: $jsonObject")
                            val ac3 = jsonObject.getString("ac3")
                            val pk = jsonObject.getString("pk")
                            PhonekeyBLELock.getInstance().open(ac3, object : PhonekeyBLELock.OpenListener {
                                override fun onAC3Error() {
                                    TODO("Not yet implemented")
                                }

                                override fun onSuccess(keyB: String) {
                                    OkHttpClient().newCall(Request.Builder()
                                        .url("http://210.65.11.172:8080/demo/Basketball/Netkey_updatakeyb.jsp?id=uscabpandroid&pw=userstar&lockid=${deviceName.substring(3)}&b=$keyB&pk=$pk")
                                        .build())
                                        .enqueue(object : Callback {
                                            override fun onFailure(request: Request?, e: IOException?) {
                                                Timber.e("Update KeyB error")
                                            }

                                            override fun onResponse(response: Response?) {
                                                if (response!=null) {
                                                    Timber.i("Open and Update KeyB successfully")
                                                }
                                            }
                                        })
                                }
                            })
                        }
                    }
                })
        }
    }

    private fun resetMasterPassword(newMasterPassword: String) {
        alertDialog = AlertDialog.Builder(requireContext())
            .setMessage("Scan NFC Tag")
            .setOnCancelListener {
                tagLiveData = null
            }
            .setPositiveButton("Cancel", null)
            .show()

        tagLiveData = MutableLiveData<NfcV>().apply {
            observe(viewLifecycleOwner) { nfcV ->
                tagLiveData = null
                alertDialog!!.dismiss()

                PhonekeyBLELock.getInstance().resetMasterPassword(nfcV, deviceName, newMasterPassword, object : PhonekeyBLELock.ActivateListener {
                    override fun onFailed(
                        status: PhonekeyBLELock.ActivationCodeStatus,
                        errorTimes: Int,
                        needToWait: Int
                    ) {
//                        TODO("Not yet implemented")
                    }

                    override fun onSuccess() {
                        updateLockStatus()
                    }
                })
            }
        }
    }

    private fun getEnterMasterPasswordAlertDialog(title: String, callback: (String) -> Unit) {
        val editText = EditText(requireContext())
        alertDialog = AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setView(getAlertAlertDialogEditView(editText))
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Confirm") { _, _ ->
                if (editText.text.toString().length < 16) {
                    AlertDialog.Builder(requireContext())
                        .setMessage("Length must be > 16")
                        .setNegativeButton("Dismiss", null)
                        .show()
                } else {
                    callback(editText.text.toString())
                }
            }
            .setCancelable(false)
            .show()
    }

    private val defaultPassword ="1111111111111111"
    private fun getAlertAlertDialogEditView(editText: EditText): View {
        val linearLayout = LinearLayout(requireContext())
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        )
        linearLayout.orientation = LinearLayout.VERTICAL
        linearLayout.layoutParams = params
        linearLayout.gravity = Gravity.CENTER_HORIZONTAL

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