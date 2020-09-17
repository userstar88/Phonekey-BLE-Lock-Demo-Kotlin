package com.userstar.phonekeyblelockdemokotlin.views

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.le.ScanResult
import android.content.Context
import android.nfc.NfcAdapter
import android.nfc.tech.NfcV
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.MutableLiveData
import com.journeyapps.barcodescanner.BarcodeView
import com.squareup.okhttp.Callback
import com.squareup.okhttp.OkHttpClient
import com.squareup.okhttp.Request
import com.squareup.okhttp.Response
import com.userstar.phonekeyblelockdemokotlin.BLEHelper
import com.userstar.phonekeyblelock.PhonekeyBLELock
import com.userstar.phonekeyblelock.PhonekeyBLELockObserver
import com.userstar.phonekeyblelockdemokotlin.R
import com.userstar.phonekeyblelockdemokotlin.checkPermission
import kotlinx.android.synthetic.main.lock_fragment.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber
import java.io.IOException
import java.util.*
import kotlin.concurrent.thread

private const val DEFAULT_LOCK_PASSWORD = "1111111111111111"
private const val DEFAULT_KEYPAD_PASSWORD = "666666"

@SuppressLint("SetTextI18n")
class LockFragment : Fragment(), PhonekeyBLELockObserver {

    override fun onStart() {
        super.onStart()
        EventBus.getDefault().register(this)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.lock_fragment, container, false)

        view.findViewById<ImageButton>(R.id.update_button).setOnClickListener { updateLockStatus() }
        view.findViewById<Button>(R.id.activate_by_qr_code_Button).setOnClickListener { checkPermission(requireActivity() as AppCompatActivity, Manifest.permission.CAMERA) { activateByQRCode() } }
        view.findViewById<Button>(R.id.activate_by_nfc_Button).setOnClickListener { activateByNFC() }
        view.findViewById<Button>(R.id.deactivate_Button).setOnClickListener { deactivate() }
        view.findViewById<Button>(R.id.check_device_password_Button).setOnClickListener { checkDevicePassword() }
        view.findViewById<Button>(R.id.change_device_password_Button).setOnClickListener { changeDevicePassword() }
        view.findViewById<Button>(R.id.reset_device_password_by_nfc_Button).setOnClickListener { resetDevicePasswordByNfc() }
        view.findViewById<Button>(R.id.establish_key_Button).setOnClickListener { establishKey() }
        view.findViewById<Button>(R.id.open_Button).setOnClickListener { openLock() }
        view.findViewById<Button>(R.id.set_keypad_password_Button).setOnClickListener {  setKeypadPassword() }
        view.findViewById<Button>(R.id.remove_keypad_password_Button).setOnClickListener { removeKeypadPassword() }
        view.findViewById<BarcodeView>(R.id.zxing_BarcodeView).decodeSingle { barcodeResult ->
            Timber.i("QR code: $barcodeResult")
            qrCodeLiveData!!.postValue(barcodeResult.toString())
            zxing_BarcodeView.pause()
            zxing_BarcodeView.visibility = View.INVISIBLE
        }

