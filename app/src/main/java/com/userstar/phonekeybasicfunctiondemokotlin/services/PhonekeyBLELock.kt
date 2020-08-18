package com.userstar.phonekeybasicfunctiondemokotlin.services

import android.annotation.SuppressLint
import android.nfc.tech.NfcV
import android.util.Log
import com.userstar.phonekeybasicfunctiondemokotlin.services.userstar.Userstar.*
import com.userstar.phonekeybasicfunctiondemokotlin.services.userstar.triv
import com.userstar.phonekeybasicfunctiondemokotlin.services.userstar.ustag

@SuppressLint("LogNotTimber", "SimpleDateFormat")
class PhonekeyBLELock {

    val TAG = "PhonekeyBLELock"

    enum class MasterPasswordStatus {
        PASSWORD_INCORRECT,
        PASSWORD_CORRECT,
        PASSWORD_INCORRECT_3_TIMES,
        UNKNOWN,
        SUCCESS
    }

    enum class ActivationCodeStatus {
        CODE_INCORRECT,
        CODE_INCORRECT_3_TIMES,
        SUCCESS,
        LENGTH_ERROR
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

    fun isActive(callback: (Boolean) -> Unit) {
        val sendData = "0100"
        checkBLEAndLog(sendData)
        bluetoothHelper!!.write(toHexByteArrayWithLength(sendData)) { gattCharacteristic ->
                val receiveData = (gattCharacteristic.value as ByteArray).toHex().toUpperCase()
                logReceive(receiveData)

                val isActive = receiveData.substring(4, 6) == "01"
                callback(isActive)
            }
    }

    fun getStatus(callback: (String, String, String) -> Unit) {
        val sendData = "0399"
        checkBLEAndLog(sendData)
        bluetoothHelper!!.write(toHexByteArrayWithLength(sendData)) { gattCharacteristic ->
                val receiveData = (gattCharacteristic.value as ByteArray).toHex()
                logReceive(receiveData)

                val battery = receiveData.substring(6, 8)
                val version = receiveData.substring(10, 12)
                val door = receiveData.substring(14, 16)
                callback(battery, version, door)
            }
    }

