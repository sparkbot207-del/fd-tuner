package com.bretthalliday.fdtuner.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.bretthalliday.fdtuner.databinding.FragmentSettingsBinding
import com.bretthalliday.fdtuner.ui.params.ParamsViewModel

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val paramsViewModel: ParamsViewModel by activityViewModels()

    // Access BLE manager via the activity
    private val bleManager get() = (requireActivity() as? com.bretthalliday.fdtuner.MainActivity)?.bleManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mgr = bleManager ?: return

        // ---- Speed units spinner ----
        val units = listOf("mph", "km/h")
        val unitAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, units)
        unitAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerSpeedUnits.adapter = unitAdapter

        val currentUnitIdx = if (mgr.useMph) 0 else 1
        binding.spinnerSpeedUnits.setSelection(currentUnitIdx)

        binding.spinnerSpeedUnits.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                mgr.useMph = (pos == 0)
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        // ---- Pole pairs ----
        binding.etPolePairs.setText(mgr.polePairs.toString())
        binding.etPolePairs.addTextChangedListener { text ->
            text?.toString()?.toIntOrNull()?.let { value ->
                if (value in 1..50) mgr.polePairs = value
            }
        }

        // ---- Wheel circumference ----
        binding.etWheelCircumference.setText(mgr.wheelCircumferenceMm.toString())
        binding.etWheelCircumference.addTextChangedListener { text ->
            text?.toString()?.toIntOrNull()?.let { value ->
                if (value in 100..5000) mgr.wheelCircumferenceMm = value
            }
        }

        // ---- Common presets for wheel circumference ----
        binding.btnPreset20inch.setOnClickListener {
            binding.etWheelCircumference.setText("1994")
        }
        binding.btnPreset24inch.setOnClickListener {
            binding.etWheelCircumference.setText("2391")
        }
        binding.btnPreset26inch.setOnClickListener {
            binding.etWheelCircumference.setText("2073")
        }

        // ---- Version / info ----
        binding.tvAppVersion.text = "FD Tuner v1.0 | Protocol: Fardriver BLE"
        binding.tvAppNotes.text =
            "Wheel circumference is used to calculate speed from RPM.\n" +
            "Pole pairs must match your motor to get accurate speed readings.\n" +
            "Example: QS205 = 23 pole pairs"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
