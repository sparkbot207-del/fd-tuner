package com.bretthalliday.fdtuner.settings

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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

        applyBottomInsets()

        val prefs = requireContext().getSharedPreferences("fd_settings", Context.MODE_PRIVATE)
        val mgr = bleManager ?: return

        // ---- Load persisted values into BleManager on open ----
        // NOTE: pole pairs is intentionally NOT loaded here — it is read live from the
        // controller (PolePairs param, word 0x14) by FardriverBleManager.
        mgr.useMph = prefs.getBoolean("use_mph", true)
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
        // Grounded standard roll-out values (cycle-computer charts). Brand-specific fat-tire
        // presets were removed because their circumference could not be reliably confirmed.
        binding.btnPreset20inch.setOnClickListener { binding.etWheelCircumference.setText("1590") }
        binding.btnPreset24inch.setOnClickListener { binding.etWheelCircumference.setText("1900") }
        binding.btnPreset26inch.setOnClickListener { binding.etWheelCircumference.setText("2070") }

        // ---- Alert settings ----
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

        // ---- Developer mode (gates the dev tools sub-group) ----
        val devMode = prefs.getBoolean("dev_mode", false)
        binding.switchDevMode.isChecked = devMode
        binding.groupDevTools.visibility = if (devMode) View.VISIBLE else View.GONE
        binding.switchDevMode.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("dev_mode", isChecked).apply()
            binding.groupDevTools.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
        binding.btnOpenSniffer.setOnClickListener {
            findNavController().navigate(R.id.action_settings_to_sniffer)
        }
        binding.btnOpenDiagnostics.setOnClickListener {
            findNavController().navigate(R.id.action_settings_to_diagnostics)
        }

        // ---- About (version is dynamic; the body copy lives in string resources) ----
        binding.tvAppVersion.text = "FarDriver Whisperer v${BuildConfig.VERSION_NAME}"
    }

    /**
     * Add the bottom window inset (navigation bar + IME) to the scroll content's bottom padding
     * so the last card always clears the system bars / bottom nav, instead of a hardcoded value.
     */
    private fun applyBottomInsets() {
        val content = binding.settingsContent
        val baseL = content.paddingLeft
        val baseT = content.paddingTop
        val baseR = content.paddingRight
        val baseB = content.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(content) { v, insets ->
            val sys = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
            )
            v.setPadding(baseL, baseT, baseR, baseB + sys.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(content)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
