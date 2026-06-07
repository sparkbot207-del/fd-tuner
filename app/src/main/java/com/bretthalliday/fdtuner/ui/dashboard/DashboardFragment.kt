package com.bretthalliday.fdtuner.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bretthalliday.fdtuner.databinding.FragmentDashboardBinding
import com.bretthalliday.fdtuner.model.TelemetryData
import kotlinx.coroutines.launch

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DashboardViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.telemetry.collect { telem ->
                    updateDashboard(telem)
                }
            }
        }
    }

    private fun updateDashboard(t: TelemetryData?) {
        if (t == null) {
            binding.tvSpeed.text = "--"
            binding.tvSpeedUnit.text = "mph"
            binding.tvVoltage.text = "--.- V"
            binding.tvCurrent.text = "--.- A"
            binding.tvRpm.text = "---- RPM"
            binding.tvGear.text = "-"
            binding.tvSoc.text = "--%"
            binding.tvControllerTemp.text = "--°C"
            binding.tvMotorTemp.text = "--°C"
            return
        }

        binding.tvSpeed.text = "%.1f".format(t.speed)
        binding.tvSpeedUnit.text = t.speedUnit
        binding.tvVoltage.text = "%.1f V".format(t.voltage)
        binding.tvCurrent.text = "%.1f A".format(t.lineCurrent)
        binding.tvRpm.text = "${t.rpm} RPM"
        binding.tvGear.text = t.gear
        binding.tvSoc.text = "${t.soc}%"
        binding.tvControllerTemp.text = "${t.controllerTemp}°C"
        binding.tvMotorTemp.text = "${t.motorTemp}°C"

        // Color current: orange if drawing power, blue if regenerating
        val currentColor = if (t.lineCurrent >= 0) {
            requireContext().getColor(com.bretthalliday.fdtuner.R.color.orange_primary)
        } else {
            requireContext().getColor(com.bretthalliday.fdtuner.R.color.regen_cyan)
        }
        binding.tvCurrent.setTextColor(currentColor)

        // SOC color
        val socColor = when {
            t.soc > 50 -> requireContext().getColor(com.bretthalliday.fdtuner.R.color.soc_green)
            t.soc > 20 -> requireContext().getColor(com.bretthalliday.fdtuner.R.color.orange_primary)
            else -> requireContext().getColor(com.bretthalliday.fdtuner.R.color.error_red)
        }
        binding.tvSoc.setTextColor(socColor)

        // Temperature warning colors
        val mosTempColor = if (t.controllerTemp > 80) {
            requireContext().getColor(com.bretthalliday.fdtuner.R.color.error_red)
        } else {
            requireContext().getColor(com.bretthalliday.fdtuner.R.color.text_primary)
        }
        binding.tvControllerTemp.setTextColor(mosTempColor)

        val motorTempColor = if (t.motorTemp > 120) {
            requireContext().getColor(com.bretthalliday.fdtuner.R.color.error_red)
        } else {
            requireContext().getColor(com.bretthalliday.fdtuner.R.color.text_primary)
        }
        binding.tvMotorTemp.setTextColor(motorTempColor)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
