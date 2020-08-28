package com.userstar.phonekeyblelock

import android.annotation.SuppressLint
import android.nfc.tech.NfcV
import android.util.Log
import com.userstar.phonekeyblelock.Userstar.*
import java.lang.Exception

@SuppressLint("LogNotTimber", "SimpleDateFormat", "DefaultLocale")
class PhonekeyBLELock private constructor(
    private var phonekeyBLEHelper: AbstractPhonekeyBLEHelper? = null,
    private var deviceName: String? = null,
    var isLog: Boolean = true
) {

    private val TAG = "PhonekeyBLELock"

    private lateinit var lockType: LockType
    fun getLockType() : LockType {
        return lockType
    }

    private var isReady = false
    fun isReady() : Boolean {
        return isReady
    }

    enum class DevicePasswordStatus {
        CORRECT,
        INCORRECT,
        INCORRECT_3_TIMES,
        LENGTH_ERROR
    }

    enum class ActivationCodeStatus {
        INCORRECT,
        INCORRECT_3_TIMES,
        LENGTH_ERROR
    }

    enum class LockType {
        NO_KEYPAD,  // "1"
        KEYPAD_NO_READER,  // "2"
        KEYPAD_WITH_READER,  // "3"
        UNDEFINED  // "4"
    }

    data class Builder(
        var phonekeyBLEHelper: AbstractPhonekeyBLEHelper? = null,
        var deviceName: String? = null,
        var isLog: Boolean? = null
    ) {
        /**
         * Set the BLE helper so that this class can write and read bluetooth data.
         * If this is not be set, other function won't work!!!
         *
         * @param phonekeyBLEHelper the BLE class extend AbstractPhonekeyBLEHelper and implement the writing and reading
         */
        fun setBLEHelper(phonekeyBLEHelper: AbstractPhonekeyBLEHelper): Builder {
            this.phonekeyBLEHelper = phonekeyBLEHelper
            return this
        }

        /**
         * Set the device name, for some encryption.
         * If this is not set, some function won't work!!!
         *
         * @param deviceName the lock's device name
         */
        fun setDeviceName(deviceName: String): Builder {
            this.deviceName = deviceName
            return this
        }

        /**
         * Choose to show log if you want or not
         * Default will show
         *
         * @param bool indicates show or not
         */
        fun enableLog(bool: Boolean): Builder {
            this.isLog = bool
            return this
        }

        var listener: (() -> Unit)? = null
        fun setOnReadyListener(listener: () -> Unit) : Builder {
            this.listener = listener
            return this
        }

        fun build(): PhonekeyBLELock {
            val phonekeyBLELock = PhonekeyBLELock(
                phonekeyBLEHelper,
                deviceName,
                if (isLog != null) isLog!! else true
            )

            phonekeyBLELock.getStatus(object : GetStatusListener {
                override fun onReceive(battery: String, version: String, isOpening: Boolean, type: LockType) {
                    phonekeyBLELock.lockType = type
                    phonekeyBLELock.isReady = true

                    if (listener!=null) {
                        listener!!()
                    }
                }
            })

            return phonekeyBLELock
        }
    }


    /*---------------------Lock status---------------------------------------------------------------------------------------------------------------*/
    /**
     * Check device is already activated or not
     * Write 0100, and will receive 0100XX
     *      XX -> 01 is activated, 00 is not
     *
     * @param listener listen the result
     */
    fun isActive(listener: (Boolean) -> Unit) {
        val data = "0100"
        sendDataAndReceive(data) { receivedData ->
            // 0100
            val isActive = receivedData.substring(4, 6) == "01"
            listener(isActive)
        }
    }


    interface GetStatusListener {
        fun onReceive(battery: String, version: String, isOpening: Boolean, type: LockType)
    }
    /**
     * Get lock status from lock
     * Write 0399, and will receive 0399XXYYZZ
     *      XX -> battery
     *      YY -> version
     *      ZZ -> the lock is opening or not, 00 for opening, 01 for closing
     *
     * @param listener listen the result
     * @see GetStatusListener
     */
    fun getStatus(listener: GetStatusListener) {
        val data = "0399"
        sendDataAndReceive(data) { receivedData ->
            // 0399
            val battery = receivedData.substring(6, 8)
            val version = receivedData.substring(10, 12)
            val isOpening = receivedData.substring(14, 16) == "00"
            var type = LockType.UNDEFINED
            try {
                type = when (receivedData.substring(19, 20)) {
                    "1" -> LockType.NO_KEYPAD
                    "2" -> LockType.KEYPAD_NO_READER
                    "3" -> LockType.KEYPAD_WITH_READER
                    else -> type
                }
            } catch (e: StringIndexOutOfBoundsException) {
                e.printStackTrace()
            } finally {
                listener.onReceive(battery, version, isOpening, type)
            }
        }
    }


    /*---------------------Activation------------------------------------------------------------------------------------------------------------------*/
    /**
     * Listener for activation process's result
     */
    interface ActivateListener {
        /**
         * Mostly, this be called because the activation code is incorrect
         * Activation code is generated by the NFC card tag or QR Code with some calculation
         * Witch means you probably use wrong NFC card or QR Code
         *
         * @param status indicates the code's status
         * @param errorTimes returns how many times entering wrong NFC tag or QR Code
         * @param needToWait After 3 times incorrect, have to wait 'needToWait' minutes, then can proceed
         */
        fun onFailure(status: ActivationCodeStatus, errorTimes: Int, needToWait: Int)
        fun onSuccess()
    }
    /**
     * Activate lock by NFC card
     * Before any operation with the lock, you need to activate it first
     * 1. Write 0501 will receive 0502 with T1(0502XXXXXXXXXXXXXXXXXXXX), this procedure is just for getting T1, which is used to calculate later
     *    T1 and NFC result will be used to calculate 'uid', 'ac3', 'counter'
     *
     * 2. Write 0507 with uid and now time(specific format, provided) and will receive 0508 for next step
     *   @see get_time_8
     *   @see get_nowtime
     *
     * 3. Check NFC tag's correctness, and callback on failure with status or success
     *   @see setDevicePassword
     *   @see ActivateListener
     *
     * @param nfcV the NFC scanned result
     * @param newDevicePassword set your own device password for establish key, deactivate ...
     * @param listener listen the result
     */
    fun activate(nfcV: NfcV, newDevicePassword: String, listener: ActivateListener) {
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

                    setDevicePassword(t1, ac3, counter, newDevicePassword, listener)
                }
            } catch (e: Exception) {
                listener.onFailure(ActivationCodeStatus.LENGTH_ERROR, -1, -1)
                if (isLog) {
                    e.printStackTrace()
                }
            }
        }
    }

    /**
     * Activate lock by QR Code
     * Before any operation with the lock, you need to activate it first
     * 1. Write 0501 will receive 0502 with T1(0502XXXXXXXXXXXXXXXXXXXX), this procedure is just for getting T1, which is used to calculate later
     *    T1 and QR code will be used to calculate 'uid', 'ac3', 'counter'
     *
     * 2. Write 0507 with uid and now time(specific format, provided) and will receive 0508 for next step
     *   @see get_time_8
     *   @see get_nowtime
     *
     * 3. Check QR code's correctness, and callback on failure with status or success
     *   @see setDevicePassword
     *   @see ActivateListener
     *
     * @param code this is a string that you scan from the card or you can simply try type the activation code on the card
     * @param newDevicePassword set your own device password for establish key, deactivate ...
     * @param listener listen the result
     */
    fun activate(code: String, newDevicePassword: String, listener: ActivateListener) {
        if (code.length != 36) {
            if (isLog) {
                Log.e(TAG, "QR Code is incorrect, length must be 36")
            }
            listener.onFailure(ActivationCodeStatus.LENGTH_ERROR, -1, -1)
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

                    setDevicePassword(t1, ac3, counter, newDevicePassword, listener)
                }
            }
        }
    }


    interface DeactivateListener {
        /**
         * Callback when password is incorrect
         *
         * @param status indicates the password's status
         * @param errorTimes returns how many times entering wrong password
         * @param needToWait After 3 times incorrect, have to wait 'needToWait' minutes, then can proceed
         */
        fun onFailure(status: DevicePasswordStatus, errorTimes: Int, needToWait: Int)
        fun onSuccess()
    }
    /**
     * Deactivate the lock and clear memory
     * This function will do a factory reset
     *
     * 1. Write 0601 to get T1, read T1 from 0602XXXXXXXXXXXXXXXXXXXX
     *      XXXXXXXXXXXXXXXXXXXX is T1
     *
     * 2. Use T1 to encrypted the device password in specific format
     *   @see triv.get_triv
     *    Write 0603 with the trimmed device password, this procedure is to check password, only enter the right device password can reset the lock
     *    and will receive 0604XY with password status
     *      X == 0 -> device password isn't correct, Y means how many times wrong password have been sent
     *      X == 1 -> device password is correct
     *      X == 2 -> device password is already incorrect 3 times, need to wait Y minutes
     *   @see DeactivateListener
     *
     * 3. Write 99AC to do factory reset, and will receive 99AD to confirm
     *
     * @param devicePassword the device password set before
     * @param listener listen the result
     */
    fun deactivate(devicePassword: String, listener: DeactivateListener) {
        var data = "0601"
        sendDataAndReceive(data) { receivedData ->
            // 0602
            val t1 = receivedData.substring(4)
            val trimmedDevicePassword = triv.get_triv(
                t1,
                devicePassword.substring(0, 12),
                devicePassword.substring(12, 16) + t1.substring(0, 4),
                "00000000000000000000"
            ).toString()
            data = "0603$trimmedDevicePassword"
            sendDataAndReceive(data) { receivedData ->
                // 0604
                when (receivedData.substring(4, 5)) {
                    "0" -> listener.onFailure(
                        DevicePasswordStatus.INCORRECT, receivedData.substring(
                            5
                        ).toInt(), -1
                    )
                    "2" -> listener.onFailure(
                        DevicePasswordStatus.INCORRECT_3_TIMES, -1, receivedData.substring(
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


    /*--------------------------Device password-----------------------------------------------------------------------------------------------*/
    interface ChangeDevicePasswordListener {
        /**
         * Callback when password is incorrect
         *
         * @param status indicates the password's status
         * @param errorTimes returns how many times entering wrong password
         * @param needToWait After 3 times incorrect, have to wait 'needToWait' minutes, then can proceed
         */
        fun onFailure(status: DevicePasswordStatus, errorTimes: Int, needToWait: Int)
        fun onSuccess()
    }
    /**
     * Change device password by entering the old password
     * 1. Write 0501 to get T1, which will receive with 0502XXXXXXXXXXXXXXXXXXXX
     *      XXXXXXXXXXXXXXXXXXXX is T1
     *
     * 2. Use T1 to encrypt old password, and write to lock to verify
     *   @see triv.get_triv
     *    Write 0503 with trimmed old password, will receive 0504XY with password's status
     *      X == "2" -> Password is incorrect, Y means error times
     *      X == "3" -> Enter wrong password 3 times, need to wait Y minutes
     *      X == "7" -> Length error, maybe is not actually meaning the length, the encryption might be wrong either.
     *      XY == "00" -> correct
     *   @see ChangeDevicePasswordListener
     *
     * 3. Encrypted new password
     *   @see encryptDevicePassword
     *    And Write 0505 with encrypted new password, and receive 0506 to check procedure is done
     *
     * @param oldPassword original device password, set at activation
     * @param newPassword new device password
     * @param listener listen the result
     * */
    fun changeDevicePassword(oldPassword: String, newPassword: String, listener: ChangeDevicePasswordListener) {
        if (isLog) {
            Log.i(TAG, "old: $oldPassword,  new: $newPassword")
        }
        var data = "0501"
        sendDataAndReceive(data) { receivedData ->
            // 0502
            val t1 = receivedData.substring(4)
            val trimmedPassword = triv.get_triv(
                t1,
                oldPassword.substring(0, 12),
                oldPassword.substring(12, 16) + t1.substring(0, 4),
                "00000000000000000000"
            ).toString()

            data = "0503$trimmedPassword"
            sendDataAndReceive(data) { receivedData ->
                // 0504

                when (receivedData.substring(4, 5)) {
                    "2" -> listener.onFailure(DevicePasswordStatus.INCORRECT, receivedData.substring(5, 6).toInt(), -1)
                    "3" -> listener.onFailure(DevicePasswordStatus.INCORRECT_3_TIMES, 3, receivedData.substring(5, 6).toInt())
                    "7" -> listener.onFailure(DevicePasswordStatus.LENGTH_ERROR, -1, -1)
                    else -> {
                        if (isLog) {
                            Log.i(TAG, "Set device password: $newPassword")
                        }
                        val encryptedNewPasswordArray = encryptDevicePassword(
                            deviceName,
                            t1,
                            newPassword
                        )
                        data = "0505${encryptedNewPasswordArray[0]}${encryptedNewPasswordArray[1]}"
                        sendDataAndReceive(data) {
                            // 0506
                            listener.onSuccess()
                        }
                    }
                }
            }
        }
    }

    /**
     * Reset the device password with NFC card(not QR Code), if old password is missing
     * Scan the NFC card first and pass it in
     * 1. Write 0501 to get T1, which will receive with 0502XXXXXXXXXXXXXXXXXXXX
     *      XXXXXXXXXXXXXXXXXXXX is T1
     *
     * 2. Calculate ac3 and counter with T1 and NFC Tag result
     *
     * 3. Check NFC tag's correctness, and callback on failure with status or success
     *   @see setDevicePassword
     *   @see ActivateListener
     *
     * @param nfcV the NFC scanned result
     * @param newPassword new device password
     * @param listener listen the result
     */
    fun resetDevicePassword(nfcV: NfcV, newPassword: String, listener: ActivateListener) {
        val data = "0501"
        sendDataAndReceive(data) { receivedData ->
            // 0502
            val t1 = receivedData.substring(4)
            val vData = ustag(nfcV).getVdata(t1)
            val ac3 = vData[1]
            val counter = vData[2] + "01"

            setDevicePassword(t1, ac3, counter, newPassword, listener)
        }
    }

    /**
     * Check the correctness of NFC tag or QR Code, callback on failure or set new password and callback on success
     * 1. Write 0503 with AC3 + counter + activation way(01), and will receive 0504XY to check AC3's correctness
     *      XY == 00 -> correct
     *      X == 1 -> AC3 is incorrect, Y means how many times incorrect AC3 have been sent
     *      X == 3 -> Already try three times with wrong AC3, Y means need to wait how many minutes
     *      XY == 77 -> Length error, maybe is not actually meaning the length, the encryption might be wrong either.
     *   @see ActivateListener
     *
     * 2. Encrypted new password
     *   @see encryptDevicePassword
     *    And Write 0505 with encrypted new password, and receive 0506 to check procedure is done
     *
     * @param T1 get from lock
     * @param ac3 calculate from NFC tag or QR Code with T1, see the caller
     * @param counter calculate from NFC tag or QR Code with T1, see the caller
     * @param listener listen the result
     */
    private fun setDevicePassword(T1: String, ac3: String, counter: String, newPassword: String ,listener: ActivateListener) {
        var data = "0503$ac3$counter"
        sendDataAndReceive(data) { receivedData ->
            // 0504

            when (receivedData.substring(4, 5)) {
                "1" -> listener.onFailure(ActivationCodeStatus.INCORRECT, receivedData.substring(5, 6).toInt(), -1)
                "3" -> listener.onFailure(ActivationCodeStatus.INCORRECT_3_TIMES, 3, receivedData.substring(5, 6).toInt())
                "7" -> listener.onFailure(ActivationCodeStatus.LENGTH_ERROR, -1, -1)
                else -> {
                    Log.i(TAG, "Set new password: $newPassword")
                    val encryptedNewPasswordArray = encryptDevicePassword(
                        deviceName,
                        T1,
                        newPassword
                    )
                    data = "0505${encryptedNewPasswordArray[0]}${encryptedNewPasswordArray[1]}"
                    sendDataAndReceive(data) {
                        // 0506
                        listener.onSuccess()
                    }
                }
            }
        }
    }

    interface CheckDevicePasswordListener {
        /**
         * Be called when get the result
         *
         * @param status indicates the password's status
         * @param errorTimes returns how many times entering wrong password
         * @param needToWait After 3 times incorrect, have to wait 'needToWait' minutes, then can proceed
         */
        fun onResult(status: DevicePasswordStatus, errorTimes: Int, needToWait: Int)
    }
    /**
     * Check device password
     * @see checkDevicePassword
     *
     * @param password the device password
     * @param listener listen the result
     * @see CheckDevicePasswordListener
     * */
    fun checkDevicePassword(password: String, listener: CheckDevicePasswordListener) {
        checkDevicePassword(password) { receivedData ->
            when (receivedData.substring(4, 5)) {
                "0" -> listener.onResult(DevicePasswordStatus.INCORRECT, receivedData.substring(5).toInt(), -1)
                "1" -> listener.onResult(DevicePasswordStatus.CORRECT, -1, -1)
                "2" -> listener.onResult(DevicePasswordStatus.INCORRECT_3_TIMES, -1, receivedData.substring(5).toInt())
            }
        }
    }

    /**
     * 1. Write 0601 to get T1, read T1 from 0602XXXXXXXXXXXXXXXXXXXX
     *      XXXXXXXXXXXXXXXXXXXX is T1
     *
     * 2. Use T1 to encrypted the device password in specific format
     *   @see triv.get_triv
     *    Write 0603 with the trimmed device password to check password and will receive 0604XY with password's status
     *      X == 0 -> device password isn't correct, Y means how many times wrong password have been sent
     *      X == 1 -> device password is correct
     *      X == 2 -> device password is already incorrect 3 times, need to wait Y minutes
     *
     * @param password password want to check
     * @param listener listen the result
     */
    private fun checkDevicePassword(password: String, listener: (String) -> Unit) {
        var data = "0601"
        sendDataAndReceive(data) { receivedData ->
            // 0602
            val t1 = receivedData.substring(4)
            val trimmedPassword = triv.get_triv(
                t1,
                password.substring(0, 12),
                password.substring(12, 16) + t1.substring(0, 4),
                "00000000000000000000"
            ).toString()
            data = "0603$trimmedPassword"
            sendDataAndReceive(data) { receivedData ->
                // 0604
                listener(receivedData)
            }
        }
    }


    /*---------------------Establish Key-----------------------------------------------------------------------------------------------------------*/
    interface EstablishKeyListener {
        /**
         * Be called when password isn't correct
         *
         * @param status indicates the password's status
         * @param errorTimes returns how many times entering wrong password
         * @param needToWait After 3 times incorrect, have to wait 'needToWait' minutes, then can proceed
         */
        fun onFailure(status: DevicePasswordStatus, errorTimes: Int, needToWait: Int)

        /**
         * After successfully established key
         * this function will return KeyA and KeyB which is used to calculate AC3 for next time opening lock
         * You have to properly save these two Key
         */
        fun onSuccess(keyA: String, keyB: String)
    }
    /**
     * Establish key
     * This function primarily get KeyA and KeyB
     * With KeyA and KeyB, AC3 can be calculated
     * Opening lock and set keypad password will need this AC3
     * If KeyA and KeyB are properly stored, this function only need to be execute once, until the AC3 is not working
     *
     * 1. Enter the correct device password first, and do check procedure
     *   @see checkDevicePassword
     *   @see EstablishKeyListener
     *
     * 2. Write 0101 and receive the type of lock in 0102XXYY
     *      XX ->
     *      YY == 01 ->
     *
     * 3. Write 0103 with 01(establish) and KeyA(generate from app)), and receive 0104XXYYYYYYYYYYYYYYYYYYYY
     *   @see random_value
     *      XX -> 01 means establish key
     *      YYYYYYYYYYYYYYYYYYYY -> KeyB return from the lock
     *
     * After receive KeyB, KeyA and KeyB will return through listener's onSuccess function
     * KeyA and KeyB need to saved properly for calculating AC3
     *
     * @param devicePassword device password set at activation
     * @param listener listen the result
     */
    fun establishKey(devicePassword: String, listener: EstablishKeyListener) {
        checkDevicePassword(devicePassword) { receivedData ->
            when (receivedData.substring(4, 5)) {
                "0" -> listener.onFailure(DevicePasswordStatus.INCORRECT, receivedData.substring(5).toInt(), -1)
                "2" -> listener.onFailure(DevicePasswordStatus.INCORRECT_3_TIMES, -1, receivedData.substring(5).toInt())
                else -> {
                    var data = "0101"
                    sendDataAndReceive(data) {
                        val keyA = random_value(2, 20)
                        data = "010301$keyA"
                        sendDataAndReceive(data) { receivedData ->
                            // 0104
                            val keyB = receivedData.substring(6)
                            listener.onSuccess(keyA, keyB)
                        }
                    }
                }
            }
        }
    }


    /*---------------------Unlock----------------------------------------------------------------------------------------------------------*/
    /*
    * When unlocking the lock, there is 4 steps
    *
    * 1. Get T1
    *       Need to get T1 first, and use it to calculate AC3 with the KeyA and KeyB which is return when establishing key
    *
    * 2. open
    *       Use the correct ac3 to open the lock
    *
    * 3. Update KeyB
    *       If successfully open the lock, the lock will return a new KeyB(the KeyB in lock doesn't update yet)
    *       And this KeyB is for calculating AC3 next time, so must properly store it
    *
    * 4. Inform the lock to update its KeyB
    *       Tell the lock, the new KeyB is stored properly
    *       This step is to prevent that the new KeyB and the KeyB in lock is not the same
    *       So must inform the lock, ac3 will be calculate with new KeyB next time(if it is really stored properly)
    *       If this step do not be performed, the lock will use the old KeyB to check AC3.
    *       Which means if the new KeyB isn't stored properly, use the old KeyB to calculate AC3 next time
    */

    /**
     * Get the T1 for opening the lock(calculating AC3)
     * 1. Write 020104 to get T1, read T1 from 0202XXXXXXXXXXXXXXXXXXXX
     *      0202XXXXXXXXXXXXXXXXXXXX is T1
     *
     * @param listener listen the result
     */
    fun getT1(listener: (String) -> Unit) {
        val data = "020104"
        sendDataAndReceive(data) { receivedData ->
            // 0202
            val t1 = receivedData.substring(4)
            listener(t1)
        }
    }

    interface OpenListener {
        /**
         * Be called when AC3 is wrong
         */
        fun onAC3Error()

        /**
         * Be called when open successfully
         * Pass new KeyB from the lock
         *
         * @param newKeyB the new KeyB
         * */
        fun onSuccess(newKeyB: String, secs: Int)
    }
    /**
     * Open the lock
     * Use ac3 as key to open the lock, ac3 will be verified
     * 1. Write 0203 with AC3 and receive 0204XXYYYYYYYYYYYYYYYYYYYYZZ
     *      XX == "00" -> AC3 is incorrect, there won't be YYYYYYYYYYYYYYYYYYYY,
     *      XX == "01" -> AC3 is correct, open successfully, YYYYYYYYYYYYYYYYYYYY is new KeyB, ZZ means to wait ZZ seconds
     *
     * @param ac3 Calculate by T1(from getT1()), KeyA and KeyB(from successfully established key)
     * @see getT1
     * @param listener listen the result
     * */
    fun open(ac3: String, listener: OpenListener) {
        val data = "0203$ac3"
        sendDataAndReceive(data) { receivedData ->
            // 0204
            when (receivedData.substring(5, 6)) {
                "0" -> Log.e(TAG, "AC3 Error")
                "1" -> listener.onSuccess(receivedData.substring(6, 26), receivedData.substring(27, 28).toInt())
            }
        }
    }

    interface GetNewKeyBListener {
        /**
         * Be called when cannot get the same new KeyB anymore
         */
        fun onFailure()

        /**
         * Be called when the same new KeyB is returned
         */
        fun onSuccess(newKeyB: String)
    }
    /**
     * If new KeyB is missing, call this can get a same new one
     * But only can get three times
     *
     * 1. Write 02050 to get same new KeyB and will receive 0204XXXXXXXXXXXXXXXXXXXX or 0206
     *      0204 -> XXXXXXXXXXXXXXXXXXXX is the same new KeyB
     *      0206 -> exceed the limit call onFailure
     *
     * @param listener listen the result
     * @see GetNewKeyBListener
     * */
    fun getNewKeyB(listener: GetNewKeyBListener) {
        val data = "02050"
        sendDataAndReceive(data) { receivedData ->
            // 0204 or 0206
            if (receivedData.substring(3, 4) == "4") {
                listener.onSuccess(receivedData.substring(6))
            } else {
                listener.onFailure()
            }
        }
    }

    /**
     * Inform the lock to update its KeyB
     * 1. Write 020501 and receive 0206XXXXXXXXXXXXXXXXXXXX
     *      XXXXXXXXXXXXXXXXXXXX is verification code
     *      verification code is only useful on the lock with keypad
     *      which is used to clear keypad's password
     * 2. Return verification code
     * @param listener listen the result
     */
    fun updateLockKeyB(listener: (String) -> Unit) {
        val data = "020501"
        sendDataAndReceive(data) { receivedData ->
            // 0206
            listener(receivedData.substring(6))
        }
    }


    /*----------------Keypad---------------------------------------------------------------------------------------------------------*/
    interface SetKeypadPasswordListener {
        /**
         * Be called when AC3 is wrong
         */
        fun onAC3Error()
        fun onSuccess()
    }
    /**
     * Set keypad's password
     * Need to use AC3 as key to set
     *
     * 1. Write 0301 with AC3 and will receive 0302XX
     *      XX == "00" -> AC3 is wrong
     *      XX == "01" -> AC3 is correct
     *
     * 2. Encrypt keypad's password
     *   @see AES.Encrypt2
     *   @see AES.parseHexStr2Ascii
     *    Write 0303 with encrypted keypad's password
     *
     * @param ac3 calculate with 00000000000000000000(T1) and KeyA, KeyB
     * @param newKeyPadPassword new keypad password
     * @param listener listen the result
     * @see SetKeypadPasswordListener
     */
    fun setKeypadPassword(ac3: String, newKeyPadPassword: String, listener: SetKeypadPasswordListener) {
        if (lockType == LockType.KEYPAD_NO_READER || lockType == LockType.KEYPAD_WITH_READER) {
            var data = "0301$ac3"
            sendDataAndReceive(data) { receivedData ->
                // 0302
                if (receivedData.substring(5, 6) == "0") {
                    listener.onAC3Error()
                } else {
                    val encryptedPassword = AES.Encrypt2(AES.parseHexStr2Ascii(newKeyPadPassword + newKeyPadPassword + ac3), AES.parseHexStr2Ascii(ac3 + ac3.substring(0, 12)))
                    data = "0303$encryptedPassword"
                    sendDataAndReceive(data) {
                        listener.onSuccess()
                    }
                }
            }
        } else {
            Log.w(TAG, "This lock is not support Keypad")
        }
    }

    interface CheckKeypadStatusListener {
        /**
         * Be called when verification code is correct return
         *
         * @param type lock's type
         * @param isOpening lock's status
         * */
        fun onStatusReturn(type: LockType, isOpening: Boolean)

        /**
         * Be called when verification code is incorrect
         */
        fun onVerificationCodeError()
    }
    /**
     * Remove keypad password
     * Before removing, verification code is required, which can get from opening procedure
     *
     * 1. Write 0401 with verification code, and will receive 0402XXYY
     *      XX -> Lock's type
     *      YY -> Is opening or verification's correctness
     *
     * @param verificationCode after opening the lock and update KeyB successfully, the listener will return verification code
     * @param listener listen the result
     */
    fun checkKeypadStatus(verificationCode: String, listener: CheckKeypadStatusListener) {
        if (lockType == LockType.KEYPAD_NO_READER || lockType == LockType.KEYPAD_WITH_READER) {
            val data = "0401$verificationCode"
            sendDataAndReceive(data) { receivedData ->
                // 0402
                when (val status = receivedData.substring(7, 8)) {
                    "2" -> listener.onVerificationCodeError()
                    else -> {
                        val isOpening = status == "0"
                        val type = when (receivedData.substring(5, 6)) {
                            "1" -> LockType.NO_KEYPAD
                            "2" -> LockType.KEYPAD_NO_READER
                            "3" -> LockType.KEYPAD_WITH_READER
                            else -> LockType.UNDEFINED
                        }
                        listener.onStatusReturn(type, isOpening)
                    }
                }
            }
        } else {
            Log.w(TAG, "This lock is not support Keypad")
        }
    }

    /**
     * After checkKeypadStatus, removing keypad's password is available
     * 1. Write 040301 and will receive as finish
     *
     * @param listener listen the result
     */
    fun removeKeypadPassword(listener: () -> Unit) {
        val data = "040301"
        sendDataAndReceive(data) {
            // 0404
            listener()
        }
    }

    private fun sendDataAndReceive(data: String, listener: (String) -> Unit) {
        checkBLEAndLog(data)
        val dataWithLength = concatStringLength(data)
        phonekeyBLEHelper!!.write(toHexByteArray(dataWithLength)) {
            val receiveData = (it.value as ByteArray).toHex().toUpperCase()
            logReceive(receiveData)
            listener(receiveData)
        }
    }

    private fun checkBLEAndLog(string: String) {
        check(phonekeyBLEHelper != null) { "Phonekey BLE helper doesn't set" }
        check(deviceName != null ) { "Device name doesn't set" }
        if (isLog) {
            Log.i(TAG, "write: (${string.substring(0, 4)})${string.substring(4)}")
        }
    }

    private fun logReceive(string: String) {
        if (isLog) {
            Log.i(TAG, " read: (${string.substring(0, 4)})${string.substring(4)}")
        }
    }

    private fun ByteArray.toHex(): String {
        return joinToString("") { "%02x".format(it) }
    }
}