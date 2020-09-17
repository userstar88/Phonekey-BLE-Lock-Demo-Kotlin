package com.userstar.phonekeyblelockdemokotlin

import android.Manifest
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import timber.log.Timber

fun checkPermission(
    activity: AppCompatActivity,
    permission: String,
    callback: (Boolean) -> Unit
) {
    Timber.i("Check $permission.")
    activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
        if (isGranted) {
            callback(true)
        } else {
            val message = when (permission) {
                Manifest.permission.ACCESS_FINE_LOCATION -> "Location accessing permission is required for android BLE function."
                Manifest.permission.CAMERA -> "Camera's permission is required for scanning the QR activation code."
                else -> "ERROR"
            }

            val listener: (DialogInterface, Int) -> Unit
            val rightButtonTitle: String
            if (ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)) {
                rightButtonTitle = "Retry"
                listener =  { _, _ ->
                    checkPermission(activity, permission, callback)
                }
            } else {
                rightButtonTitle = "Set"
                listener = { _, _ ->
                    activity.startActivity(Intent().apply {
                        action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                        data = Uri.parse("package:${activity.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                }
            }

            AlertDialog.Builder(activity)
                .setCancelable(false)
                .setMessage(message)
                .setNegativeButton("Cancel", null)
                .setPositiveButton(rightButtonTitle, listener)
                .show()
        }
    }.launch(permission)
}