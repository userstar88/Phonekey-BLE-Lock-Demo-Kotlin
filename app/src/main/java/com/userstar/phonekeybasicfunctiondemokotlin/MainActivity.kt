package com.userstar.phonekeybasicfunctiondemokotlin

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.NfcV
import android.os.Bundle
import android.os.Parcelable
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.userstar.phonekeybasicfunctiondemokotlin.timber.ReleaseTree
import com.userstar.phonekeybasicfunctiondemokotlin.timber.ThreadIncludedDebugTree
import org.greenrobot.eventbus.EventBus
import timber.log.Timber

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (BuildConfig.DEBUG) {
            Timber.plant(ThreadIncludedDebugTree())
        } else {
            Timber.plant(ReleaseTree())
        }

        checkPermission()
    }

    private var nfcAdapter: NfcAdapter? = null
    override fun onResume() {
        super.onResume()
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter != null) {
            nfcAdapter!!.enableForegroundDispatch(
                this,
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    0
                ),
                arrayOf(
                    IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED)
                ),
                null)


        } else {
            Timber.w("Can't no get NFC adapter")
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val nfcVTag = NfcV.get(intent.getParcelableExtra<Parcelable>(NfcAdapter.EXTRA_TAG) as Tag)
        EventBus.getDefault().post(nfcVTag)
    }

    private fun checkPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 0)
        }
    }
}