package com.userstar.phonekeybasicfunctiondemokotlin.services

import android.annotation.SuppressLint
import android.util.Log
import com.userstar.phonekeybasicfunctiondemokotlin.services.userstar.Triv
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

@SuppressLint("LogNotTimber", "SimpleDateFormat")
class PhonekeyBLELock {

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

    fun checkActivation(callback: (Boolean) -> Unit) {
        val sendData = "0100"
        checkAndLog(sendData)
        bluetoothHelper!!.write(sendData.toCustomHexByteArray())
            .setOnReceiveListener { gattCharacteristic ->
                val receiveData = (gattCharacteristic.value as ByteArray).toHex()
                logReceive(receiveData)

                val isActive = receiveData.substring(4, 6) == "01"
                callback(isActive)
            }
    }

    fun checkLockerStatus(callback: (String, String, String) -> Unit) {
        val sendData = "0399"
        checkAndLog(sendData)
        bluetoothHelper!!.write(sendData.toCustomHexByteArray())
            .setOnReceiveListener { gattCharacteristic ->
                val receiveData = (gattCharacteristic.value as ByteArray).toHex()
                logReceive(receiveData)

                val battery = receiveData.substring(6, 8)
                val version = receiveData.substring(10, 12)
                val door = receiveData.substring(14, 16)
                callback(battery, version, door)
            }
    }

    fun getT1(callback: (String) -> Unit) {
        val sendData = "0501"
        checkAndLog(sendData)
        bluetoothHelper!!.write(sendData.toCustomHexByteArray())
            .setOnReceiveListener { gattCharacteristic ->
                val receiveData = (gattCharacteristic.value as ByteArray).toHex()
                logReceive(receiveData)

                val T1 = receiveData.substring(4)
                callback(T1)
            }
    }

    fun sendCardID(no: String, callback: () -> Unit) {
        val sendData = "0507$no${getTime8(getNowTime())}"
        checkAndLog(sendData)
        bluetoothHelper!!.write(sendData.toCustomHexByteArray())
            .setOnReceiveListener { gattCharacteristic ->
                val receiveData = (gattCharacteristic.value as ByteArray).toHex()
                logReceive(receiveData)

                callback()
            }
    }

    fun active(masterPassword: String, callback: () -> Unit) {

    }

    // FIXME
    fun restore(masterPassword: String, callback: (RestoreInstructionStatus, Int, Int) -> Unit) {
        var sendData = "0601"
        var receiveData: String
        checkAndLog(sendData)
        bluetoothHelper!!.write(sendData.toCustomHexByteArray())
            .setOnReceiveListener {
                receiveData = (it.value as ByteArray).toHex()
                logReceive(receiveData)
                val T1 = receiveData.substring(4)
                val trimmedMasterPassword = Triv.get_triv(
                    T1,
                    masterPassword.substring(0, 12),
                    masterPassword.substring(12, 16) + T1.substring(0, 4),
                    "00000000000000000000"
                )
                sendData = "0603$trimmedMasterPassword"
                checkAndLog(sendData)
                bluetoothHelper!!.write(sendData.toCustomHexByteArray())
                    .setOnReceiveListener {
                        receiveData = (it.value as ByteArray).toHex()
                        logReceive(receiveData)

                        var times: Int = -1
                        var needToWait: Int = -1
                        val status =  when (receiveData.substring(4, 5)) {
                            "0" -> {
                                times = receiveData.substring(5).toInt()
                                RestoreInstructionStatus.PASSWORD_INCORRECT
                            }
                            "1" -> {
                                if (receiveData.substring(5) == "0") {
                                    RestoreInstructionStatus.PASSWORD_CORRECT
                                } else {
                                    RestoreInstructionStatus.PASSWORD_INCORRECT
                                }
                            }
                            "2" -> {
                                needToWait = receiveData.substring(5).toInt()
                                RestoreInstructionStatus.PASSWORD_INCORRECT_3_TIMES
                            }
                            else -> RestoreInstructionStatus.UNKNOWN
                        }

                        if (status == RestoreInstructionStatus.PASSWORD_CORRECT) {
                            callback(status, times, needToWait)
                        } else {
                            sendData = "99AC"
                            bluetoothHelper!!.write(sendData.toCustomHexByteArray())
                                .setOnReceiveListener {
                                    receiveData = (it.value as ByteArray).toHex()
                                    logReceive(receiveData)

                                    callback(
                                        if (receiveData.substring(4) == "01") {
                                            RestoreInstructionStatus.SUCCESS
                                        } else {
                                            RestoreInstructionStatus.UNKNOWN
                                        }
                                    , times
                                    , needToWait)
                                }
                        }
                    }
            }
    }

    enum class RestoreInstructionStatus {
        PASSWORD_INCORRECT, PASSWORD_CORRECT, PASSWORD_INCORRECT_3_TIMES, UNKNOWN, SUCCESS
    }

    private fun String.toCustomHexByteArray() : ByteArray {
        // Calculate data length and insert in head of data
        val length: Int = if (length % 2 != 0) {
            length / 2 + 1
        } else {
            length / 2
        }
        val stringBuilder = StringBuilder()
        stringBuilder.append(length)
        if (stringBuilder.length < 2) {
            stringBuilder.insert(0, '0')
        }
        val dataWithLength = stringBuilder.append(this).toString()

        val HEX_CHARS = "0123456789ABCDEF"
        val result = ByteArray(dataWithLength.length / 2)
        for (i in dataWithLength.indices step 2) {
            val firstIndex = HEX_CHARS.indexOf(dataWithLength[i])
            val secondIndex = HEX_CHARS.indexOf(dataWithLength[i + 1])
            val octet = firstIndex.shl(4).or(secondIndex)
            result[i.shr(1)] = octet.toByte()
        }
        return result
    }

    private fun ByteArray.toHex(): String {
        return joinToString("") { "%02x".format(it) }
    }

    private fun getTime8(usetime: String): String {
        var nowTime = java.lang.Long.toHexString(getSec("20160101000000", usetime))
        if (nowTime.length < 8) {
            nowTime = "00000000".substring(0, 8 - nowTime.length) + nowTime
        } else if (nowTime.length > 8) {
            nowTime = "FFFFFFFF"
        }
        return nowTime
    }

    private fun getSec(time1: String, time2: String): Long {
        var date1: Date? = null
        var date2: Date? = null
        return if (time1.length == 14 && time2.length == 14) {
            val myFormatter =
                SimpleDateFormat("yyyyMMddHHmmss")
            try {
                date1 = myFormatter.parse(time1)
                date2 = myFormatter.parse(time2)
            } catch (var9: ParseException) {
                var9.printStackTrace()
            }
            abs(date1!!.time - date2!!.time) / 1000L
        } else {
            0L
        }
    }

    private fun getNowTime(): String {
        val sy1 = SimpleDateFormat("yyyyMMddHHmmss")
        val date = Date()
        return sy1.format(date)
    }

    private fun checkAndLog(string: String) {
        if (showLog) {
            Log.i("PhonekeyBLELock", "send: $string")
        }
        check(bluetoothHelper!=null) { "Phonekey BLE helper have not set up yet" }
    }

    private fun logReceive(string: String) {
        if (showLog) {
            Log.i("PhonekeyBLELock", "receive: $string")
        }
    }
}