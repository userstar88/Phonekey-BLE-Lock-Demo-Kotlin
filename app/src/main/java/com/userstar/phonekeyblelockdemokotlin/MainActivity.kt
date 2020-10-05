package com.userstar.phonekeyblelockdemokotlin

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.NfcV
import android.os.Bundle
import android.os.Parcelable
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.userstar.phonekeyblelockdemokotlin.Utility.Updater
import com.userstar.phonekeyblelockdemokotlin.timber.ReleaseTree
import com.userstar.phonekeyblelockdemokotlin.timber.ThreadIncludedDebugTree
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

        runUpdate()
    }

    override fun onResume() {
        super.onResume()
        val nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter != null) {
            nfcAdapter.enableForegroundDispatch(
                this,
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, javaClass),
                    0
                ),
                arrayOf(
                    IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED)
                ),
                null
            )
        } else {
            Timber.w("Can't get NFC adapter")
            Toast.makeText(this, "This device does not support NFC", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val nfcVTag = NfcV.get(intent.getParcelableExtra<Parcelable>(NfcAdapter.EXTRA_TAG) as Tag)
        EventBus.getDefault().post(nfcVTag)
    }

    private fun runUpdate() {
        Updater(this).auto()
    }
}