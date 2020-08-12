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
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.userstar.phonekeybasicfunctiondemokotlin.R
import com.userstar.phonekeybasicfunctiondemokotlin.services.BLEHelper
import timber.log.Timber
import kotlin.collections.ArrayList

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

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        deviceListRecyclerViewAdapter = DeviceListRecyclerViewAdapter()
        deviceListRecyclerView.adapter = deviceListRecyclerViewAdapter

        BLEHelper.getInstance().startScan(requireContext(), null, object : ScanCallback() {
            override fun onScanFailed(errorCode: Int) {
                super.onScanFailed(errorCode)
                Timber.i("failed: $errorCode")
            }

            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                super.onScanResult(callbackType, result)
                if (result != null && result.device.name!=null) {
                    Timber.i("discover name=${result.device.name}, address=${result.device.address}, rssi=${result.rssi}")
                    for (position in 0 until deviceListRecyclerViewAdapter.scanResultList.size) {
                        if (deviceListRecyclerViewAdapter.scanResultList[position].device.name == result.device.name) {
                            deviceListRecyclerViewAdapter.updateRssi(position, result)
                            return
                        }
                    }
                    // Add new devices
                    deviceListRecyclerViewAdapter.updateList(result)
                }
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>?) {
                super.onBatchScanResults(results)
                Timber.i(results.toString())
            }
        })
    }

    override fun onPause() {
        super.onPause()
        deviceListRecyclerViewAdapter.scanResultList.clear()
        deviceListRecyclerViewAdapter.notifyDataSetChanged()
    }

    inner class DeviceListRecyclerViewAdapter : RecyclerView.Adapter<DeviceListRecyclerViewAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.device_list_recycler_view_holder, parent, false))

        var scanResultList = ArrayList<ScanResult>()
        fun updateList(result: ScanResult) {
            requireActivity().runOnUiThread {
                scanResultList.add(result)
                notifyDataSetChanged()
            }
        }

        fun updateRssi(position: Int, result: ScanResult) {
            requireActivity().runOnUiThread {
                scanResultList[position] = result
                notifyDataSetChanged()
            }
        }

        override fun getItemCount() = scanResultList.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
    //            Timber.d(deviceList[position].toString())
            holder.deviceNameTextView.text = scanResultList[position].device.name
            holder.deviceMacTextView.text = scanResultList[position].device.address
            holder.deviceRSSITextView.text = scanResultList[position].rssi.toString()
            holder.itemView.setOnClickListener {

                val callbackConnected = {
                    val destination = DeviceListFragmentDirections
                        .actionDeviceListFragmentToDeviceFragment(scanResultList[position])
                    findNavController().navigate(destination)
                }

                val callbackDisconnected = {
                    findNavController().popBackStack()
                    requireActivity().runOnUiThread {
                        Toast.makeText(requireActivity(), "Device disconnected", Toast.LENGTH_LONG).show()
                    }
                }

                BLEHelper.getInstance().connectBLE(
                    requireContext(),
                    scanResultList[position].device,
                    callbackConnected,
                    callbackDisconnected)
            }
        }

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val deviceNameTextView: TextView = view.findViewById(R.id.device_name_textView)
            val deviceMacTextView: TextView = view.findViewById(R.id.device_mac_textView)
            val deviceRSSITextView: TextView = view.findViewById(R.id.device_rssi_textView)
        }
    }
}