    fun activate(code: String, masterPassword: String, deviceName: String, callback: (ActivationCodeStatus, Int, Int) -> Unit) {
        if (code.length != 36) {
            Log.e(TAG, "QR Code's length need to be 36")
            callback(ActivationCodeStatus.LENGTH_ERROR, -1, -1)
        } else {
            var sendData = "0501"
            var receiveData: String
            checkBLEAndLog(sendData)
            bluetoothHelper!!.write(toHexByteArrayWithLength(sendData)) {
                receiveData = (it.value as ByteArray).toHex().toUpperCase()
                logReceive(receiveData)
                val T1 = receiveData.substring(4)

                val uid = code.substring(0, 16)
                val ac3 = code.substring(16, 36)
                val counter = "0000000002"

                val nowTime = get_time_8(get_nowtime())
                sendData = "0507$uid$nowTime"
                checkBLEAndLog(sendData)
                bluetoothHelper!!.write(toHexByteArrayWithLength(sendData)) {
                    receiveData = (it.value as ByteArray).toHex().toUpperCase()
                    logReceive(receiveData)

                    sendData = "0503$ac3${counter}"
                    checkBLEAndLog(sendData)
                    bluetoothHelper!!.write(toHexByteArrayWithLength(sendData)) {
                        receiveData = (it.value as ByteArray).toHex().toUpperCase()
                        logReceive(receiveData)

                        when (receiveData.substring(4, 5)) {
                            "1" -> callback(ActivationCodeStatus.CODE_INCORRECT, receiveData.substring(5, 6).toInt(), -1)
                            "3" -> callback(ActivationCodeStatus.CODE_INCORRECT_3_TIMES, 3, receiveData.substring(5, 6).toInt())
                            "7" -> callback(ActivationCodeStatus.LENGTH_ERROR, -1, -1)
                            else -> {
                                val encryptedKey = encryptMasterPassword(deviceName, T1, masterPassword)
                                sendData = "0505${encryptedKey[0]}${encryptedKey[1]}"
                                checkBLEAndLog(sendData)
                                bluetoothHelper!!.write(toHexByteArrayWithLength(sendData)) {
                                    receiveData = (it.value as ByteArray).toHex().toUpperCase()
                                    logReceive(receiveData)

                                    callback(ActivationCodeStatus.SUCCESS, -1, -1)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun activity(nfcV: NfcV, deviceName: String, masterPassword: String, callback: (ActivationCodeStatus, Int, Int) -> Unit) {
        var sendData = "0501"
        var receiveData: String
        checkBLEAndLog(sendData)
        bluetoothHelper!!.write(toHexByteArrayWithLength(sendData)) {
            receiveData = (it.value as ByteArray).toHex().toUpperCase()
            logReceive(receiveData)
            val T1 = receiveData.substring(4)

            val vData = ustag(nfcV).getVdata(T1)
            val uid = vData[0]
            val ac3 = vData[1]
            val counter = vData[2] + "01"

            val nowTime = get_time_8(get_nowtime())
            sendData = "0507$uid$nowTime"
            checkBLEAndLog(sendData)
            bluetoothHelper!!.write(toHexByteArrayWithLength(sendData)) {
                receiveData = (it.value as ByteArray).toHex().toUpperCase()
                logReceive(receiveData)

                sendData = "0503$ac3$counter"
                checkBLEAndLog(sendData)
                bluetoothHelper!!.write(toHexByteArrayWithLength(sendData)) {
                    receiveData = (it.value as ByteArray).toHex().toUpperCase()
                    logReceive(receiveData)

                    when (receiveData.substring(4, 5)) {
                        "1" -> callback(ActivationCodeStatus.CODE_INCORRECT, receiveData.substring(5, 6).toInt(), -1)
                        "3" -> callback(ActivationCodeStatus.CODE_INCORRECT_3_TIMES, 3, receiveData.substring(5, 6).toInt())
                        "7" -> callback(ActivationCodeStatus.LENGTH_ERROR, -1, -1)
                        else -> {
                            val encryptedKey = encryptMasterPassword(deviceName, T1, masterPassword)
                            sendData = "0505${encryptedKey[0]}${encryptedKey[1]}"
                            checkBLEAndLog(sendData)
                            bluetoothHelper!!.write(toHexByteArrayWithLength(sendData)) {
                                receiveData = (it.value as ByteArray).toHex().toUpperCase()
                                logReceive(receiveData)

                                callback(ActivationCodeStatus.SUCCESS, -1, -1)
                            }
                        }
                    }
                }
            }
        }
    }

    fun deactivate(masterPassword: String, callback: (MasterPasswordStatus, Int, Int) -> Unit) {
        var sendData = "0601"
        var receiveData: String
        checkBLEAndLog(sendData)
        bluetoothHelper!!.write(toHexByteArrayWithLength(sendData)) {
                receiveData = (it.value as ByteArray).toHex().toUpperCase()
                logReceive(receiveData)
                val T1 = receiveData.substring(4)
                val trimmedMasterPassword = triv.get_triv(
                    T1,
                    masterPassword.substring(0, 12),
                    masterPassword.substring(12, 16) + T1.substring(0, 4),
                    "00000000000000000000"
                )

                sendData = "0603$trimmedMasterPassword"
                checkBLEAndLog(sendData)
                bluetoothHelper!!.write(toHexByteArrayWithLength(sendData)) {
                        receiveData = (it.value as ByteArray).toHex().toUpperCase()
                        logReceive(receiveData)

                        var times: Int = -1
                        var needToWait: Int = -1
                        val status =  when (receiveData.substring(4, 5)) {
                            "0" -> {
                                times = receiveData.substring(5).toInt()
                                MasterPasswordStatus.PASSWORD_INCORRECT
                            }
                            "1" -> {
                                if (receiveData.substring(5) == "0") {
                                    MasterPasswordStatus.PASSWORD_CORRECT
                                } else {
                                    MasterPasswordStatus.PASSWORD_INCORRECT
                                }
                            }
                            "2" -> {
                                needToWait = receiveData.substring(5).toInt()
                                MasterPasswordStatus.PASSWORD_INCORRECT_3_TIMES
                            }
                            else -> MasterPasswordStatus.UNKNOWN
                        }

                        if (status != MasterPasswordStatus.PASSWORD_CORRECT) {
                            callback(status, times, needToWait)
                        } else {
                            sendData = "99AC"
                            checkBLEAndLog(sendData)
                            bluetoothHelper!!.write(toHexByteArrayWithLength(sendData)) {
                                    receiveData = (it.value as ByteArray).toHex()
                                    logReceive(receiveData)

                                    callback(
                                        if (receiveData.substring(4) == "01") {
                                            MasterPasswordStatus.SUCCESS
                                        } else {
                                            MasterPasswordStatus.UNKNOWN
                                        }
                                    , times
                                    , needToWait)
                                }
                        }
                    }
            }
    }

    private fun ByteArray.toHex(): String {
        return joinToString("") { "%02x".format(it) }
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
}