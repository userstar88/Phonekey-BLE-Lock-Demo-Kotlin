package com.userstar.phonekeybasicfunctiondemokotlin.views

import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.userstar.phonekeybasicfunctiondemokotlin.R
import com.userstar.phonekeybasicfunctiondemokotlin.BLEHelper
import kotlinx.android.synthetic.main.device_list_fragment.*
import kotlinx.coroutines.*
import timber.log.Timber
import java.util.concurrent.CopyOnWriteArrayList

class DeviceListFragment : Fragment() {

    private lateinit var deviceListRecyclerViewAdapter: DeviceListRecyclerViewAdapter
    private lateinit var deviceListRecyclerView: RecyclerView
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.device_list_fragment, container, false)

        deviceListRecyclerView = view.findViewById(R.id.device_list_recyclerView)
        deviceListRecyclerView.layoutManager = LinearLayoutManager(context)
        deviceListRecyclerView.addItemDecoration(
            DividerItemDecoration(
                context,
                LinearLayoutManager.HORIZONTAL
            )
        )

        deviceListRecyclerViewAdapter = DeviceListRecyclerViewAdapter()
        deviceListRecyclerView.adapter = deviceListRecyclerViewAdapter

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        deviceListRecyclerViewAdapter.scanResultList.clear()
        deviceListRecyclerViewAdapter.notifyDataSetChanged()

        auto_connect_Button.setOnClickListener {
            autoConnect("BKBFMLNAFBI")
        }

        BLEHelper.getInstance().startScan(requireContext(), null, object : ScanCallback() {
            override fun onScanFailed(errorCode: Int) {
                super.onScanFailed(errorCode)
                Timber.i("failed: $errorCode")
            }

            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                super.onScanResult(callbackType, result)
                if (isNotConnected) {
                    if (result != null && result.device.name!=null) {
                        Timber.i("discover name: ${result.device.name}, address: ${result.device.address}, rssi: ${result.rssi}")
                        var isNewDevice = true
                        for (position in 0 until deviceListRecyclerViewAdapter.scanResultList.size) {
                            if (deviceListRecyclerViewAdapter.scanResultList[position].device.name == result.device.name) {
                                deviceListRecyclerViewAdapter.updateRssi(position, result)
                                isNewDevice = false
                                break
                            }
                        }
                        if (isNewDevice) {
                            // Add new devices
                            deviceListRecyclerViewAdapter.updateList(result)
                        }
                    }
                }
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>?) {
                super.onBatchScanResults(results)
                Timber.i(results.toString())
            }
        })

        isNotConnected = true
    }

    inner class DeviceListRecyclerViewAdapter : RecyclerView.Adapter<DeviceListRecyclerViewAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.device_list_recycler_view_holder, parent, false))

        var scanResultList = CopyOnWriteArrayList<ScanResult>()
        fun updateList(result: ScanResult) {
            Timber.i("add device ${result.device.name}")
            requireActivity().runOnUiThread {
                scanResultList.add(result)
                notifyDataSetChanged()
            }
        }

        fun updateRssi(position: Int, result: ScanResult) {
            Timber.i("update ${result.device.name} rssi: ${result.rssi} ")
            requireActivity().runOnUiThread {
                scanResultList[position] = result
                notifyDataSetChanged()
            }
        }

        override fun getItemCount() = scanResultList.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.deviceNameTextView.text = scanResultList[position].device.name
            holder.deviceMacTextView.text = scanResultList[position].device.address
            holder.deviceRSSITextView.text = scanResultList[position].rssi.toString()
            holder.itemView.setOnClickListener {
                connect(scanResultList[position])
            }
        }

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val deviceNameTextView: TextView = view.findViewById(R.id.device_name_textView)
            val deviceMacTextView: TextView = view.findViewById(R.id.device_mac_textView)
            val deviceRSSITextView: TextView = view.findViewById(R.id.device_rssi_textView)
        }
    }

    private fun connect(result: ScanResult) {

        Timber.i("Try to connect: ${result.device.name},  ${result.device.address}")
        var isPushed = false
        val callbackConnected = {
            val destination = DeviceListFragmentDirections
                .actionDeviceListFragmentToDeviceFragment(result)
            findNavController().navigate(destination)
            isPushed = true
        }

        val callbackDisconnected = {
            if (isPushed) {
                findNavController().popBackStack()
                requireActivity().runOnUiThread {
                    Toast.makeText(requireActivity(), "Device disconnected", Toast.LENGTH_LONG).show()
                }
            }
        }

        BLEHelper.getInstance().connectBLE(
            requireContext(),
            result.device,
            callbackConnected,
            callbackDisconnected)
    }

    private var isNotConnected = true
    private fun autoConnect(deviceName: String) {
        GlobalScope.launch(Dispatchers.IO) {
            while (isNotConnected) {
                for (result in deviceListRecyclerViewAdapter.scanResultList) {
                    if (result.device.name == deviceName) {
                        connect(result)
                        isNotConnected = false
                        break
                    }
                }
            }
        }
    }
}