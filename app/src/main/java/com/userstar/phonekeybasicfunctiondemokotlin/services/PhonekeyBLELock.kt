package com.userstar.phonekeybasicfunctiondemokotlin.services

import android.annotation.SuppressLint
import android.nfc.tech.NfcV
import android.util.Log
import com.userstar.phonekeybasicfunctiondemokotlin.services.userstar.Userstar.*
import com.userstar.phonekeybasicfunctiondemokotlin.services.userstar.triv
import com.userstar.phonekeybasicfunctiondemokotlin.services.userstar.ustag
import java.util.*
import java.util.concurrent.locks.Lock

@SuppressLint("LogNotTimber", "SimpleDateFormat")
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

    enum class LockType {
        REPAIR,
        LOCKER,
        BASKETBALL,
        INFLATOR,
        UNKNOWN
    }

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
            val T1 = receivedData.substring(4)
            val vData = ustag(nfcV).getVdata(T1)
            val uid = vData[0]
            val ac3 = vData[1]
            val counter = vData[2] + "01"
            val nowTime = get_time_8(get_nowtime())
            data = "0507$uid$nowTime"
            sendDataAndReceive(data) {
                data = "0503$ac3$counter"
                sendDataAndReceive(data) { receivedData ->
                    when (receivedData.substring(4, 5)) {
                        "1" -> listener.onFailed(ActivationCodeStatus.CODE_INCORRECT, receivedData.substring(5, 6).toInt(), -1)
                        "3" -> listener.onFailed(ActivationCodeStatus.CODE_INCORRECT_3_TIMES, 3, receivedData.substring(5, 6).toInt())
                        "7" -> listener.onFailed(ActivationCodeStatus.LENGTH_ERROR, -1, -1)
                        else -> {
                            val encryptedMasterPasswordKey = encryptMasterPassword(deviceName, T1, masterPassword)
                            data = "0505${encryptedMasterPasswordKey[0]}${encryptedMasterPasswordKey[1]}"
                            sendDataAndReceive(data) {
                                listener.onSuccess()
                            }
                        }
                    }
                }
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
                val T1 = receivedData.substring(4)
                val uid = code.substring(0, 16)
                val ac3 = code.substring(16, 36)
                val counter = "0000000002"
                val nowTime = get_time_8(get_nowtime())
                data = "0507$uid$nowTime"
                sendDataAndReceive(data) {
                    data = "0503$ac3${counter}"
                    sendDataAndReceive(data) { receivedData ->
                        when (receivedData.substring(4, 5)) {
                            "1" -> listener.onFailed(ActivationCodeStatus.CODE_INCORRECT, receivedData.substring(5, 6).toInt(), -1)
                            "3" -> listener.onFailed(ActivationCodeStatus.CODE_INCORRECT_3_TIMES, 3, receivedData.substring(5, 6).toInt())
                            "7" -> listener.onFailed(ActivationCodeStatus.LENGTH_ERROR, -1, -1)
                            else -> {
                                val encryptedMasterPasswordKey = encryptMasterPassword(deviceName, T1, masterPassword)
                                data = "0505${encryptedMasterPasswordKey[0]}${encryptedMasterPasswordKey[1]}"
                                sendDataAndReceive(data) {
                                    listener.onSuccess()
                                }
                            }
                        }
                    }
                }
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
            val T1 = receivedData.substring(4)
            val trimmedMasterPassword = triv.get_triv(
                T1,
                masterPassword.substring(0, 12),
                masterPassword.substring(12, 16) + T1.substring(0, 4),
                "00000000000000000000"
            )
            data = "0603$trimmedMasterPassword"
            sendDataAndReceive(data) { receivedData ->
                when (receivedData.substring(4, 5)) {
                    "0" -> listener.onFailed(MasterPasswordStatus.PASSWORD_INCORRECT, receivedData.substring(5).toInt(), -1)
                    "2" -> listener.onFailed(MasterPasswordStatus.PASSWORD_INCORRECT_3_TIMES, -1, receivedData.substring(5).toInt())
                    else -> {
                        data = "99AC"
                        sendDataAndReceive(data) {
                            listener.onSuccess()
                        }
                    }
                }
            }
        }
    }

    /*---------------------Create Key---------------------------------------------------------------------------------*/
    interface CreateKeyListener {
        fun onFailed(status: MasterPasswordStatus, errorTimes: Int, needToWait: Int)
        fun onSuccess(keyA: String, keyB: String)
    }

    fun createKey(masterPassword: String, callback: CreateKeyListener) {
        var data = "0601"
        sendDataAndReceive(data) { receivedData ->
            val T1 = receivedData.substring(4)
            val trimmedMasterPassword = triv.get_triv(T1, masterPassword.substring(0, 12), masterPassword.substring(12, 16) + T1.substring(0, 4), "00000000000000000000")
            data = "0603$trimmedMasterPassword"
            sendDataAndReceive(data) { receivedData ->

                if (receivedData.substring(4, 5) != "1") {
                    when (receivedData.substring(4, 5)) {
                        "0" -> callback.onFailed(MasterPasswordStatus.PASSWORD_INCORRECT, receivedData.substring(5).toInt(), -1)
                        "2" -> callback.onFailed(MasterPasswordStatus.PASSWORD_INCORRECT_3_TIMES, -1, receivedData.substring(5).toInt())
                    }
                } else {
                    data = "0101"
                    sendDataAndReceive(data) { receivedData ->
                        val lockType = when (receivedData.subSequence(3, 4)) {
                            "01" -> LockType.REPAIR
                            "02" -> LockType.LOCKER
                            "03" -> LockType.BASKETBALL
                            "04" -> LockType.INFLATOR
                            else -> LockType.UNKNOWN
                        }

                        val keyA = random_value(2, 20)
                        data = "010301$keyA"
                        sendDataAndReceive(data) { receivedData ->
                            val keyB = receivedData.substring(6)
                            callback.onSuccess(keyA, keyB)
                        }
                    }
                }
            }
        }
    }

    fun resetMasterPassword(nfcV: NfcV, deviceName: String, newMasterPassword: String, listener: ActivateListener) {
        var data = "0501"
        sendDataAndReceive(data) { receivedData ->
            // 0502
            val T1 = receivedData.substring(4)
            val vData = ustag(nfcV).getVdata(T1)
            val ac3 = vData[1]
            val counter = vData[2] + "01"

            data = "0503$ac3$counter"
            sendDataAndReceive(data) { receivedData ->
                when (receivedData.substring(4, 5)) {
                    "1" -> listener.onFailed(ActivationCodeStatus.CODE_INCORRECT, receivedData.substring(5, 6).toInt(), -1)
                    "3" -> listener.onFailed(ActivationCodeStatus.CODE_INCORRECT_3_TIMES, 3, receivedData.substring(5, 6).toInt())
                    "7" -> listener.onFailed(ActivationCodeStatus.LENGTH_ERROR, -1, -1)
                    else -> {
                        val encryptedMasterPasswordKey = encryptMasterPassword(deviceName, T1, newMasterPassword)
                        data = "0505${encryptedMasterPasswordKey[0]}${encryptedMasterPasswordKey[1]}"
                        sendDataAndReceive(data) {
                            listener.onSuccess()
                        }
                    }
                }
            }
        }
    }

    interface OpenListener {
        fun onAC3Error()
        fun onSuccess(keyB: String)
    }

    fun getT1(callback: (String) -> Unit) {
        val data = "020104"
        sendDataAndReceive(data) { receivedData ->
            val T1 = receivedData.substring(4)
            callback(T1)
        }
    }

    fun open(ac3: String, listener: OpenListener) {
        var data = "0203$ac3"
        var status: String? = null
        var keyB: String
        sendDataAndReceive(data) { receivedData ->
            when (receivedData.substring(4, 5)) {
                "00" -> listener.onAC3Error()
                "01" -> {
                    data = "020501"
                    sendDataAndReceive(data) { receivedData ->
                        val keyB = receivedData.substring(6)
                        listener.onSuccess(keyB)
                    }
                }
            }
        }
//        val timer = Timer()
//        var counter = 0
//        timer.schedule(object : TimerTask() {
//            override fun run() {
//                if (status==null) {
//                    timer.cancel()
//                    open(ac3, listener)
//                } else if (status=="00") {
//                    if (counter<3) {
//                        data = "020500"
//                        ++counter
//                        sendDataAndReceive(data) { receivedData ->
//                            status = receivedData.substring(4, 5)
//                            if (status=="01") {
//                                keyB = receivedData.substring(6)
//                            }
//                        }
//                    } else {
//                        timer.cancel()
//                        listener.onFailed()
//                    }
//                } else {
//                    timer.cancel()
//
//                }
//            }
//        }, 500, 1000)
    }

    private fun sendDataAndReceive(data: String, callback: (String) -> Unit) {
        checkBLEAndLog(data.toUpperCase())
        bluetoothHelper!!.write(toHexByteArrayWithLength(data.toUpperCase())) {
            val receiveData = (it.value as ByteArray).toHex().toUpperCase()
            logReceive(receiveData)
            callback(receiveData)
        }
    }

    private fun checkBLEAndLog(string: String) {
        check(bluetoothHelper!=null) { "Phonekey BLE helper have not set up yet" }
        if (showLog) {
            Log.i(TAG, "send: $string")
        }
    }

    private fun logReceive(string: String) {
        if (showLog) {
            Log.i(TAG, "receive: $string")
        }
    }

    private fun ByteArray.toHex(): String {
        return joinToString("") { "%02x".format(it) }
    }
}