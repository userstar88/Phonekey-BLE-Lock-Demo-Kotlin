package com.userstar.phonekeyblelockdemokotlin


import android.app.AlertDialog
import android.app.Dialog
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import okhttp3.*
import okio.*
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.lang.Exception


class UpdaterFailedException(message: String) : Exception(message)

class Updater(
    private val context: Context
) {
    fun loadVersionInfo(onSuccess: ((VersionInfo) -> Unit)) {
        OkHttpClient().newCall(
            Request.Builder()
                .url( "https://f3b85ac3d0c5.ngrok.io/phonekeydemo/version_info.json")
                .build()
        ).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Timber.e("Request failed.")
                throw UpdaterFailedException("IOException: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.code != 200) {
                    Timber.e("Request failed.")
                    throw UpdaterFailedException("Loading version info HttpException: response code: ${response.code}, message: ${response.message}")
                } else {
                    val resultString = response.body?.string()
                    response.body?.close()
                    if (resultString == null) {
                        throw UpdaterFailedException("Loading version info HttpException: response code: ${response.code}, message: ${response.message}")
                    } else {
                        onSuccess(Gson().fromJson(resultString, VersionInfo::class.java))
                    }
                }
            }
        })
    }

    fun downloadApk(info: VersionInfo, file: File, listener: ProgressListener) {
        OkHttpClient.Builder().run {
            addNetworkInterceptor { chain ->
                val originalResponse: Response = chain.proceed(chain.request())
                originalResponse.newBuilder()
                    .body(ProgressResponseBody(originalResponse.body!!, listener))
                    .build()
            }
            build()
        }.newCall(Request.Builder()
            .url(info.uri)
            .build()
        ).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Timber.e("Request failed.")
                throw UpdaterFailedException("IOException: ${e.message}")
            }
            override fun onResponse(call: Call, response: Response) {
                if (response.code != 200) {
                    Timber.e("Request failed.")
                    throw UpdaterFailedException("Downloading apk HttpException: response code: ${response.code}, message: ${response.message}")
                } else {
                    val byteArray = response.body?.bytes()
                    response.body?.close()
                    if (byteArray == null) {
                        throw UpdaterFailedException("Downloading apk HttpException: response code: ${response.code}, message: ${response.message}")
                    } else {
                        Timber.i("Download completed.")
                        if (file.exists()) file.delete()
                        val fileOutputStream = FileOutputStream(file)
                        fileOutputStream.write(byteArray)
                        fileOutputStream.close()
                        Timber.i("File saved.")
                        listener.onFinished(file)
                    }
                }
            }
        })
    }

    fun auto(file: File, listener: ProgressListener) {
        loadVersionInfo { info ->
            listener.onDownloading(5)
            val serverVersion = info.version.replace(".", "")
            val currentVersion = BuildConfig.VERSION_NAME.replace(".", "")
            Timber.i("Server: ${info.version}, current: ${BuildConfig.VERSION_NAME}")
            if (serverVersion > currentVersion) {
                GlobalScope.launch(Dispatchers.Main) {
                    val dialog = AlertDialog.Builder(context)
                        .setMessage("Found new version ${info.version} .\nUpdate?")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Yes") { _, _ ->
                            downloadApk(info, file, listener)
                        }
                        .setCancelable(false)
                        .show()
                }
            } else {
                Timber.i("No need to update.")
            }
        }
    }

    fun downloadByDownloadManager(info: VersionInfo, file: File, onFinished: () -> Unit) {
        if (file.exists()) file.delete()
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadUri = Uri.parse(info.uri)
        // Enqueue a new download and same the referenceId
        downloadManager.enqueue(DownloadManager.Request(downloadUri).apply {
            setMimeType("application/vnd.android.package-archive")
            setTitle("OQRScanner updater")
            setDescription("Downloading...")
            // set destination
            setDestinationUri(Uri.parse("file://${file.absolutePath}"))
        })
        // set BroadcastReceiver to install app when .apk is downloaded
        context.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent
            ) {
                context.unregisterReceiver(this)
                onFinished()
            }
        }, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
    }

    fun startInstallIntent(context: Context, file: File) {
        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                data = FileProvider.getUriForFile(
                    context,
                    BuildConfig.APPLICATION_ID + ".provider",
                    file
                )
            } else {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                setDataAndType(
                    Uri.parse(file.absolutePath),
                    "application/vnd.android.package-archive"
                )
            }
        })
    }
}


data class VersionInfo(
    @SerializedName("appName") val appName: String,
    @SerializedName("version") val version: String,
    @SerializedName("force")  val force: Boolean,
    @SerializedName("uri") val uri: String
)

interface ProgressListener {
    fun onDownloading(percentage: Long)
    fun onDone()
    fun onFinished(file: File)
}

private class ProgressResponseBody(
    private val responseBody: ResponseBody,
    private val progressListener: ProgressListener
) : ResponseBody() {

    override fun contentType(): MediaType {
        return responseBody.contentType()!!
    }

    override fun contentLength(): Long {
        return responseBody.contentLength()
    }

    override fun source(): BufferedSource {
        return source(responseBody.source()).buffer()
    }

    private fun source(source: Source): Source {
        return object : ForwardingSource(source) {
            var totalBytesRead = 0L
            @Throws(IOException::class)
            override fun read(sink: Buffer, byteCount: Long): Long {
                val bytesRead = super.read(sink, byteCount)
                // read() returns the number of bytes read, or -1 if this source is exhausted.
                totalBytesRead += if (bytesRead != -1L) bytesRead else 0
                if (bytesRead != -1L) {
                    progressListener.onDownloading(((totalBytesRead*100)/responseBody.contentLength()))
                } else {
                    progressListener.onDone()
                }
                return bytesRead
            }
        }
    }
}