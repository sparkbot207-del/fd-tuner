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
import androidx.navigation.fragment.findNavController
import com.bretthalliday.fdtuner.BuildConfig
import com.bretthalliday.fdtuner.MainActivity
import com.bretthalliday.fdtuner.R
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

        // ---- Feature 6: Alert settings ----
        binding.switchAlertsEnabled.isChecked = prefs.getBoolean("alerts_enabled", true)
        binding.switchAlertsEnabled.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("alerts_enabled", isChecked).apply()
        }

        val savedMotorTemp = prefs.getInt("alert_motor_temp", 80)
        binding.etAlertMotorTemp.setText(savedMotorTemp.toString())
        binding.etAlertMotorTemp.addTextChangedListener { text ->
            text?.toString()?.toIntOrNull()?.let { v ->
                if (v in 30..200) prefs.edit().putInt("alert_motor_temp", v).apply()
            }
        }

        val savedBattLow = prefs.getFloat("alert_batt_low", 60.0f)
        binding.etAlertBattLow.setText(savedBattLow.toString())
        binding.etAlertBattLow.addTextChangedListener { text ->
            text?.toString()?.toFloatOrNull()?.let { v ->
                if (v in 20.0f..120.0f) prefs.edit().putFloat("alert_batt_low", v).apply()
            }
        }

        // ---- Developer mode ----
        val devMode = prefs.getBoolean("dev_mode", false)
        binding.switchDevMode.isChecked = devMode
        binding.btnOpenSniffer.visibility = if (devMode) View.VISIBLE else View.GONE

        binding.switchDevMode.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("dev_mode", isChecked).apply()
            binding.btnOpenSniffer.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        binding.btnOpenSniffer.setOnClickListener {
            findNavController().navigate(R.id.action_settings_to_sniffer)
        }

        binding.btnOpenDiagnostics.setOnClickListener {
            findNavController().navigate(R.id.action_settings_to_diagnostics)
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
