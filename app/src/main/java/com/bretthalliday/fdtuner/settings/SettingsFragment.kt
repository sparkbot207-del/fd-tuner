package com.bretthalliday.fdtuner.settings

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import com.bretthalliday.fdtuner.BuildConfig
import com.bretthalliday.fdtuner.MainActivity
import com.bretthalliday.fdtuner.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val bleManager get() = (requireActivity() as? MainActivity)?.bleManager

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefs = requireContext().getSharedPreferences("fd_settings", Context.MODE_PRIVATE)
        val mgr = bleManager ?: return

        // ---- Load persisted values into BleManager on open ----
        mgr.useMph = prefs.getBoolean("use_mph", true)
        mgr.polePairs = prefs.getInt("pole_pairs", 23)
        mgr.wheelCircumferenceMm = prefs.getInt("wheel_circ_mm", 2100)

        // ---- Speed units spinner ----
        val units = listOf("mph", "km/h")
        binding.spinnerSpeedUnits.adapter =
            ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, units).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
        binding.spinnerSpeedUnits.setSelection(if (mgr.useMph) 0 else 1)
        binding.spinnerSpeedUnits.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, v: View?, pos: Int, id: Long) {
                mgr.useMph = (pos == 0)
                prefs.edit().putBoolean("use_mph", mgr.useMph).apply()
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        // ---- Pole pairs ----
        binding.etPolePairs.setText(mgr.polePairs.toString())
        binding.etPolePairs.addTextChangedListener { text ->
            text?.toString()?.toIntOrNull()?.let { v ->
                if (v in 1..50) {
                    mgr.polePairs = v
                    prefs.edit().putInt("pole_pairs", v).apply()
                }
            }
        }

        // ---- Wheel circumference ----
        binding.etWheelCircumference.setText(mgr.wheelCircumferenceMm.toString())
        binding.etWheelCircumference.addTextChangedListener { text ->
            text?.toString()?.toIntOrNull()?.let { v ->
                if (v in 100..5000) {
                    mgr.wheelCircumferenceMm = v
                    prefs.edit().putInt("wheel_circ_mm", v).apply()
                }
            }
        }

        // ---- Wheel circumference presets ----
        val presets = mapOf(
            binding.btnPresetOnyxRcr    to Pair("Onyx RCR (17\")",    2150),
            binding.btnPresetSuper73    to Pair("Super73 (20\")",      1994),
            binding.btnPreset20inch     to Pair("20\" standard",       1994),
            binding.btnPreset24inch     to Pair("24\" standard",       1899),
            binding.btnPreset26inch     to Pair("26\" standard",       2073),
        )
        presets.forEach { (btn, pair) ->
            val (_, mm) = pair
            btn.setOnClickListener {
                binding.etWheelCircumference.setText(mm.toString())
            }
        }

        // ---- About ----
        binding.tvAppVersion.text = "Fardriver Whisperer v${BuildConfig.VERSION_NAME}"
        binding.tvAppNotes.text =
            "Wheel circumference is used to calculate speed from RPM.\n" +
            "Pole pairs must match your motor for accurate speed readings.\n\n" +
            "QS205 = 23 pole pairs\n" +
            "QS273 = 23 pole pairs\n" +
            "MAC motor = typically 8–16 pole pairs\n\n" +
            "Settings are saved automatically."
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