        return view
    }

    private lateinit var scanResult: ScanResult
    private lateinit var lockName: String
    private lateinit var phonekeyBLELock: PhonekeyBLELock
    private lateinit var lockType: PhonekeyBLELock.LockType
    private var isActive = false
    private var alertDialog: AlertDialog? = null
    private var communicationDialogFragment = CommunicationDialogFragment()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        scanResult = requireArguments()["scanResult"] as ScanResult
        lockName = scanResult.device.name
        lock_name_TextView.text = "Lock Name: $lockName"

        communicationDialogFragment.create(parentFragmentManager) {
            /**
             * Init your bluetooth class with PhonykeyBLELock first
             * Your bluetooth class must implement AbstractPhonekeyBLEHelper
             *
             * @see BLEHelper
             * */
            phonekeyBLELock = PhonekeyBLELock.Builder()
                .enableLog(true)
                .setLockName(lockName)
                .setBLEHelper(BLEHelper.getInstance())
                .setOnReadyListener { isActive: Boolean, type: PhonekeyBLELock.LockType, battery: String, version: String, isOpening: Boolean ->
                    refreshLockStatus(isActive, type, battery, version, isOpening)
                }
                .registerObserver(this)
                .build()
        }
    }

    override fun onPause() {
        super.onPause()
        if (zxing_BarcodeView.visibility == View.VISIBLE) {
            zxing_BarcodeView.pause()
            zxing_BarcodeView.visibility = View.INVISIBLE
        }
        alertDialog?.dismiss()
        communicationDialogFragment.hide()
    }

    override fun onStop() {
        super.onStop()
        BLEHelper.getInstance().disConnectBLE()
        EventBus.getDefault().unregister(this)
    }

    private fun updateLockStatus() {
        // Update lock's status
        phonekeyBLELock.getLockStatus(object : PhonekeyBLELock.LockStatusGetListener {
            override fun onReceive(
                isActive: Boolean,
                type: PhonekeyBLELock.LockType,
                battery: String,
                version: String,
                isOpening: Boolean
            ) {
                refreshLockStatus(isActive, type, battery, version, isOpening)
            }
        })
    }

    private fun refreshLockStatus(isActive: Boolean, type: PhonekeyBLELock.LockType, battery: String, version: String, isOpening: Boolean) {
        this.isActive = isActive
        this.lockType = type

        lock_type_TextView.text = "Lock Type: ${this.lockType}"
        is_active_TextView.text = "isActive: ${this.isActive}"
        battery_TextView.text = "Battery: $battery"
        version_TextView.text = "Version: $version"
        open_close_status_TextView.text = "isOpening: $isOpening"

//        communicationDialogFragment.hide(parentFragmentManager)
    }

    /*-------------------------Activation----------------------------------------------------*/
    private var qrCodeLiveData: MutableLiveData<String>? = null
    private fun activateByQRCode() {
        if (phonekeyBLELock.isActive()) {
            makeToastAndLog("Lock has been activated", 0)
        } else {
            checkPermission(requireActivity() as AppCompatActivity, Manifest.permission.CAMERA) { isGranted ->
                if (!isGranted) {
                    makeToastAndLog("Camera's permission is not allowed.", 0)
                } else {
                    zxing_BarcodeView.visibility = View.VISIBLE
                    zxing_BarcodeView.resume()
                    qrCodeLiveData = MutableLiveData<String>().apply {
                        observe(viewLifecycleOwner) { code ->
                            qrCodeLiveData!!.removeObservers(viewLifecycleOwner)
                            qrCodeLiveData = null
                            enterPasswordAlertDialog("Set your device password",
                                PasswordType.LOCK
                            ) { devicePassword ->
                                Timber.i("Set device password: $devicePassword")
                                communicationDialogFragment.show("Activate by QR Code")
                                phonekeyBLELock.activate(code, devicePassword, object : PhonekeyBLELock.ActivateListener {
                                    override fun onFailure(
                                        status: PhonekeyBLELock.ActivationCodeStatus,
                                        errorTimes: Int,
                                        needToWait: Int
                                    ) {
                                        when (status) {
                                            PhonekeyBLELock.ActivationCodeStatus.LENGTH_ERROR -> makeToastAndLog("QR Code length ERROR!!!", 0)
                                            PhonekeyBLELock.ActivationCodeStatus.INCORRECT -> makeToastAndLog("QR Code is incorrect $errorTimes times.", 0)
                                            PhonekeyBLELock.ActivationCodeStatus.INCORRECT_3_TIMES -> makeToastAndLog("QR Code is incorrect above 3 times, need to wait $needToWait minutes.", 0)
                                        }
                                    }

                                    override fun onSuccess() {
                                        makeToastAndLog("Activate successfully!", 1)
                                    }
                                })
                            }
                        }
                    }
                }
            }
        }
    }

    private var tagLiveData: MutableLiveData<NfcV>? = null
    private fun activateByNFC() {
        if (phonekeyBLELock.isActive()) {
            makeToastAndLog("Lock has been activated", 0)
        } else {
            if (NfcAdapter.getDefaultAdapter(requireContext()) != null) {
                enterPasswordAlertDialog("Set your device password",
                    PasswordType.LOCK
                ) { devicePassword ->
                    Timber.i("Set device password: $devicePassword")
                    alertDialog = AlertDialog.Builder(requireContext())
                        .setMessage("Scan NFC Tag")
                        .setPositiveButton("Cancel") { _, _ ->
                            tagLiveData!!.removeObservers(viewLifecycleOwner)
                            tagLiveData = null
                        }
                        .setCancelable(false)
                        .show()

                    tagLiveData = MutableLiveData<NfcV>().apply {
                        observe(viewLifecycleOwner) { nfcV ->
                            tagLiveData!!.removeObservers(viewLifecycleOwner)
                            tagLiveData = null
                            alertDialog!!.dismiss()

                            communicationDialogFragment.show("Activate by NFC")
                            phonekeyBLELock.activate(nfcV, devicePassword, object : PhonekeyBLELock.ActivateListener {
                                    override fun onFailure(
                                        status: PhonekeyBLELock.ActivationCodeStatus,
                                        errorTimes: Int,
                                        needToWait: Int
                                    ) {
                                        when (status) {
                                            PhonekeyBLELock.ActivationCodeStatus.LENGTH_ERROR -> makeToastAndLog("NFC Tag LOST!!!", 0)
                                            PhonekeyBLELock.ActivationCodeStatus.INCORRECT -> makeToastAndLog("NFC Tag is incorrect $errorTimes times.", 0)
                                            PhonekeyBLELock.ActivationCodeStatus.INCORRECT_3_TIMES -> makeToastAndLog("NFC Tag is incorrect above 3 times, need to wait $needToWait minutes.", 0)
                                        }
                                    }

                                    override fun onSuccess() {
                                        makeToastAndLog("Activate successfully!", 1)

                                    }
                                })
                        }
                    }
                }
            } else {
                makeToastAndLog("This lock does not support NFC", 0)
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onReceiveNFCTag(nfcV: NfcV) {
        tagLiveData?.postValue(nfcV)
    }

    private fun deactivate() {
        if (!phonekeyBLELock.isActive()) {
            makeToastAndLog("Activated Lock first!!!", 0)
        } else {
            alertDialog = AlertDialog.Builder(requireContext())
                .setTitle("Warning")
                .setMessage("This action will reset the lock and clear lock's memory.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Confirm") { _, _ ->
                    enterPasswordAlertDialog("Device password", PasswordType.LOCK) { devicePassword ->
                        communicationDialogFragment.show("Deactivate")
                        phonekeyBLELock.deactivate(devicePassword, object : PhonekeyBLELock.DeactivateListener {
                            override fun onFailure(
                                status: PhonekeyBLELock.DevicePasswordStatus,
                                errorTimes: Int,
                                needToWait: Int
                            ) {
                                when (status) {
                                    PhonekeyBLELock.DevicePasswordStatus.LENGTH_ERROR -> makeToastAndLog("Device password length error!!!", 0)
                                    PhonekeyBLELock.DevicePasswordStatus.INCORRECT -> makeToastAndLog("Device password is error $errorTimes times.", 0)
                                    PhonekeyBLELock.DevicePasswordStatus.INCORRECT_3_TIMES -> makeToastAndLog("Device password is error above 3 times, need to wait $needToWait minutes.", 0)
                                    else -> { makeToastAndLog("UNKNOWN ERROR.", 0) }
                                }
                            }

                            override fun onSuccess() {
                                makeToastAndLog("The lock has already reset.", 1)
                            }
                        })

                    }
                }
                .show()
        }
    }

    /*-------------------------Device Password------------------------------------------------------------*/
    private fun checkDevicePassword() {
        if (!phonekeyBLELock.isActive()) {
            makeToastAndLog("Activated Lock first!!!", 0)
        } else {
            Timber.i("Check device password")
            enterPasswordAlertDialog("Device password", PasswordType.LOCK) { devicePassword ->
                communicationDialogFragment.show("Check device password")
                phonekeyBLELock.checkDevicePassword(devicePassword, object : PhonekeyBLELock.CheckDevicePasswordListener {
                    override fun onResult(
                        status: PhonekeyBLELock.DevicePasswordStatus,
                        errorTimes: Int,
                        needToWait: Int
                    ) {
                        when (status) {
                            PhonekeyBLELock.DevicePasswordStatus.CORRECT -> makeToastAndLog("Device password is correct!!!", 0)
                            PhonekeyBLELock.DevicePasswordStatus.INCORRECT -> makeToastAndLog("Device password is incorrect $errorTimes times", 0)
                            PhonekeyBLELock.DevicePasswordStatus.INCORRECT_3_TIMES -> makeToastAndLog("Device password is incorrect above 3 times, need to wait $needToWait minutes", 0)
                        }
                    }
                })
            }
        }
    }

    private fun changeDevicePassword() {
        if (!phonekeyBLELock.isActive()) {
            makeToastAndLog("Activated Lock first!!!", 0)
        } else {
            Timber.i("Change device password")
            enterPasswordAlertDialog("Enter old device password", PasswordType.LOCK) { oldPassword ->
                Timber.i("Old device password: $oldPassword")
                enterPasswordAlertDialog("Enter new device password", PasswordType.LOCK) { newPassword ->
                    Timber.i("New device password: $newPassword")
                    communicationDialogFragment.show("Change device password")
                    phonekeyBLELock.changeDevicePassword(
                        oldPassword,
                        newPassword,
                        object : PhonekeyBLELock.ChangeDevicePasswordListener {
                            override fun onFailure(
                                status: PhonekeyBLELock.DevicePasswordStatus,
                                errorTimes: Int,
                                needToWait: Int
                            ) {
                                when (status) {
                                    PhonekeyBLELock.DevicePasswordStatus.INCORRECT -> makeToastAndLog("Device password is incorrect $errorTimes times", 0)
                                    PhonekeyBLELock.DevicePasswordStatus.INCORRECT_3_TIMES -> makeToastAndLog("Device password is incorrect above 3 times, need to wait $needToWait minutes", 0)
                                }
                            }

                            override fun onSuccess() {
                                makeToastAndLog("Set new password $newPassword successfully", 1)
                            }
                        }
                    )
                }
            }
        }
    }

    private fun resetDevicePasswordByNfc() {
        if (phonekeyBLELock.isActive()) {
            makeToastAndLog("Activated Lock first!!!", 0)
        } else {
            if (NfcAdapter.getDefaultAdapter(requireContext()) != null) {
                enterPasswordAlertDialog("New device password", PasswordType.LOCK) { newDevicePassword ->
                    Timber.i("New device password: $newDevicePassword")
                    alertDialog = AlertDialog.Builder(requireContext())
                        .setMessage("Scan NFC Tag")
                        .setOnCancelListener {
                            tagLiveData!!.removeObservers(viewLifecycleOwner)
                            tagLiveData = null
                        }
                        .setPositiveButton("Cancel", null)
                        .show()

                    tagLiveData = MutableLiveData<NfcV>().apply {
                        observe(viewLifecycleOwner) { nfcV ->
                            tagLiveData!!.removeObservers(viewLifecycleOwner)
                            tagLiveData = null
                            alertDialog!!.dismiss()

                            communicationDialogFragment.show("Reset device password")
                            phonekeyBLELock.resetDevicePassword(
                                nfcV,
                                newDevicePassword,
                                object : PhonekeyBLELock.ActivateListener {
                                    override fun onFailure(
                                        status: PhonekeyBLELock.ActivationCodeStatus,
                                        errorTimes: Int,
                                        needToWait: Int
                                    ) {
                                        when (status) {
                                            PhonekeyBLELock.ActivationCodeStatus.LENGTH_ERROR -> makeToastAndLog("NFC Tag LOST!!!", 0)
                                            PhonekeyBLELock.ActivationCodeStatus.INCORRECT -> makeToastAndLog("NFC Tag is incorrect $errorTimes times.", 0)
                                            PhonekeyBLELock.ActivationCodeStatus.INCORRECT_3_TIMES -> makeToastAndLog("NFC Tag is incorrect above 3 times, need to wait $needToWait minutes.", 0)
                                        }
                                    }

                                    override fun onSuccess() {
                                        makeToastAndLog("Reset device password successfully.", 1)
                                    }
                                }
                            )
                        }
                    }
                }
            } else {
                makeToastAndLog("This lock does not support NFC", 0)
            }
        }
    }

    /*-------------------------Operation------------------------------------------------------------------*/
    /**
     * In most cases, Key(KeyA and KeyB) only need to be established once
     * Properly storing the KeyA and KeyB, use this two value to calculate AC3 and do a opening or set keypad password
     */
    private fun establishKey() {
        if (!phonekeyBLELock.isActive()) {
            makeToastAndLog("Activated Lock first!!!", 0)
        } else {
            enterPasswordAlertDialog("Device password", PasswordType.LOCK) { devicePassword ->
                communicationDialogFragment.show("Establish key")
                phonekeyBLELock.establishKey(devicePassword, object : PhonekeyBLELock.EstablishKeyListener {
                    override fun onFailure(
                        status: PhonekeyBLELock.DevicePasswordStatus,
                        errorTimes: Int,
                        needToWait: Int
                    ) {
                        when (status) {
                            PhonekeyBLELock.DevicePasswordStatus.LENGTH_ERROR -> makeToastAndLog("Device password length ERROR!!!", 0)
                            PhonekeyBLELock.DevicePasswordStatus.INCORRECT -> makeToastAndLog("Device password is error $errorTimes times.", 0)
                            PhonekeyBLELock.DevicePasswordStatus.INCORRECT_3_TIMES -> makeToastAndLog("Device password is error above 3 times, need to wait $needToWait minutes.", 0)
                        }
                    }

                    override fun onSuccess(keyA: String, keyB: String) {
                        makeToastAndLog("Establish key successfully.", 1)
                        // Store KeyA and KeyB and calculate AC3
                        Timber.i("KeyA: $keyA")
                        Timber.i("KeyB: $keyB")
                        OkHttpClient().newCall(Request.Builder()
                            .url("http://210.65.11.172:8080/demo/Basketball/DetailLattices_Set.jsp?id=uscabpandroid&pw=userstar&lockid=${lockName.substring(3)}&a=$keyA&b=$keyB")
                            .build())
                            .enqueue(object : Callback {
                                override fun onFailure(request: Request?, e: IOException?) {
                                    makeToastAndLog("Save KeyA, KeyB to server ERROR.", 0)
                                }

                                override fun onResponse(response: Response?) {
                                    if (response!=null) {
                                        val jsonObject = JSONObject(response.body().string())
                                        Timber.i("response: $jsonObject")
                                        val status = jsonObject.getString("s")
                                        if (status == "01") {
                                            makeToastAndLog("Save KeyA, KeyB to server successfully.", 1)
                                        } else {
                                            makeToastAndLog("Save KeyA, KeyB to server ERROR.", 0)
                                        }
                                    }
                                }
                            })
                    }
                })

            }
        }
    }

    /**
     * Make sure key already established!!!
     * Or there are not KeyA and KeyB to calculate AC3
     *
     * In this example, we save KeyA and KeyB on ours Userstar server
     *
     */
    private var canOpen = true
    private var secs = 0
    private fun openLock() {
        if (!phonekeyBLELock.isActive()) {
            makeToastAndLog("Activated Lock first!!!", 0)
        } else {
            if (canOpen) {
                communicationDialogFragment.show("Open")
                phonekeyBLELock.getT1 { T1 ->
                    val response = OkHttpClient().newCall(Request.Builder()
                        .url("http://210.65.11.172:8080/demo/Basketball/Netkey_keydata_demo.jsp?id=uscabpandroid&pw=userstar&lockid=${lockName.substring(3)}&t1=$T1")
                        .build())
                        .execute()
                    Timber.i("$response")

                    if (response != null && response.isSuccessful) {
                        val jsonObject = JSONObject(response.body().string())
                        response.body().close()
                        Timber.i("response: $jsonObject")

                        try {
                            val ac3 = jsonObject.getString("ac3")
                            val pk = jsonObject.getString("pk")
                            phonekeyBLELock.open(ac3, object : PhonekeyBLELock.OpenListener {
                                override fun onAC3Error() {
                                    makeToastAndLog("AC3 ERROR, check KeyA, KeyB and AC3, or redo Establish Key.", 0)
                                }

                                override fun onSuccess(newKeyB: String, secs: Int) {
                                    makeToastAndLog("Open successfully, update keyB", 1)
                                    Timber.i("new keyB: $newKeyB")
                                    updateLockKeyB(newKeyB, pk)
                                    canOpen = false
                                    this@LockFragment.secs = secs
                                    val timer = Timer()
                                    timer.schedule(object : TimerTask() {
                                        override fun run() {
                                            if (this@LockFragment.secs > 0) {
                                                this@LockFragment.secs -= 1
                                            } else {
                                                canOpen = true
                                                timer.cancel()
                                            }
                                        }
                                    }, 0, 1000)
                                }
                            })
                        } catch (e: JSONException) {
                            makeToastAndLog("Failed to get AC3", 0)
                        }
                    } else {
                        makeToastAndLog("Get AC3 ERROR", 0)
                    }
                }
            } else {
                makeToastAndLog("Need to wait $secs secs", 1)
            }
        }
    }

    private fun updateLockKeyB(keyB: String, pk: String) {
        val response = OkHttpClient().newCall(Request.Builder()
            .url("http://210.65.11.172:8080/demo/Basketball/Netkey_updatakeyb.jsp?id=uscabpandroid&pw=userstar&lockid=${lockName.substring(3)}&b=$keyB&pk=$pk")
            .build())
            .execute()
        Timber.i("$response")
        Timber.i("response: ${JSONObject(response.body().string())}")
        response.body().close()

        if (response != null && response.isSuccessful) {
            makeToastAndLog("Open and Update KeyB successfully.", 1)
            phonekeyBLELock.updateLockKeyB {  verificationCode ->
                requireActivity().getPreferences(Context.MODE_PRIVATE)
                    .edit()
                    .putString("verificationCode", verificationCode)
                    .apply()
                makeToastAndLog("Update verification code successfully.", 1)
            }
        } else {
            makeToastAndLog("Update KeyB ERROR.", 0)
            phonekeyBLELock.getNewKeyB(object : PhonekeyBLELock.GetNewKeyBListener {
                override fun onFailure() {
                    makeToastAndLog("Failed above 3 times, can't get KeyB anymore, please use original keyB to unlock next time.", 0)
                }

                override fun onSuccess(newKeyB: String) {
                    updateLockKeyB(newKeyB, pk)
                }
            })
        }
    }

    /*-------------------------Keypad------------------------------------------------------------------------------------------*/
    /**
     * This two functions only work when the lock is with keypad
     *
     * @see setKeypadPassword
     * @see removeKeypadPassword
     */

    private fun setKeypadPassword() {
        if (!phonekeyBLELock.isActive()) {
            makeToastAndLog("Activated Lock first!!!", 0)
            setKeypadPassword()
        } else {
            Timber.i("Set keypad password.")
            communicationDialogFragment.show("Set keypad password")
            if (phonekeyBLELock.getLockType() != PhonekeyBLELock.LockType.NO_KEYPAD) {
                thread {
                    val response = OkHttpClient().newCall(Request.Builder()
                        .url("http://210.65.11.172:8080/demo/Basketball/Netkey_keydata_demo.jsp?id=uscabpandroid&pw=userstar&lockid=${lockName.substring(3)}&t1=00000000000000000000")
                        .build())
                        .execute()
                    Timber.i("$response")

                    if (response != null && response.isSuccessful) {
                        val jsonObject = JSONObject(response.body().string())
                        response.body().close()
                        Timber.i("response: $jsonObject")
                        val ac3 = jsonObject.getString("ac3")
                        enterPasswordAlertDialog("Set  keypad password",
                            PasswordType.KEYPAD
                        ) { keypadPassword ->
                            phonekeyBLELock.setKeypadPassword(ac3, keypadPassword, object : PhonekeyBLELock.SetKeypadPasswordListener{
                                override fun onFailure(error: PhonekeyBLELock.KeypadError) {
                                    when (error) {
                                        PhonekeyBLELock.KeypadError.AC3_ERROR -> makeToastAndLog("AC3 ERROR, check KeyA, KeyB and AC3, or redo Establish Key", 0)
                                        PhonekeyBLELock.KeypadError.NOT_SUPPORT -> makeToastAndLog("This lock doesn't support this function", 0)
                                    }
                                }

                                override fun onSuccess() {
                                    makeToastAndLog("Set keypad password successfully", 1)
                                }
                            })
                        }
                    } else {
                        makeToastAndLog("Get AC3 ERROR!!!", 1)
                    }
                }
            } else {
                makeToastAndLog("This lock doesn't support this function", 0)
            }
        }
    }

    private fun removeKeypadPassword() {
        if (!phonekeyBLELock.isActive()) {
            makeToastAndLog("Activated Lock first!!!", 0)
        } else {
            Timber.i("Remove keypad password.")
            val verificationCode = requireActivity().getPreferences(Context.MODE_PRIVATE)
                .getString("verificationCode", "")!!

            if (verificationCode == "") {
                makeToastAndLog("Open lock first!!!", 0)
                return
            }

            communicationDialogFragment.show("Remove keypad password")
            phonekeyBLELock.checkKeypadStatus(verificationCode, object : PhonekeyBLELock.CheckKeypadStatusListener {
                override fun onStatusReturn(type: PhonekeyBLELock.LockType, isOpening: Boolean) {
                    makeToastAndLog("Lock isOpening: $isOpening", 1)

                    phonekeyBLELock.removeKeypadPassword(object : PhonekeyBLELock.RemoveKeypadPasswordListener {
                        override fun onFailure() {
                            makeToastAndLog("This lock doesn't support this function", 0)
                        }

                        override fun onSuccess() {
                            makeToastAndLog("Remove keypad password successfully", 1)
                        }
                    })
                }

                override fun onFailure(error: PhonekeyBLELock.KeypadError) {
                    when (error) {
                        PhonekeyBLELock.KeypadError.VERIFICATION_CODE_ERROR ->  makeToastAndLog("Verification code is incorrect!!!", 0)
                        PhonekeyBLELock.KeypadError.NOT_SUPPORT -> makeToastAndLog("This lock doesn't support this function", 0)
                    }
                }
            })
        }
    }

    override fun onWrite(data: String) {
        GlobalScope.launch(Dispatchers.Main) {
            communicationDialogFragment.addLine(data)
        }
    }

    override fun onReceive(result: String) {
        GlobalScope.launch(Dispatchers.Main) {
            communicationDialogFragment.addLine(result)
        }
    }

    enum class PasswordType {
        LOCK, KEYPAD
    }
    private fun enterPasswordAlertDialog(title: String, type: PasswordType, callback: (String) -> Unit) {
        GlobalScope.launch(Dispatchers.Main) {
            val editText = EditText(requireContext())
            alertDialog = AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setView(getAlertAlertDialogEditView(editText, type))
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Confirm") { _, _ ->
                    when (type) {
                        PasswordType.LOCK -> {
                            if (editText.text.toString().length != 16) {
                                AlertDialog.Builder(requireContext())
                                    .setMessage("Length must equal 16")
                                    .setNegativeButton("Dismiss", null)
                                    .show()
                                return@setPositiveButton
                            }
                        }

                        PasswordType.KEYPAD -> {
                            if (editText.text.toString().length != 6) {
                                AlertDialog.Builder(requireContext())
                                    .setMessage("Length must equal 6")
                                    .setNegativeButton("Dismiss", null)
                                    .show()
                                return@setPositiveButton
                            }
                        }
                    }
                    callback(editText.text.toString())
                }
                .setCancelable(false)
                .show()
        }
    }

    private fun getAlertAlertDialogEditView(editText: EditText, type: PasswordType): View {
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
        if (type == PasswordType.LOCK) {
            editText.setText(DEFAULT_LOCK_PASSWORD)
        } else {
            editText.setText(DEFAULT_KEYPAD_PASSWORD)
        }

        linearLayout.addView(editText)
        return linearLayout
    }

    fun makeToastAndLog(message: String, logger: Int) {
        if (logger==1) {
            Timber.i(message)
        } else {
            Timber.e(message)
        }
        GlobalScope.launch(Dispatchers.Main) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
        }
    }
}