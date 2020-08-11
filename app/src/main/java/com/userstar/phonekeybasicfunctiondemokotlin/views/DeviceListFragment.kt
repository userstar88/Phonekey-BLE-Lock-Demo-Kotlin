package com.userstar.phonekeybasicfunctiondemokotlin.views

import android.bluetooth.le.ScanResult
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.lifecycle.observe
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.userstar.phonekeybasicfunctiondemokotlin.viewmodels.DeviceListViewModel
import com.userstar.phonekeybasicfunctiondemokotlin.R
import com.userstar.phonekeybasicfunctiondemokotlin.utilities.Injector
import kotlinx.android.synthetic.main.device_list_fragment.*

class DeviceListFragment : Fragment() {

    companion object {
        @JvmStatic
        fun newInstance() =
            DeviceListFragment()
    }

    lateinit var deviceListRecyclerViewAdapter: DeviceListRecyclerViewAdapter
    lateinit var deviceListRecyclerView: RecyclerView
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

    private val viewModel: DeviceListViewModel by viewModels {
        Injector.provideDeviceListViewModelFactory()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        deviceListRecyclerViewAdapter = DeviceListRecyclerViewAdapter()
        deviceListRecyclerView.adapter = deviceListRecyclerViewAdapter

        start_scan_button.setOnClickListener {
            viewModel.startScan(requireContext())
        }

        viewModel.bleDevice.observe(viewLifecycleOwner) { result ->
            // Check device have already showed?
            for (position in 0 until deviceListRecyclerViewAdapter.scanResultList.size) {
                if (deviceListRecyclerViewAdapter.scanResultList[position].device.name == result.device.name) {
                    deviceListRecyclerViewAdapter.updateRssi(position, result)
                    return@observe
                }
            }

            // Add new devices
            deviceListRecyclerViewAdapter.updateList(result)
        }
    }

    inner class DeviceListRecyclerViewAdapter : RecyclerView.Adapter<DeviceListRecyclerViewAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.device_list_recycler_view_holder, parent, false))

        var scanResultList = ArrayList<ScanResult>()
        fun updateList(result: ScanResult) {
            scanResultList.add(result)
            notifyDataSetChanged()
        }

        fun updateRssi(position: Int, result: ScanResult) {
            scanResultList[position] = result
            notifyDataSetChanged()
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
                    requireActivity().runOnUiThread {
                        Toast.makeText(requireActivity(), "Device disconnected", Toast.LENGTH_LONG).show()
                    }
                }

                viewModel.connectBLE(
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