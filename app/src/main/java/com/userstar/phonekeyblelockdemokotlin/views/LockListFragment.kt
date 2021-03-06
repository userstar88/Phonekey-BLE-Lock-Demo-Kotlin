package com.userstar.phonekeyblelockdemokotlin.views

import android.Manifest
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.userstar.phonekeyblelockdemokotlin.SimpleBLEHelper
import com.userstar.phonekeyblelockdemokotlin.BuildConfig
import com.userstar.phonekeyblelockdemokotlin.R
import com.userstar.phonekeyblelockdemokotlin.Utility.checkPermission
import kotlinx.android.synthetic.main.lock_list_fragment.*
import timber.log.Timber
import java.util.*

private const val AUTO_CONNECT_LOCK = "BKBFMLNAFBI"

class LockListFragment : Fragment() {

    private val locationPermission: String = Manifest.permission.ACCESS_FINE_LOCATION
    private lateinit var checkLocationPermissionLauncher: ActivityResultLauncher<String>
    private var locationOnPermitCallback: ((Boolean) -> Unit)? = null
    private var isLocationPermissionGranted = false
    override fun onAttach(context: Context) {
        super.onAttach(context)
        checkLocationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            locationOnPermitCallback?.invoke(isGranted)
        }
    }

    private fun checkLocationPermission(callback: ((Boolean) -> Unit)) {
        locationOnPermitCallback = callback
        checkLocationPermissionLauncher.launch(locationPermission)

    }

//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//
//        checkPermission(requireActivity() as AppCompatActivity, ) { isGranted ->
//            isLocationPermissionGranted = isGranted
//        }
//    }

    private lateinit var lockListRecyclerViewAdapter: LockListRecyclerViewAdapter
    private lateinit var lockListRecyclerView: RecyclerView
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.lock_list_fragment, container, false)

        lockListRecyclerView = view.findViewById(R.id.lock_list_recyclerView)
        lockListRecyclerView.layoutManager = LinearLayoutManager(context)
        lockListRecyclerView.addItemDecoration(
            DividerItemDecoration(
                context,
                LinearLayoutManager.HORIZONTAL
            )
        )

        lockListRecyclerViewAdapter = LockListRecyclerViewAdapter()
        lockListRecyclerView.adapter = lockListRecyclerViewAdapter

        view.findViewById<TextView>(R.id.app_version_TextView).text =  BuildConfig.VERSION_NAME

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        lockListRecyclerViewAdapter.scanResultList.clear()
        lockListRecyclerViewAdapter.notifyDataSetChanged()

        start_scan_Button.setOnClickListener {
            checkLocationPermission { isGranted -> Boolean
                if (isGranted) {
                    if (SimpleBLEHelper.getInstance().isScanning) {
                        Toast.makeText(requireContext(), "Already scanning", Toast.LENGTH_LONG).show()
                    } else {
                        SimpleBLEHelper.getInstance().startScan(requireContext(), arrayOf(), object : ScanCallback() {
                            override fun onScanFailed(errorCode: Int) {
                                super.onScanFailed(errorCode)
                                Timber.i("failed: $errorCode")
                                when (errorCode) {
                                    SCAN_FAILED_ALREADY_STARTED -> Timber.e("SCAN_FAILED_ALREADY_STARTED")
                                    SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> Timber.e("SCAN_FAILED_APPLICATION_REGISTRATION_FAILED")
                                    SCAN_FAILED_FEATURE_UNSUPPORTED -> Timber.e("SCAN_FAILED_FEATURE_UNSUPPORTED")
                                    SCAN_FAILED_INTERNAL_ERROR -> Timber.e("SCAN_FAILED_INTERNAL_ERROR")
                                }
                            }

                            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                                super.onScanResult(callbackType, result)
                                if (result != null && result.device.name!=null) {
                                    Timber.i("discover name: ${result.device.name}, address: ${result.device.address}, rssi: ${result.rssi}")
                                    var isNewDevice = true
                                    for (position in 0 until lockListRecyclerViewAdapter.scanResultList.size) {
                                        if (lockListRecyclerViewAdapter.scanResultList[position].device.name == result.device.name) {
                                            lockListRecyclerViewAdapter.updateRssi(position, result)
                                            isNewDevice = false
                                            break
                                        }
                                    }
                                    if (isNewDevice) {
                                        // Add new locks
                                        lockListRecyclerViewAdapter.updateList(result)
                                    }
                                }
                            }
                        })
                    }
                } else {
                    Toast.makeText(requireContext(), "Lack of location accessing permission.", Toast.LENGTH_LONG).show()
                }
            }
//            if (isLocationPermissionGranted) {

        }

        start_scan_Button.setOnLongClickListener {
            isAutoConnect = true
            start_scan_Button.performClick()
            true
        }
    }

    private var isAutoConnect = false
    inner class LockListRecyclerViewAdapter : RecyclerView.Adapter<LockListRecyclerViewAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.lock_list_recycler_view_holder, parent, false))

        var scanResultList = ArrayList<ScanResult>()
        fun updateList(result: ScanResult) {
            Timber.i("add lock ${result.device.name}")
            requireActivity().runOnUiThread {
                scanResultList.add(result)
                notifyDataSetChanged()
            }
        }

        fun updateRssi(position: Int, result: ScanResult) {
            Timber.i("update ${result.device.name} rssi: ${result.rssi}")
            scanResultList[position] = result
            requireActivity().runOnUiThread {
                notifyItemChanged(position)
            }
        }

        override fun getItemCount() = scanResultList.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.lockNameTextView.text = scanResultList[position].device.name
            holder.lockMacTextView.text = scanResultList[position].device.address
            holder.lockRSSITextView.text = scanResultList[position].rssi.toString()
            holder.itemView.setOnClickListener {
                Toast.makeText(requireContext(), "Connecting...", Toast.LENGTH_LONG).show()
                connect(scanResultList[position])
            }

            if (isAutoConnect && scanResultList[position].device.name == AUTO_CONNECT_LOCK) {
                isAutoConnect = false
                Toast.makeText(requireContext(), "Connecting...", Toast.LENGTH_LONG).show()
                connect(scanResultList[position])
            }
        }

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val lockNameTextView: TextView = view.findViewById(R.id.lock_name_textView)
            val lockMacTextView: TextView = view.findViewById(R.id.lock_mac_textView)
            val lockRSSITextView: TextView = view.findViewById(R.id.lock_rssi_textView)
        }
    }

    private fun connect(result: ScanResult) {
        Timber.i("Try to connect: ${result.device.name},  ${result.device.address}")
        SimpleBLEHelper.getInstance().connect(
            requireContext(),
            result.device
        ) {
            requireActivity().runOnUiThread {
                findNavController().navigate(
                    LockListFragmentDirections.actionLockListFragmentToLockFragment(result)
                )
            }
        }
    }
}