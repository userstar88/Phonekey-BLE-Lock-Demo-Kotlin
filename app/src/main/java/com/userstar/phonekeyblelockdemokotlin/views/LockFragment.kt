package com.userstar.phonekeyblelockdemokotlin.views

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.nfc.tech.NfcV
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.observe
import com.squareup.okhttp.Callback
import com.squareup.okhttp.OkHttpClient
import com.squareup.okhttp.Request
import com.squareup.okhttp.Response
import com.userstar.phonekeyblelockdemokotlin.BLEHelper
import com.userstar.phonekeyblelock.PhonekeyBLELock
import com.userstar.phonekeyblelockdemokotlin.R
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

@SuppressLint("SetTextI18n")
class LockFragment : Fragment() {

    override fun onStart() {
        super.onStart()
        EventBus.getDefault().register(this)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.lock_fragment, container, false)
    }

    private lateinit var scanResult: ScanResult
    private lateinit var lockName: String
    private lateinit var phonekeyBLELock: PhonekeyBLELock
    private lateinit var lockType: PhonekeyBLELock.LockType
    private var isActive = false
    private var alertDialog: AlertDialog? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        scanResult = requireArguments()["scanResult"] as ScanResult
        lockName = scanResult.device.name
        lock_name_TextView.text = "Lock Name: $lockName"

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
            .setOnReadyListener { isActive: Boolean, battery: String, version: String, isOpening: Boolean, type: PhonekeyBLELock.LockType ->
                refreshLock(isActive, battery, version, isOpening, type)
            }
            .build()

        update_button.setOnClickListener {
            updateLockStatus()
        }

        activate_Button.setOnClickListener {
            if (phonekeyBLELock.isActive) {
                makeToastAndLog("Lock have been activated", 0)
            } else {
                alertDialog = AlertDialog.Builder(requireContext())
                    .setNegativeButton("QR Code") { _, _ ->
                        activateByQRCode()
                    }
                    .setPositiveButton("NFC") { _, _ ->
                        activateByNfc()
                    }
                    .show()
            }
        }

        deactivate_Button.setOnClickListener {
            if (phonekeyBLELock.isActive) {
                deactivate()
            } else {
                makeToastAndLog("Activated Lock first!!!", 0)
            }
        }

        establish_key_Button.setOnClickListener {
            if (phonekeyBLELock.isActive) {
                establishKey()
            } else {
                makeToastAndLog("Activated Lock first!!!", 0)
            }
        }

        open_Button.setOnClickListener {
            if (phonekeyBLELock.isActive) {
                openLock()
            } else {
                makeToastAndLog("Activated Lock first!!!", 0)
            }
        }

        reset_device_password_Button.setOnClickListener {
            if (phonekeyBLELock.isActive) {
                changeDevicePassword()
            } else {
                makeToastAndLog("Activated Lock first!!!", 0)
            }
        }

        reset_device_password_by_nfc_Button.setOnClickListener {
            if (phonekeyBLELock.isActive) {
                resetDevicePasswordByNfc()
            } else {
                makeToastAndLog("Activated Lock first!!!", 0)
            }
        }

        check_device_password_Button.setOnClickListener {
            if (phonekeyBLELock.isActive) {
                checkDevicePassword()
            } else {
                makeToastAndLog("Activated Lock first!!!", 0)
            }
        }

        set_keypad_password_Button.setOnClickListener {
            if (phonekeyBLELock.isActive) {
                setKeypadPassword()
            } else {
                makeToastAndLog("Activated Lock first!!!", 0)
            }
        }

        remove_keypad_password_Button.setOnClickListener {
            if (phonekeyBLELock.isActive) {
                removeKeypadPassword()
            } else {
                makeToastAndLog("Activated Lock first!!!", 0)
            }
        }

        zxing_BarcodeView.decodeSingle { barcodeResult ->
            Timber.i("QR code: $barcodeResult")
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
        Timber.i("onStop")
        BLEHelper.getInstance().disConnectBLE()
        EventBus.getDefault().unregister(this)
    }

    private fun updateLockStatus() {
        // Update lock's status
        phonekeyBLELock.getStatus(object : PhonekeyBLELock.GetStatusListener {
            override fun onReceive(
                isActive: Boolean,
                battery: String,
                version: String,
                isOpening: Boolean,
                type: PhonekeyBLELock.LockType
            ) {
                refreshLock(isActive, battery, version, isOpening, type)
            }
        })
    }

    private fun refreshLock(isActive: Boolean, battery: String, version: String, isOpening: Boolean, type: PhonekeyBLELock.LockType) {
        this.isActive = isActive
        this.lockType = type

        lock_type_TextView.text = "Lock Type: ${this.lockType}"
        is_active_TextView.text = "isActive: ${this.isActive}"
        battery_TextView.text = "Battery: $battery"
        version_TextView.text = "Version: $version"
        open_close_status_TextView.text = "isOpening: $isOpening"
    }

    /*-------------------------Activation----------------------------------------------------*/
    private var tagLiveData: MutableLiveData<NfcV>? = null
    private fun activateByNfc() {
        if (NfcAdapter.getDefaultAdapter(requireContext()) != null) {
            alertDialog = AlertDialog.Builder(requireContext())
                .setMessage("Scan NFC Tag")
                .setPositiveButton("Cancel") { _, _ ->
                    tagLiveData = null
                }
                .setCancelable(false)
                .show()

            tagLiveData = MutableLiveData<NfcV>().apply {
                observe(viewLifecycleOwner) { nfcV ->
                    tagLiveData!!.removeObservers(viewLifecycleOwner)
                    tagLiveData = null
                    alertDialog!!.dismiss()

                    enterPasswordAlertDialog("Set your device password",
                        PasswordType.LOCK
                    ) { devicePassword ->
                        Timber.i("Set device password: $devicePassword")
                        phonekeyBLELock.activate(nfcV, devicePassword, object : PhonekeyBLELock.ActivateListener {
                            override fun onFailure(
                                status: PhonekeyBLELock.ActivationCodeStatus,
                                errorTimes: Int,
                                needToWait: Int
                            ) {
                                when (status) {
                                    PhonekeyBLELock.ActivationCodeStatus.LENGTH_ERROR -> makeToastAndLog("NFC Tag LOST!!!", 0)
                                    PhonekeyBLELock.ActivationCodeStatus.INCORRECT -> makeToastAndLog("NFC Tag INCORRECT $errorTimes times.", 0)
                                    PhonekeyBLELock.ActivationCodeStatus.INCORRECT_3_TIMES -> makeToastAndLog("NFC Tag INCORRECT 3 times, need to wait $needToWait minutes.", 0)
                                }
                            }

                            override fun onSuccess() {
                                makeToastAndLog("Activate successfully!", 1)
                                updateLockStatus()
                            }
                        })
                    }
                }
            }

        } else {
            makeToastAndLog("This lock does not support NFC", 0)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onReceiveNFCTag(nfcV: NfcV) {
        if (tagLiveData!=null) {
            tagLiveData!!.postValue(nfcV)
        }
    }

    private var qrCodeLiveData: MutableLiveData<String>? = null
    private fun activateByQRCode() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 0)
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
                        phonekeyBLELock.activate(code, devicePassword, object : PhonekeyBLELock.ActivateListener {
                            override fun onFailure(
                                status: PhonekeyBLELock.ActivationCodeStatus,
                                errorTimes: Int,
                                needToWait: Int
                            ) {
                                when (status) {
                                    PhonekeyBLELock.ActivationCodeStatus.LENGTH_ERROR -> makeToastAndLog("QR Code length ERROR!!!", 0)
                                    PhonekeyBLELock.ActivationCodeStatus.INCORRECT -> makeToastAndLog("QR Code INCORRECT $errorTimes times", 0)
                                    PhonekeyBLELock.ActivationCodeStatus.INCORRECT_3_TIMES -> makeToastAndLog("QR Code INCORRECT 3 times, need to wait $needToWait minutes", 0)
                                }
                            }

                            override fun onSuccess() {
                                makeToastAndLog("Activate successfully!", 1)
                                updateLockStatus()
                            }
                        })
                    }
                }
            }
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
            makeToastAndLog("Camera permission is not allowed", 0)
        }
    }

    private fun deactivate() {
        alertDialog = AlertDialog.Builder(requireContext())
            .setMessage("This action will reset the lock and clear lock's memory")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Confirm") { _, _ ->
                enterPasswordAlertDialog("Device password", PasswordType.LOCK) { devicePassword ->

                    phonekeyBLELock.deactivate(devicePassword, object : PhonekeyBLELock.DeactivateListener {
                        override fun onFailure(
                            status: PhonekeyBLELock.DevicePasswordStatus,
                            errorTimes: Int,
                            needToWait: Int
                        ) {
                            when (status) {
                                PhonekeyBLELock.DevicePasswordStatus.LENGTH_ERROR -> makeToastAndLog("Device password length ERROR!!!", 0)
                                PhonekeyBLELock.DevicePasswordStatus.INCORRECT -> makeToastAndLog("Device password ERROR $errorTimes times.", 0)
                                PhonekeyBLELock.DevicePasswordStatus.INCORRECT_3_TIMES -> makeToastAndLog("Device password ERROR 3 times, need to wait $needToWait minutes.", 0)
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

    /*-------------------------Device Password------------------------------------------------------------*/
    private fun changeDevicePassword() {
        enterPasswordAlertDialog("Enter old device password",
            PasswordType.LOCK
        ) { oldDevicePassword ->
            Timber.i("Old device password: $oldDevicePassword")
            enterPasswordAlertDialog("Enter new device password",
                PasswordType.LOCK
            ) { newDevicePassword ->
                Timber.i("New device password: $newDevicePassword")
                phonekeyBLELock.changeDevicePassword(
                    oldDevicePassword,
                    newDevicePassword,
                    object : PhonekeyBLELock.ChangeDevicePasswordListener {
                        override fun onFailure(
                            status: PhonekeyBLELock.DevicePasswordStatus,
                            errorTimes: Int,
                            needToWait: Int
                        ) {
                            when (status) {
                                PhonekeyBLELock.DevicePasswordStatus.LENGTH_ERROR -> makeToastAndLog("Device password length ERROR!!!", 0)
                                PhonekeyBLELock.DevicePasswordStatus.INCORRECT -> makeToastAndLog("Device password INCORRECT $errorTimes times", 0)
                                PhonekeyBLELock.DevicePasswordStatus.INCORRECT_3_TIMES -> makeToastAndLog("Device password INCORRECT 3 times, need to wait $needToWait minutes", 0)
                            }
                        }

                        override fun onSuccess() {
                            makeToastAndLog("Set new password $newDevicePassword SUCCESSFULLY", 1)
                        }
                    }
                )
            }
        }
    }

    private fun resetDevicePasswordByNfc() {
        if (NfcAdapter.getDefaultAdapter(requireContext()) != null) {
            enterPasswordAlertDialog("New device password", PasswordType.LOCK) { newDevicePassword ->
                Timber.i("New device password: $newDevicePassword")
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
                                        PhonekeyBLELock.ActivationCodeStatus.INCORRECT -> makeToastAndLog("NFC Tag INCORRECT $errorTimes times.", 0)
                                        PhonekeyBLELock.ActivationCodeStatus.INCORRECT_3_TIMES -> makeToastAndLog("NFC Tag INCORRECT 3 times, need to wait $needToWait minutes.", 0)
                                    }
                                }

                                override fun onSuccess() {
                                    makeToastAndLog("Reset device password SUCCESSFULLY.", 1)
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

    private fun checkDevicePassword() {
        Timber.i("Check device password")
        enterPasswordAlertDialog("Device password", PasswordType.LOCK) { devicePassword ->
            phonekeyBLELock.checkDevicePassword(devicePassword, object : PhonekeyBLELock.CheckDevicePasswordListener {
                override fun onResult(
                    status: PhonekeyBLELock.DevicePasswordStatus,
                    errorTimes: Int,
                    needToWait: Int
                ) {
                    when (status) {
                        PhonekeyBLELock.DevicePasswordStatus.CORRECT -> makeToastAndLog("Device password is correct!!!", 0)
                        PhonekeyBLELock.DevicePasswordStatus.INCORRECT -> makeToastAndLog("Device password INCORRECT $errorTimes times", 0)
                        PhonekeyBLELock.DevicePasswordStatus.INCORRECT_3_TIMES -> makeToastAndLog("Device password INCORRECT 3 times, need to wait $needToWait minutes", 0)
                    }
                }
            })
        }
    }

    /*-------------------------Operation------------------------------------------------------------------*/
    /**
     * In most cases, Key(KeyA and KeyB) only need to be established once
     * Properly storing the KeyA and KeyB, use this two value to calculate AC3 and do a opening or set keypad password
     */
    private fun establishKey() {
        enterPasswordAlertDialog("Device password", PasswordType.LOCK) { devicePassword ->
            phonekeyBLELock.establishKey(devicePassword, object : PhonekeyBLELock.EstablishKeyListener {
                override fun onFailure(
                    status: PhonekeyBLELock.DevicePasswordStatus,
                    errorTimes: Int,
                    needToWait: Int
                ) {
                    when (status) {
                        PhonekeyBLELock.DevicePasswordStatus.LENGTH_ERROR -> makeToastAndLog("Device password length ERROR!!!", 0)
                        PhonekeyBLELock.DevicePasswordStatus.INCORRECT -> makeToastAndLog("Device password ERROR $errorTimes times.", 0)
                        PhonekeyBLELock.DevicePasswordStatus.INCORRECT_3_TIMES -> makeToastAndLog("Device password ERROR 3 times, need to wait $needToWait minutes.", 0)
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
                                        makeToastAndLog("Save KeyA, KeyB to server SUCCESSFULLY.", 1)
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
        if (canOpen) {
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
                                makeToastAndLog("AC3 ERROR, check KeyA, KeyB and AC3, or redo Establish Key", 0)
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

    private fun updateLockKeyB(keyB: String, pk: String) {
        val response = OkHttpClient().newCall(Request.Builder()
            .url("http://210.65.11.172:8080/demo/Basketball/Netkey_updatakeyb.jsp?id=uscabpandroid&pw=userstar&lockid=${lockName.substring(3)}&b=$keyB&pk=$pk")
            .build())
            .execute()
        Timber.i("$response")
        Timber.i("response: ${JSONObject(response.body().string())}")
        response.body().close()

        if (response != null && response.isSuccessful) {
            makeToastAndLog("Open and Update KeyB SUCCESSFULLY.", 1)
            phonekeyBLELock.updateLockKeyB {  verificationCode ->
                requireActivity().getPreferences(Context.MODE_PRIVATE)
                    .edit()
                    .putString("verificationCode", verificationCode)
                    .apply()
                Timber.i("Verification code: $verificationCode")
                makeToastAndLog("Update verification code SUCCESSFULLY.", 1)
            }
        } else {
            makeToastAndLog("Update KeyB ERROR.", 0)
            phonekeyBLELock.getNewKeyB(object : PhonekeyBLELock.GetNewKeyBListener {
                override fun onFailure() {
                    makeToastAndLog("Failed 3 times, can't get KeyB anymore, please use original keyB to unlock", 0)
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
        Timber.i("Set keypad password.")

        if (phonekeyBLELock.getLockType() == PhonekeyBLELock.LockType.KEYPAD_WITH_READER || phonekeyBLELock.getLockType() == PhonekeyBLELock.LockType.KEYPAD_NO_READER) {
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
                                makeToastAndLog("Set keypad password SUCCESSFULLY", 1)
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

    private fun removeKeypadPassword() {
        Timber.i("Remove keypad password.")
        val verificationCode = requireActivity().getPreferences(Context.MODE_PRIVATE)
            .getString("verificationCode", "")!!

        if (verificationCode == "") {
            makeToastAndLog("Open lock first!!!", 0)
            return
        }

        phonekeyBLELock.checkKeypadStatus(verificationCode, object : PhonekeyBLELock.CheckKeypadStatusListener {
            override fun onStatusReturn(type: PhonekeyBLELock.LockType, isOpening: Boolean) {
                makeToastAndLog("Lock isOpening: $isOpening", 1)

                phonekeyBLELock.removeKeypadPassword(object : PhonekeyBLELock.RemoveKeypadPasswordListener {
                    override fun onFailure(error: PhonekeyBLELock.KeypadError) {
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

    private val DEFAULT_LOCK_PASSWORD = "1111111111111111"
    private val DEFAULT_KEYPAD_PASSWORD = "666666"
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