package com.userstar.phonekeybasicfunctiondemokotlin.services

import android.annotation.SuppressLint
import android.nfc.tech.NfcV
import android.util.Log
import com.userstar.phonekeybasicfunctiondemokotlin.services.userstar.Userstar.*
import com.userstar.phonekeybasicfunctiondemokotlin.services.userstar.triv
import com.userstar.phonekeybasicfunctiondemokotlin.services.userstar.ustag
import java.lang.Exception

@SuppressLint("LogNotTimber", "SimpleDateFormat", "DefaultLocale")
class PhonekeyBLELock {

    val TAG = "PhonekeyBLELock"

    enum class MasterPasswordStatus {
        PASSWORD_INCORRECT,
        PASSWORD_INCORRECT_3_TIMES,
    }

    enum class ActivationCodeStatus {
        CODE_INCORRECT,
        CODE_INCORRECT_3_TIMES,
        LENGTH_ERROR
    }

//    enum class LockType {
//        REPAIR,
//        LOCKER,
//        BASKETBALL,
//        INFLATOR,
//        UNKNOWN
//    }

    companion object {
        private var instance: PhonekeyBLELock? = null
        fun getInstance() : PhonekeyBLELock {
            if (instance==null) {
                instance = PhonekeyBLELock()
            }
            return instance as PhonekeyBLELock
        }
    }

    private var bluetoothHelper: AbstractPhonekeyBLEHelper? = null
    fun setBLEHelper(bluetoothHelper: AbstractPhonekeyBLEHelper) : PhonekeyBLELock {
        this.bluetoothHelper = bluetoothHelper
        return this
    }

    private var showLog = true
    fun showLog(bool: Boolean) : PhonekeyBLELock {
        showLog = bool
        return this
    }

    /*---------------------Lock status---------------------------------------------------------------------------------*/
    fun isActive(callback: (Boolean) -> Unit) {
        val data = "0100"
        sendDataAndReceive(data) { receivedData ->
            // 0100
            val isActive = receivedData.substring(4, 6) == "01"
            callback(isActive)
        }
    }

    interface StatusListener {
        fun onReceived(battery: String, version: String, isClosing: Boolean)
    }

    fun getStatus(callback: StatusListener) {
        val data = "0399"
        sendDataAndReceive(data) { receivedData ->
            // 0399
            val battery = receivedData.substring(6, 8)
            val version = receivedData.substring(10, 12)
            val isClosing = receivedData.substring(14, 16) == "01"
            callback.onReceived(battery, version, isClosing)
        }
    }

    /*---------------------Activate---------------------------------------------------------------------------------*/
    interface ActivateListener {
        fun onFailed(status: ActivationCodeStatus, errorTimes: Int, needToWait: Int)
        fun onSuccess()
    }

    fun activate(nfcV: NfcV, deviceName: String, masterPassword: String, listener: ActivateListener) {
        var data = "0501"
        sendDataAndReceive(data) { receivedData ->
            // 0502
            val t1 = receivedData.substring(4)
            try {
                val vData = ustag(nfcV).getVdata(t1)
                val uid = vData[0]
                val ac3 = vData[1]
                val counter = vData[2] + "01"

                val nowTime = get_time_8(get_nowtime())

                data = "0507$uid$nowTime"
                sendDataAndReceive(data) {
                    // 0508

                    setMasterPassword(t1, ac3, counter, deviceName, masterPassword, listener)
                }

//                setMasterPassword(t1, uid, ac3, counter, deviceName, masterPassword, listener)
            } catch (e: Exception) {
                listener.onFailed(ActivationCodeStatus.LENGTH_ERROR, -1, -1)
                e.printStackTrace()
            }
        }
    }

    fun activate(code: String, deviceName: String, masterPassword: String, listener: ActivateListener) {
        if (code.length != 36) {
            Log.e(TAG, "QR Code's length need to be 36")
            listener.onFailed(ActivationCodeStatus.LENGTH_ERROR, -1, -1)
        } else {
            var data = "0501"
            sendDataAndReceive(data) { receivedData ->
                // 0502
                val t1 = receivedData.substring(4)
                val uid = code.substring(0, 16)
                val ac3 = code.substring(16, 36)
                val counter = "0000000002"
                val nowTime = get_time_8(get_nowtime())

                data = "0507$uid$nowTime"
                sendDataAndReceive(data) {
                    // 0508

                    setMasterPassword(t1, ac3, counter, deviceName, masterPassword, listener)
                }

//                setMasterPassword(t1, uid, ac3, counter, deviceName, masterPassword, listener)
            }
        }
    }

