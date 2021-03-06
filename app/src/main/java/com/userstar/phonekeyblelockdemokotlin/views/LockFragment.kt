package com.userstar.phonekeyblelockdemokotlin.views


import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.nfc.NfcAdapter
import android.nfc.tech.NfcV
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.MutableLiveData
import androidx.navigation.fragment.findNavController
import com.journeyapps.barcodescanner.BarcodeView
import com.userstar.phonekeyblelockdemokotlin.SimpleBLEHelper
import com.userstar.phonekeyblelock.PhonekeyBLELock
import com.userstar.phonekeyblelock.PhonekeyBLELockObserver
import com.userstar.phonekeyblelockdemokotlin.R
import com.userstar.phonekeyblelockdemokotlin.Utility.checkPermission
import kotlinx.android.synthetic.main.lock_fragment.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import okhttp3.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber
import java.io.IOException
import java.util.*
import kotlin.concurrent.thread


private const val DEFAULT_DEVICE_PASSWORD = "1111111111111111"
private const val DEFAULT_KEYPAD_PASSWORD = "000000"
private const val DEFAULT_READER_COMMAND = "RU300"
// RE
// WE6666777788889999AAAABBBB
// RT
// RU300

@SuppressLint("SetTextI18n")
class LockFragment : Fragment(), PhonekeyBLELockObserver {

