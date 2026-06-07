package com.bretthalliday.fdtuner.ui.scan

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bretthalliday.fdtuner.R
import com.bretthalliday.fdtuner.ble.ConnectionState
import com.bretthalliday.fdtuner.ble.ScannedDevice
import com.bretthalliday.fdtuner.databinding.FragmentScanBinding
import com.bretthalliday.fdtuner.databinding.ItemScanDeviceBinding
import kotlinx.coroutines.launch

class ScanFragment : Fragment() {

    private var _binding: FragmentScanBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ScanViewModel by activityViewModels()
    private lateinit var deviceAdapter: DeviceAdapter

    private val requiredPermissions: Array<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            doStartScan()
        } else {
            Toast.makeText(requireContext(), "BLE permissions required to scan", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScanBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        deviceAdapter = DeviceAdapter { device ->
            viewModel.connect(device.device)
        }

        binding.recyclerDevices.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = deviceAdapter
        }

        binding.btnScan.setOnClickListener {
            checkPermissionsAndScan()
        }

        binding.btnDemo.setOnClickListener {
            viewModel.startDemo()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.connectionState.collect { state ->
                        updateUiForState(state)
                    }
                }
                launch {
                    viewModel.scannedDevices.collect { devices ->
                        deviceAdapter.submitList(devices)
                    }
                }
            }
        }
    }

    private fun updateUiForState(state: ConnectionState) {
        when (state) {
            is ConnectionState.Disconnected -> {
                binding.btnScan.isEnabled = true
                binding.btnScan.text = getString(R.string.scan_start)
                binding.progressScan.visibility = View.GONE
                binding.tvStatus.text = getString(R.string.scan_tap_to_scan)
            }
            is ConnectionState.Scanning -> {
                binding.btnScan.isEnabled = true
                binding.btnScan.text = getString(R.string.scan_stop)
                binding.progressScan.visibility = View.VISIBLE
                binding.tvStatus.text = getString(R.string.scan_found_devices, state.found)
            }
            is ConnectionState.Connecting -> {
                binding.btnScan.isEnabled = false
                binding.progressScan.visibility = View.VISIBLE
                binding.tvStatus.text = getString(R.string.scan_connecting, state.deviceName)
            }
            is ConnectionState.Connected -> {
                binding.btnScan.isEnabled = true
                binding.progressScan.visibility = View.GONE
                findNavController().navigate(R.id.action_scan_to_main)
            }
            is ConnectionState.Demo -> {
                binding.btnScan.isEnabled = true
                binding.progressScan.visibility = View.GONE
                findNavController().navigate(R.id.action_scan_to_main)
            }
            is ConnectionState.Error -> {
                binding.btnScan.isEnabled = true
                binding.btnScan.text = getString(R.string.scan_start)
                binding.progressScan.visibility = View.GONE
                binding.tvStatus.text = state.message
                Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkPermissionsAndScan() {
        val state = viewModel.connectionState.value
        if (state is ConnectionState.Scanning) {
            viewModel.stopScan()
            return
        }

        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            doStartScan()
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun doStartScan() {
        val btManager = requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = btManager?.adapter
        if (adapter == null || !adapter.isEnabled) {
            Toast.makeText(requireContext(), "Please enable Bluetooth", Toast.LENGTH_SHORT).show()
            return
        }
        viewModel.startScan(adapter)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ---- Adapter ----

    private class DeviceAdapter(
        private val onConnect: (ScannedDevice) -> Unit
    ) : ListAdapter<ScannedDevice, DeviceAdapter.VH>(DIFF) {

        inner class VH(private val b: ItemScanDeviceBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(device: ScannedDevice) {
                b.tvDeviceName.text = device.name
                b.tvDeviceAddress.text = device.address
                b.tvRssi.text = "${device.rssi} dBm"
                b.btnConnect.setOnClickListener { onConnect(device) }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val b = ItemScanDeviceBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return VH(b)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(getItem(position))
        }

        companion object {
            val DIFF = object : DiffUtil.ItemCallback<ScannedDevice>() {
                override fun areItemsTheSame(a: ScannedDevice, b: ScannedDevice) = a.address == b.address
                override fun areContentsTheSame(a: ScannedDevice, b: ScannedDevice) = a == b
            }
        }
    }
}