    /*---------------------Deactivate---------------------------------------------------------------------------------*/
    interface DeactivateListener {
        fun onFailed(status: MasterPasswordStatus, errorTimes: Int, needToWait: Int)
        fun onSuccess()
    }

    fun deactivate(masterPassword: String, listener: DeactivateListener) {
        var data = "0601"
        sendDataAndReceive(data) { receivedData ->
            // 0602
            val t1 = receivedData.substring(4)
            val trimmedMasterPassword = triv.get_triv(
                t1,
                masterPassword.substring(0, 12),
                masterPassword.substring(12, 16) + t1.substring(0, 4),
                "00000000000000000000"
            ).toString()
            data = "0603$trimmedMasterPassword"
            sendDataAndReceive(data) { receivedData ->
                // 0604
                when (receivedData.substring(4, 5)) {
                    "0" -> listener.onFailed(
                        MasterPasswordStatus.PASSWORD_INCORRECT, receivedData.substring(
                            5
                        ).toInt(), -1
                    )
                    "2" -> listener.onFailed(
                        MasterPasswordStatus.PASSWORD_INCORRECT_3_TIMES, -1, receivedData.substring(
                            5
                        ).toInt()
                    )
                    else -> {
                        data = "99AC"
                        sendDataAndReceive(data) {
                            // 99AD
                            listener.onSuccess()
                        }
                    }
                }
            }
        }
    }

    /*---------------------Create Key---------------------------------------------------------------------------------*/
    interface EstablishKeyListener {
        fun onFailed(status: MasterPasswordStatus, errorTimes: Int, needToWait: Int)
        fun onSuccess(keyA: String, keyB: String)
    }

    fun establishKey(masterPassword: String, callback: EstablishKeyListener) {
        var data = "0601"
        sendDataAndReceive(data) { receivedData ->
            // 0602
            val t1 = receivedData.substring(4)
            val trimmedMasterPassword = triv.get_triv(
                t1, masterPassword.substring(0, 12), masterPassword.substring(
                    12,
                    16
                ) + t1.substring(0, 4), "00000000000000000000"
            ).toString()
            data = "0603$trimmedMasterPassword"
            sendDataAndReceive(data) { receivedData ->
                // 0604
                if (receivedData.substring(4, 5) != "1") {
                    when (receivedData.substring(4, 5)) {
                        "0" -> callback.onFailed(
                            MasterPasswordStatus.PASSWORD_INCORRECT, receivedData.substring(
                                5
                            ).toInt(), -1
                        )
                        "2" -> callback.onFailed(
                            MasterPasswordStatus.PASSWORD_INCORRECT_3_TIMES,
                            -1,
                            receivedData.substring(
                                5
                            ).toInt()
                        )
                    }
                } else {
                    data = "0101"
                    sendDataAndReceive(data) {
                        // 0102
//                        val lockType = when (receivedData.subSequence(3, 4)) {
//                            "01" -> LockType.REPAIR
//                            "02" -> LockType.LOCKER
//                            "03" -> LockType.BASKETBALL
//                            "04" -> LockType.INFLATOR
//                            else -> LockType.UNKNOWN
//                        }

                        val keyA = random_value(2, 20)
                        data = "010301$keyA"
                        sendDataAndReceive(data) { receivedData ->
                            // 0104
                            val keyB = receivedData.substring(6)
                            callback.onSuccess(keyA, keyB)
                        }
                    }
                }
            }
        }
    }

    /*--------------------------Set password-----------------------------------------------------*/

    fun resetMasterPassword(oldMasterPassword: String, deviceName: String, newMasterPassword: String, listener: ActivateListener) {
        Log.i(TAG, "old: $oldMasterPassword,  new: $newMasterPassword")
        val data = "0501"
        sendDataAndReceive(data) { receivedData ->
            // 0502
            val t1 = receivedData.substring(4)
            val trimmedPassword = triv.get_triv(
                t1,
                oldMasterPassword.substring(0, 12),
                oldMasterPassword.substring(12, 16) + t1.substring(0, 4),
                "00000000000000000000"
            ).toString()

            setMasterPassword(t1, trimmedPassword, "", deviceName, newMasterPassword, listener)
//            setMasterPassword(t1, "0000000000000000", trimmedPassword, "", deviceName, newMasterPassword, listener)
        }
    }