    private var cameraOnPermitCallback: ((Boolean) -> Unit)? = null
    private lateinit var checkCameraPermissionLauncher: ActivityResultLauncher<String>
    override fun onAttach(context: Context) {
        super.onAttach(context)
        checkCameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                Timber.i("Check ${Manifest.permission.CAMERA}.")
            } else {
                val message = when (Manifest.permission.CAMERA) {
                    Manifest.permission.ACCESS_FINE_LOCATION -> "Location accessing permission is required for android BLE function."
                    Manifest.permission.CAMERA -> "Camera's permission is required for scanning the QR activation code."
                    else -> "ERROR"
                }

                val listener: (DialogInterface, Int) -> Unit
                val rightButtonTitle: String
                if (ActivityCompat.shouldShowRequestPermissionRationale(requireActivity(), Manifest.permission.CAMERA)) {
                    rightButtonTitle = "Retry"
                    listener =  { _, _ ->
                        checkCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                } else {
                    rightButtonTitle = "Set"
                    listener = { _, _ ->
                        requireActivity().startActivity(Intent().apply {
                            action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                            data = Uri.parse("package:${requireActivity().packageName}")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                    }
                }
                AlertDialog.Builder(requireContext())
                        .setCancelable(false)
                        .setMessage(message)
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton(rightButtonTitle, listener)
                        .show()
            }
            cameraOnPermitCallback?.invoke(isGranted)
        }
    }

    private fun checkCameraPermission(callback: (Boolean) -> Unit) {
        cameraOnPermitCallback = callback
        checkCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    override fun onStart() {
        super.onStart()
        EventBus.getDefault().register(this)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.lock_fragment, container, false)

        view.findViewById<ImageButton>(R.id.update_button).setOnClickListener { getLockInfo() }
        view.findViewById<Button>(R.id.activate_by_qr_code_Button).setOnClickListener { activateByQRCode() }
        view.findViewById<Button>(R.id.activate_by_nfc_Button).setOnClickListener { activateByNFC() }
        view.findViewById<Button>(R.id.deactivate_Button).setOnClickListener { deactivate() }
        view.findViewById<Button>(R.id.check_device_password_Button).setOnClickListener { checkDevicePassword() }
        view.findViewById<Button>(R.id.change_device_password_Button).setOnClickListener { changeDevicePassword() }
        view.findViewById<Button>(R.id.reset_device_password_by_nfc_Button).setOnClickListener { resetDevicePasswordByNfc() }
        view.findViewById<Button>(R.id.establish_key_Button).setOnClickListener { establishKey() }
        view.findViewById<Button>(R.id.open_Button).setOnClickListener { openLock() }
        view.findViewById<Button>(R.id.set_keypad_password_Button).setOnClickListener {  setKeypadPassword() }
        view.findViewById<Button>(R.id.remove_keypad_password_Button).setOnClickListener { removeKeypadPassword() }
        view.findViewById<Button>(R.id.reader_operation_Button).setOnClickListener { readerOperation() }
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
    private var alertDialog: AlertDialog? = null
    private var communicationDialogFragment: CommunicationDialogFragment? = CommunicationDialogFragment()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        scanResult = requireArguments()["scanResult"] as ScanResult
        lockName = scanResult.device.name
        lock_name_TextView.text = "Lock Name: $lockName"

        communicationDialogFragment = CommunicationDialogFragment()
        communicationDialogFragment?.create(parentFragmentManager) {
            /**
             * Init your bluetooth class with PhonykeyBLELock first
             * Your bluetooth class must inherit PhonekeyBLELockHelper
             *
             * @see SimpleBLEHelper
             * */
            phonekeyBLELock = PhonekeyBLELock.Builder()
                .setBLEHelper(SimpleBLEHelper.getInstance())
                .setLockName(lockName)
                .enableLog(true)
                .registerObserver(this@LockFragment)
                .setOnReadyListener(object : PhonekeyBLELock.GetInfoListener {
                    override fun onReceive(isActive: Boolean, type: PhonekeyBLELock.LockType, battery: String, version: String, isOpening: Boolean) {
                        refreshLockStatus(isActive, type, battery, version, isOpening)
                    }
                })
                .build()
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
        alertDialog?.dismiss()
        EventBus.getDefault().unregister(this)
        SimpleBLEHelper.getInstance().disconnect()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onReceiveNFCTag(nfcV: NfcV) {
        tagLiveData?.postValue(nfcV)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onDisconnected(message: String) {
        if (communicationDialogFragment == null || !communicationDialogFragment!!.isShowing) {
            findNavController().popBackStack()
        } else {
            communicationDialogFragment?.addLine(message, true)
            communicationDialogFragment?.isDisconnected = true
        }
        Toast.makeText(requireActivity(), "Lock disconnected", Toast.LENGTH_LONG).show()
    }

    private fun getLockInfo() {
        // Update lock's status
        communicationDialogFragment?.show("Get Lock's info")
        phonekeyBLELock.getInfo(object : PhonekeyBLELock.GetInfoListener {
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
        GlobalScope.launch(Dispatchers.Main) {
            lock_type_TextView.text = "Lock Type: $type"
            is_active_TextView.text = "isActive: $isActive"
            battery_TextView.text = "Battery: $battery"
            version_TextView.text = "Version: $version"
            open_close_status_TextView.text = "isOpening: $isOpening"
        }
        log("LockType: $type, isActive: $isActive, battery: $battery, version: $version, isOpening: $isOpening", Log.INFO, true)
    }

    /*-------------------------Activation----------------------------------------------------*/
    private var qrCodeLiveData: MutableLiveData<String>? = null
    private fun activateByQRCode() {
        if (phonekeyBLELock.isActive()) {
            log("Lock has been activated", Log.ERROR)
        } else {
            checkCameraPermission { isGranted ->
                if (!isGranted) {
                    log("Camera's permission is not allowed.", Log.ERROR)
                } else {
                    zxing_BarcodeView.visibility = View.VISIBLE
                    zxing_BarcodeView.resume()
                    qrCodeLiveData = MutableLiveData<String>().apply {
                        observe(viewLifecycleOwner) { code ->
                            qrCodeLiveData!!.removeObservers(viewLifecycleOwner)
                            qrCodeLiveData = null
                            getAlertDialogWithEditText("Set your device password", InputType.LOCK_PASSWORD) { devicePassword ->
                                Timber.i("Set device password: $devicePassword")
                                communicationDialogFragment?.show("Activate By QR Code")
                                phonekeyBLELock.activate(code, devicePassword, object : PhonekeyBLELock.ActivateListener {
                                    override fun onFailure(
                                            status: PhonekeyBLELock.ActivationCodeStatus,
                                            errorTimes: Int,
                                            needToWait: Int
                                    ) {
                                        when (status) {
                                            PhonekeyBLELock.ActivationCodeStatus.LENGTH_ERROR -> log("QR Code length ERROR!!!", Log.ERROR)
                                            PhonekeyBLELock.ActivationCodeStatus.INCORRECT -> log("QR Code is incorrect $errorTimes times.", Log.ERROR)
                                            PhonekeyBLELock.ActivationCodeStatus.INCORRECT_3_TIMES -> log("QR Code is incorrect above 3 times, need to wait $needToWait minutes.", Log.ERROR)
                                        }
                                    }

                                    override fun onSuccess() {
                                        log("Activated successfully!", Log.INFO)
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
            log("Lock has been activated", Log.WARN)
        } else {
            if (NfcAdapter.getDefaultAdapter(requireContext()) == null) {
                log("This lock does not support NFC", Log.WARN)
            } else {
                getAlertDialogWithEditText("Set your device password",
                    InputType.LOCK_PASSWORD
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

                            communicationDialogFragment?.show("Activate By NFC")
                            phonekeyBLELock.activate(nfcV, devicePassword, object : PhonekeyBLELock.ActivateListener {
                                override fun onFailure(
                                    status: PhonekeyBLELock.ActivationCodeStatus,
                                    errorTimes: Int,
                                    needToWait: Int
                                ) {
                                    when (status) {
                                        PhonekeyBLELock.ActivationCodeStatus.LENGTH_ERROR -> log("NFC Tag LOST!!!", Log.ERROR)
                                        PhonekeyBLELock.ActivationCodeStatus.INCORRECT -> log("NFC Tag is incorrect $errorTimes times.", Log.ERROR)
                                        PhonekeyBLELock.ActivationCodeStatus.INCORRECT_3_TIMES -> log("NFC Tag is incorrect above 3 times, need to wait $needToWait minutes.", Log.ERROR)
                                    }
                                }

                                override fun onSuccess() {
                                    log("Activated successfully!", Log.INFO)
                                }
                            })
                        }
                    }
                }
            }
        }
    }

    private fun deactivate() {
        if (!phonekeyBLELock.isActive()) {
            log("Activated Lock first!!!", Log.WARN)
        } else {
            alertDialog = AlertDialog.Builder(requireContext())
                .setTitle("Warning")
                .setMessage("This action will reset the lock and clear lock's memory.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Confirm") { _, _ ->
                    getAlertDialogWithEditText("Device password", InputType.LOCK_PASSWORD) { devicePassword ->
                        communicationDialogFragment?.show("Deactivate")
                        phonekeyBLELock.deactivate(devicePassword, object : PhonekeyBLELock.DeactivateListener {
                            override fun onFailure(
                                status: PhonekeyBLELock.DevicePasswordStatus,
                                errorTimes: Int,
                                needToWait: Int
                            ) {
                                when (status) {
                                    PhonekeyBLELock.DevicePasswordStatus.LENGTH_ERROR -> log("Device password length error!!!", Log.ERROR)
                                    PhonekeyBLELock.DevicePasswordStatus.INCORRECT -> log("Device password is error $errorTimes times.", Log.ERROR)
                                    PhonekeyBLELock.DevicePasswordStatus.INCORRECT_3_TIMES -> log("Device password is error above 3 times, need to wait $needToWait minutes.", Log.ERROR)
                                    else -> log("UNKNOWN ERROR.", Log.ERROR)
                                }
                            }

                            override fun onSuccess() {
                                log("The lock has already reset.", Log.INFO)
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
            log("Activated Lock first!!!", Log.WARN)
        } else {
            Timber.i("Check device password")
            getAlertDialogWithEditText("Device password", InputType.LOCK_PASSWORD) { devicePassword ->
                communicationDialogFragment?.show("Check Device Password")
                phonekeyBLELock.checkDevicePassword(devicePassword, object : PhonekeyBLELock.CheckDevicePasswordListener {
                    override fun onResult(
                        status: PhonekeyBLELock.DevicePasswordStatus,
                        errorTimes: Int,
                        needToWait: Int
                    ) {
                        when (status) {
                            PhonekeyBLELock.DevicePasswordStatus.CORRECT -> log("Device password is correct!!!", Log.INFO)
                            PhonekeyBLELock.DevicePasswordStatus.INCORRECT -> log("Device password is incorrect $errorTimes times", Log.INFO)
                            PhonekeyBLELock.DevicePasswordStatus.INCORRECT_3_TIMES -> log("Device password is incorrect above 3 times, need to wait $needToWait minutes", Log.INFO)
                            PhonekeyBLELock.DevicePasswordStatus.LENGTH_ERROR -> log("Device password's lenght must be 16.", Log.INFO)
                        }
                    }
                })
            }
        }
    }

    private fun changeDevicePassword() {
        if (!phonekeyBLELock.isActive()) {
            log("Activated Lock first!!!", Log.WARN)
        } else {
            Timber.i("Change device password")
            getAlertDialogWithEditText("Enter old device password", InputType.LOCK_PASSWORD) { oldPassword ->
                Timber.i("Old device password: $oldPassword")
                getAlertDialogWithEditText("Enter new device password", InputType.LOCK_PASSWORD) { newPassword ->
                    Timber.i("New device password: $newPassword")
                    communicationDialogFragment?.show("Change Device Password")
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
                                    PhonekeyBLELock.DevicePasswordStatus.INCORRECT -> log("Device password is incorrect $errorTimes times", Log.ERROR)
                                    PhonekeyBLELock.DevicePasswordStatus.INCORRECT_3_TIMES -> log("Device password is incorrect above 3 times, need to wait $needToWait minutes", Log.ERROR)
                                    else -> log("UNKNOWN ERROR.", Log.ERROR)
                                }
                            }

                            override fun onSuccess() {
                                log("Set new password $newPassword successfully", Log.INFO)
                            }
                        }
                    )
                }
            }
        }
    }

    private fun resetDevicePasswordByNfc() {
        if (!phonekeyBLELock.isActive()) {
            log("Activated Lock first!!!", Log.WARN)
        } else {
            if (NfcAdapter.getDefaultAdapter(requireContext()) != null) {
                getAlertDialogWithEditText("New device password", InputType.LOCK_PASSWORD) { newDevicePassword ->
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

                            communicationDialogFragment?.show("Reset Device Password")
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
                                            PhonekeyBLELock.ActivationCodeStatus.LENGTH_ERROR -> log("NFC Tag LOST!!!", Log.ERROR)
                                            PhonekeyBLELock.ActivationCodeStatus.INCORRECT -> log("NFC Tag is incorrect $errorTimes times.", Log.ERROR)
                                            PhonekeyBLELock.ActivationCodeStatus.INCORRECT_3_TIMES -> log("NFC Tag is incorrect above 3 times, need to wait $needToWait minutes.", Log.ERROR)
                                        }
                                    }

                                    override fun onSuccess() {
                                        log("Reset device password successfully.", Log.INFO)
                                    }
                                }
                            )
                        }
                    }
                }
            } else {
                log("This lock does not support NFC", Log.WARN)
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
            log("Activated Lock first!!!", Log.WARN)
        } else {
            getAlertDialogWithEditText("Device password", InputType.LOCK_PASSWORD) { devicePassword ->
                communicationDialogFragment?.show("Establish Key")
                phonekeyBLELock.establishKey(devicePassword, object : PhonekeyBLELock.EstablishKeyListener {
                    override fun onFailure(
                        status: PhonekeyBLELock.DevicePasswordStatus,
                        errorTimes: Int,
                        needToWait: Int
                    ) {
                        when (status) {
                            PhonekeyBLELock.DevicePasswordStatus.LENGTH_ERROR -> log("Device password length ERROR!!!", Log.ERROR)
                            PhonekeyBLELock.DevicePasswordStatus.INCORRECT -> log("Device password is error $errorTimes times.", Log.ERROR)
                            PhonekeyBLELock.DevicePasswordStatus.INCORRECT_3_TIMES -> log("Device password is error above 3 times, need to wait $needToWait minutes.", Log.ERROR)
                            else -> log("UNKNOWN ERROR.", Log.ERROR)
                        }
                    }

                    override fun onSuccess(keyA: String, keyB: String) {
                        log("Establish key successfully.", Log.INFO)
                        // Store KeyA and KeyB and calculate AC3
                        Timber.i("KeyA: $keyA")
                        Timber.i("KeyB: $keyB")
                        OkHttpClient().newCall(
                            Request.Builder()
                            .url("http://210.65.11.172:8080/demo/Basketball/DetailLattices_Set.jsp?id=uscabpandroid&pw=userstar&lockid=${lockName.substring(3)}&a=$keyA&b=$keyB")
                            .build())
                            .enqueue(object : Callback {
                                override fun onFailure(call: Call, e: IOException) {
                                    log("Save KeyA, KeyB to server ERROR. ${e.message}", Log.ERROR)
                                }

                                override fun onResponse(call: Call, response: Response) {
                                    if (response.isSuccessful) {
                                        val jsonObject = JSONObject(response.body!!.string())
                                        Timber.i("response: $jsonObject")
                                        val status = jsonObject.getString("s")
                                        if (status == "01") {
                                            log("Save KeyA, KeyB to server successfully.", Log.INFO)
                                        } else {
                                            log("Save KeyA, KeyB to server ERROR. ($status)", Log.ERROR)
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
     */
    private var canOpen = true
    private var secs = 0
    private fun openLock() {
        if (!phonekeyBLELock.isActive()) {
            log("Activated Lock first!!!", Log.WARN)
        } else {
            if (!canOpen) {
                log("Need to wait $secs secs", Log.ERROR)
            } else {
                communicationDialogFragment?.show("Open")
                phonekeyBLELock.getT1 { T1 ->
                    val response = OkHttpClient().newCall(Request.Builder()
                        .url("http://210.65.11.172:8080/demo/Basketball/Netkey_keydata_demo.jsp?id=uscabpandroid&pw=userstar&lockid=${lockName.substring(3)}&t1=$T1")
                        .build())
                        .execute()
                    Timber.i("$response")

                    if (response.isSuccessful) {
                        val jsonObject = JSONObject(response.body!!.string())
                        response.body?.close()
                        Timber.i("response: $jsonObject")

                        try {
                            val ac3 = jsonObject.getString("ac3")
                            log("Get AC3 from server successfully. $ac3", Log.INFO, false)
                            val pk = jsonObject.getString("pk")
                            phonekeyBLELock.open(ac3, object : PhonekeyBLELock.OpenListener {
                                override fun onAC3Error() {
                                    log("AC3 ERROR, check KeyA, KeyB and AC3, or redo Establish Key.", Log.ERROR)
                                }

                                override fun onSuccess(newKeyB: String, secs: Int) {
                                    canOpen = false
                                    this@LockFragment.secs = if (secs != 0) secs else 5
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
                                    log("Open successfully, get new keyB from lock. $newKeyB", Log.INFO, false)
                                    updateLockKeyB(newKeyB, pk)
                                }
                            })
                        } catch (e: JSONException) {
                            log("Failed to get AC3", Log.ERROR)
                        }
                    } else {
                        log("Get AC3 ERROR", Log.ERROR)
                    }
                }
            }
        }
    }

    private fun updateLockKeyB(keyB: String, pk: String) {
        val response = OkHttpClient().newCall(Request.Builder()
            .url("http://210.65.11.172:8080/demo/Basketball/Netkey_updatakeyb.jsp?id=uscabpandroid&pw=userstar&lockid=${lockName.substring(3)}&b=$keyB&pk=$pk")
            .build())
            .execute()

        if (response.isSuccessful) {
            Timber.i("$response")
            Timber.i("response: ${JSONObject(response.body!!.string())}")
            response.body?.close()
            log("Update server's KeyB successfully.", Log.INFO, false)
            phonekeyBLELock.updateLockKeyB { verificationCode ->
                log("Update lock's KeyB successfully. Get verification code. $verificationCode", Log.INFO)
                requireActivity().getPreferences(Context.MODE_PRIVATE)
                    .edit()
                    .putString("verificationCode", verificationCode)
                    .apply()
            }
        } else {
            log("Update server KeyB error. Try get new one.", Log.ERROR, false)
            phonekeyBLELock.getNewKeyB(object : PhonekeyBLELock.GetNewKeyBListener {
                override fun onFailure() {
                    log("Failed above 3 times, can't get KeyB anymore, please use original keyB to unlock next time.", Log.WARN)
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
            log("Activated Lock first!!!", Log.INFO)
        } else {
            Timber.i("Set keypad password.")
            if (phonekeyBLELock.getLockType() != PhonekeyBLELock.LockType.NO_KEYPAD) {
                thread {
                    val response = OkHttpClient().newCall(Request.Builder()
                        .url("http://210.65.11.172:8080/demo/Basketball/Netkey_keydata_demo.jsp?id=uscabpandroid&pw=userstar&lockid=${lockName.substring(3)}&t1=00000000000000000000")
                        .build())
                        .execute()
                    Timber.i("$response")

                    if (response.isSuccessful) {
                        val jsonObject = JSONObject(response.body!!.string())
                        response.body?.close()
                        Timber.i("response: $jsonObject")
                        val ac3 = jsonObject.getString("ac3")
                        getAlertDialogWithEditText("Set  keypad password", InputType.KEYPAD_PASSWORD) { keypadPassword ->
                            communicationDialogFragment?.show("Set Keypad Password")
                            phonekeyBLELock.setKeypadPassword(ac3, keypadPassword, object : PhonekeyBLELock.SetKeypadPasswordListener{
                                override fun onFailure(error: PhonekeyBLELock.KeypadError) {
                                    when (error) {
                                        PhonekeyBLELock.KeypadError.AC3_ERROR -> log("AC3 ERROR, check KeyA, KeyB and AC3, or redo Establish Key.", Log.ERROR)
                                        PhonekeyBLELock.KeypadError.NOT_SUPPORT -> log("This lock doesn't support this function.", Log.ERROR)
                                        else -> log("UNKNOWN ERROR.", Log.ERROR)
                                    }
                                }

                                override fun onSuccess() {
                                    log("Set keypad password successfully.", Log.INFO)
                                }
                            })
                        }
                    } else {
                        log("Get AC3 ERROR!!!", Log.ERROR)
                    }
                }
            } else {
                log("This lock doesn't support this function.", Log.WARN)
            }
        }
    }

    private fun removeKeypadPassword() {
        if (!phonekeyBLELock.isActive()) {
            log("Activated Lock first!!!", Log.WARN)
        } else {
            Timber.i("Remove keypad password.")
            val verificationCode = requireActivity().getPreferences(Context.MODE_PRIVATE)
                .getString("verificationCode", "") ?: ""

            if (verificationCode == "") {
                log("Open lock first!!!", Log.ERROR)
                return
            }

            communicationDialogFragment?.show("Remove Keypad Password")
            phonekeyBLELock.checkKeypadStatus(verificationCode, object : PhonekeyBLELock.CheckKeypadStatusListener {
                override fun onFailure(error: PhonekeyBLELock.KeypadError) {
                    when (error) {
                        PhonekeyBLELock.KeypadError.VERIFICATION_CODE_ERROR -> log("Verification code is incorrect!!!", Log.ERROR)
                        PhonekeyBLELock.KeypadError.NOT_SUPPORT -> log("This lock doesn't support this function.", Log.ERROR)
                        else -> log("UNKNOWN ERROR.", Log.ERROR)
                    }
                }

                override fun onStatusReturn(type: PhonekeyBLELock.LockType, isOpening: Boolean) {
                    log("Lock is opening: $isOpening", Log.INFO, false)
                    phonekeyBLELock.removeKeypadPassword(object : PhonekeyBLELock.RemoveKeypadPasswordListener {
                        override fun onFailure() {
                            log("This lock doesn't support this function.", Log.ERROR)
                        }

                        override fun onSuccess() {
                            log("Remove keypad password successfully.", Log.INFO)
                        }
                    })
                }
            })
        }
    }

    private fun readerOperation() {
        getAlertDialogWithEditText("Device password", InputType.LOCK_PASSWORD) { devicePassword ->
            getAlertDialogWithEditText("Enter command + data(optional)", InputType.READER_COMMAND) { commandData ->
                communicationDialogFragment?.show("Reader operation:\n$commandData")
                phonekeyBLELock.readerOperation(devicePassword, commandData, object : PhonekeyBLELock.ReaderOperationListener {
                    override fun onFailure(
                        status: PhonekeyBLELock.DevicePasswordStatus,
                        errorTimes: Int,
                        needToWait: Int
                    ) {
                        when (status) {
                            PhonekeyBLELock.DevicePasswordStatus.LENGTH_ERROR -> log("Device password length error!!!", Log.ERROR)
                            PhonekeyBLELock.DevicePasswordStatus.INCORRECT -> log("Device password is error $errorTimes times.", Log.ERROR)
                            PhonekeyBLELock.DevicePasswordStatus.INCORRECT_3_TIMES -> log("Device password is error above 3 times, need to wait $needToWait minutes.", Log.ERROR)
                            else -> log("UNKNOWN ERROR.", Log.ERROR)
                        }
                    }

                    override fun onFailure(readerError: PhonekeyBLELock.ReaderError) {
                        when (readerError) {
                            PhonekeyBLELock.ReaderError.COMMAND_LENGTH_ERROR -> log("Command length error.", Log.ERROR)
                            PhonekeyBLELock.ReaderError.NOT_SUPPORT -> log("Function not support.", Log.ERROR)
                            PhonekeyBLELock.ReaderError.NO_RESPONSE -> log("No response.", Log.ERROR)
                        }
                    }

                    override fun onSuccess(data: String) {
                        log("Result: $data", Log.INFO)
                    }
                })
            }
        }
    }

    override fun onWrite(data: String) {
        communicationDialogFragment?.addLine("APP  -> $data")
    }

    override fun onRead(data: String) {
        communicationDialogFragment?.addLine("Lock -> $data")
    }

    enum class InputType {
        LOCK_PASSWORD,
        KEYPAD_PASSWORD,
        READER_COMMAND
    }

    private fun getAlertDialogWithEditText(title: String, type: InputType, callback: (String) -> Unit) {
        GlobalScope.launch(Dispatchers.Main) {
            val editText = EditText(requireContext())
            alertDialog = AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setView(getAlertDialogEditView(editText, type))
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Confirm") { _, _ ->
                    when (type) {
                        InputType.LOCK_PASSWORD -> {
                            if (editText.text.toString().length != 16) {
                                AlertDialog.Builder(requireContext())
                                    .setMessage("Length must equal 16")
                                    .setNegativeButton("Dismiss", null)
                                    .show()
                                return@setPositiveButton
                            }
                        }

                        InputType.KEYPAD_PASSWORD -> {
                            if (editText.text.toString().length != 6) {
                                AlertDialog.Builder(requireContext())
                                    .setMessage("Length must equal 6")
                                    .setNegativeButton("Dismiss", null)
                                    .show()
                                return@setPositiveButton
                            }
                        }

                        InputType.READER_COMMAND -> {
                            if (editText.text.toString().isEmpty()) {
                                AlertDialog.Builder(requireContext())
                                    .setMessage("Command can not be empty.")
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

    private fun getAlertDialogEditView(editText: EditText, type: InputType): View {
        val linearLayout = LinearLayout(requireContext())
        linearLayout.orientation = LinearLayout.VERTICAL
        linearLayout.gravity = Gravity.CENTER_HORIZONTAL
        linearLayout.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        )

        val layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        )
        layoutParams.marginStart = 30
        layoutParams.marginEnd = 30
        editText.layoutParams = layoutParams
        editText.gravity = Gravity.CENTER_HORIZONTAL

        when (type) {
            InputType.LOCK_PASSWORD -> editText.setText(DEFAULT_DEVICE_PASSWORD)
            InputType.KEYPAD_PASSWORD -> editText.setText(DEFAULT_KEYPAD_PASSWORD)
            InputType.READER_COMMAND -> editText.setText(DEFAULT_READER_COMMAND)
        }

        linearLayout.addView(editText)
        return linearLayout
    }

    private fun log(message: String, logger: Int, isFinal: Boolean = true) {
        when (logger) {
            Log.VERBOSE -> Timber.v(message)
            Log.DEBUG -> Timber.d(message)
            Log.INFO -> Timber.i(message)
            Log.WARN -> Timber.w(message)
            Log.ERROR -> Timber.e(message)
        }

        GlobalScope.launch(Dispatchers.Main) {
            if (communicationDialogFragment != null) {
                if (communicationDialogFragment!!.isShowing) {
                    communicationDialogFragment!!.addLine(message, isFinal)
                } else {
                    Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
            }
        }
    }
}