    fun resetMasterPassword(nfcV: NfcV, deviceName: String, newMasterPassword: String, listener: ActivateListener) {
        val data = "0501"
        sendDataAndReceive(data) { receivedData ->
            // 0502
            val t1 = receivedData.substring(4)
            val vData = ustag(nfcV).getVdata(t1)
            val uid = vData[0]
            val ac3 = vData[1]
            val counter = vData[2] + "01"

            setMasterPassword(t1, ac3, counter, deviceName, newMasterPassword, listener)
//            setMasterPassword(t1, uid, ac3, counter, deviceName, newMasterPassword, listener)
        }
    }

//    private fun setMasterPassword(T1: String, uid: String?, ac3: String, counter: String, deviceName: String, masterPassword: String ,listener: ActivateListener) {
//        val nowTime = get_time_8(get_nowtime())
//        var data = "0507${uid?:""}$nowTime"
//        sendDataAndReceive(data) {
//            // 0508
//
//            data = "0503$ac3$counter"
//            sendDataAndReceive(data) { receivedData ->
//                // 0504
//                when (receivedData.substring(4, 5)) {
//                    "1" -> listener.onFailed(ActivationCodeStatus.CODE_INCORRECT, receivedData.substring(5, 6).toInt(), -1)
//                    "3" -> listener.onFailed(ActivationCodeStatus.CODE_INCORRECT_3_TIMES, 3, receivedData.substring(5, 6).toInt())
//                    "7" -> listener.onFailed(ActivationCodeStatus.LENGTH_ERROR, -1, -1)
//                    else -> {
//                        Log.i(TAG, "Set master password: $masterPassword")
//                        val encryptedMasterPasswordKey = encryptMasterPassword(
//                            deviceName,
//                            T1,
//                            masterPassword
//                        )
//                        data = "0505${encryptedMasterPasswordKey[0]}${encryptedMasterPasswordKey[1]}"
//                        sendDataAndReceive(data) {
//                            // 0506
//                            listener.onSuccess()
//                        }
//                    }
//                }
//            }
//        }
//    }

    private fun setMasterPassword(T1: String, ac3: String, counter: String, deviceName: String, masterPassword: String ,listener: ActivateListener) {
        var data = "0503$ac3$counter"
        sendDataAndReceive(data) { receivedData ->
            // 0504
            when (receivedData.substring(4, 5)) {
                "1" -> listener.onFailed(ActivationCodeStatus.CODE_INCORRECT, receivedData.substring(5, 6).toInt(), -1)
                "3" -> listener.onFailed(ActivationCodeStatus.CODE_INCORRECT_3_TIMES, 3, receivedData.substring(5, 6).toInt())
                "7" -> listener.onFailed(ActivationCodeStatus.LENGTH_ERROR, -1, -1)
                else -> {
                    Log.i(TAG, "Set master password: $masterPassword")
                    val encryptedMasterPasswordKey = encryptMasterPassword(
                        deviceName,
                        T1,
                        masterPassword
                    )
                    data = "0505${encryptedMasterPasswordKey[0]}${encryptedMasterPasswordKey[1]}"
                    sendDataAndReceive(data) {
                        // 0506
                        listener.onSuccess()
                    }
                }
            }
        }
    }

    /*---------------------Open---------------------------------------------------------------------------------*/
    interface OpenListener {
        fun onAC3Error()
        fun onSuccess(keyB: String)
    }

    fun getT1(callback: (String) -> Unit) {
        val data = "020104"
        sendDataAndReceive(data) { receivedData ->
            // 0202
            val t1 = receivedData.substring(4)
            callback(t1)
        }
    }

    fun open(ac3: String, listener: OpenListener) {
        var data = "0203$ac3"
        sendDataAndReceive(data) { receivedData ->
            // 0204
            when (receivedData.substring(5, 6)) {
                "00" -> Log.e(TAG, "AC3 Error")
                "01" -> {
                    data = "020501"
                    sendDataAndReceive(data) { receivedData ->
                        // 0206
                        val keyB = receivedData.substring(6)
                        listener.onSuccess(keyB)
                    }
                }
            }
        }
    }

    private fun sendDataAndReceive(data: String, callback: (String) -> Unit) {
        val dataWithLength = concatStringLength(data)
        checkBLEAndLog(dataWithLength)
        bluetoothHelper!!.write(toHexByteArray(dataWithLength)) {
            val receiveData = (it.value as ByteArray).toHex().toUpperCase()
            logReceive(receiveData)
            callback(receiveData)
        }
    }

    private fun checkBLEAndLog(string: String) {
        check(bluetoothHelper != null) { "Phonekey BLE helper have not set up yet" }
        if (showLog) {
            Log.i(TAG, "write: (${string.substring(0, 6)})${string.substring(6)}")
        }
    }

    private fun logReceive(string: String) {
        if (showLog) {
            Log.i(TAG, " read: (${string.substring(0, 4)})${string.substring(4)}")
        }
    }

    private fun ByteArray.toHex(): String {
        return joinToString("") { "%02x".format(it) }
    }
}