package com.bretthalliday.fdtuner.ui.dashboard

import android.content.res.ColorStateList
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bretthalliday.fdtuner.MainActivity
import com.bretthalliday.fdtuner.R
import com.bretthalliday.fdtuner.ble.AlertMonitor
import com.bretthalliday.fdtuner.ble.ConnectionState
import com.bretthalliday.fdtuner.databinding.FragmentDashboardBinding
import com.bretthalliday.fdtuner.model.TelemetryData
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Read-only live dashboard. Lays out the existing decoded telemetry plus two DERIVED display
 * values (power, phase-current magnitude). No writes anywhere on this screen; the gear strip is
 * a display of the controller-reported gear, not a control.
 */
class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DashboardViewModel by activityViewModels()

    companion object {
        // ---- Temp color-zone thresholds (PLACEHOLDERS — confirm real derate limits) ----
        // Controller (MOSFET)
        private const val CTRL_GREEN_MAX = 60      // °C — green below
        private const val CTRL_AMBER_MAX = 85      // °C — amber 60–85, red above
        private const val CTRL_BAR_SCALE = 120f    // °C full-scale for the zone bar
        // Motor
        private const val MOTOR_GREEN_MAX = 90     // °C
        private const val MOTOR_AMBER_MAX = 120    // °C
        private const val MOTOR_BAR_SCALE = 150f   // °C full-scale
        // SOC fill zones
        private const val SOC_GREEN_MIN = 40       // % — green at/above
        private const val SOC_AMBER_MIN = 15       // % — amber 15–40, red below
    }

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
                viewModel.telemetry.collect { telem -> updateDashboard(telem) }
            }
        }

        binding.btnDisconnect.setOnClickListener {
            viewModel.disconnect()
        }

        // Telemetry alert monitoring (unchanged)
        val bleManager = (requireActivity() as? MainActivity)?.bleManager
        if (bleManager != null) {
            val prefs = requireContext().getSharedPreferences("fd_settings", android.content.Context.MODE_PRIVATE)
            val alertMonitor = AlertMonitor(bleManager, prefs)

            viewLifecycleOwner.lifecycleScope.launch {
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    alertMonitor.alertFlow().collect { event ->
                        binding.root.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        Snackbar.make(binding.root, event.message, 5000).show()
                    }
                }
            }
            viewLifecycleOwner.lifecycleScope.launch {
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    bleManager.connectionState.collect { state ->
                        if (state is ConnectionState.Disconnected) alertMonitor.reset()
                    }
                }
            }
        }
    }

    private fun color(id: Int) = requireContext().getColor(id)

    /** "12.3 V" with the unit rendered smaller + muted. */
    private fun valueUnit(value: String, unit: String): CharSequence {
        val full = "$value $unit"
        val sp = SpannableString(full)
        val start = value.length + 1
        sp.setSpan(RelativeSizeSpan(0.55f), start, full.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sp.setSpan(ForegroundColorSpan(color(R.color.text_secondary)), start, full.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return sp
    }

    private fun setBar(fill: View, spacer: View, pct: Float) {
        val p = pct.coerceIn(0f, 100f)
        (fill.layoutParams as LinearLayout.LayoutParams).weight = p
        (spacer.layoutParams as LinearLayout.LayoutParams).weight = 100f - p
        fill.requestLayout()
        spacer.requestLayout()
    }

    private fun fillColor(fill: View, colorInt: Int, radiusDp: Float) {
        val r = radiusDp * resources.displayMetrics.density
        val d = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = r
            setColor(colorInt)
        }
        fill.background = d
    }

    private fun updateDashboard(t: TelemetryData?) {
        if (t == null) {
            binding.tvConnStatus.text = "—"
            binding.viewConnDot.backgroundTintList = ColorStateList.valueOf(color(R.color.text_hint))
            binding.tvSpeed.text = "--"
            binding.tvSpeedUnit.text = "mph"
            binding.tvVoltage.text = "--.- V"
            binding.tvLineAmps.text = "--.- A"
            binding.tvPhaseAmps.text = "--.- A"
            binding.tvRpm.text = "----"
            binding.tvPower.text = "--.- kW"
            binding.tvPowerTag.text = "—"
            binding.tvSocPct.text = "--%"
            binding.tvControllerTemp.text = "--°C"
            binding.tvMotorTemp.text = "--°C"
            setBar(binding.socFill, binding.socSpacer, 0f)
            setBar(binding.ctrlFill, binding.ctrlSpacer, 0f)
            setBar(binding.motFill, binding.motSpacer, 0f)
            highlightGear(-1)
            return
        }

        // Status row
        binding.viewConnDot.backgroundTintList = ColorStateList.valueOf(color(R.color.soc_green))
        binding.tvConnStatus.text = "CONNECTED · %.0fV".format(t.voltage)

        // Speed hero
        binding.tvSpeed.text = "%.1f".format(t.speed)
        binding.tvSpeedUnit.text = t.speedUnit.uppercase()

        // Gear strip (display only)
        highlightGear(t.gear)

        // Derived POWER = V × lineAmps / 1000
        val kw = t.voltage * t.lineCurrent / 1000f
        binding.tvPower.text = valueUnit("%.1f".format(abs(kw)), "kW")
        if (kw < 0f) {
            binding.tvPowerTag.text = "⟲ REGEN"
            binding.tvPowerTag.setTextColor(color(R.color.cyan_accent))
        } else {
            binding.tvPowerTag.text = "⚡ DRIVE"
            binding.tvPowerTag.setTextColor(color(R.color.pink_primary))
        }

        // Voltage
        binding.tvVoltage.text = valueUnit("%.1f".format(t.voltage), "V")

        // Line amps — cyan when negative (regen), normal when positive
        val la = t.lineCurrent
        val laStr = (if (la < 0f) "−" else "") + "%.1f".format(abs(la))
        binding.tvLineAmps.text = valueUnit(laStr, "A")
        binding.tvLineAmps.setTextColor(color(if (la < 0f) R.color.cyan_accent else R.color.text_primary))

        // Derived PHASE AMPS = (2/√3)·√(Ia² + Ia·Ic + Ic²)
        val ia = t.aPhaseCurrent
        val ic = t.cPhaseCurrent
        val iPhase = (2f / sqrt(3f)) * sqrt(ia * ia + ia * ic + ic * ic)
        binding.tvPhaseAmps.text = valueUnit("%.1f".format(iPhase), "A")

        // RPM
        binding.tvRpm.text = "${t.rpm}"

        // SOC bar
        binding.tvSocPct.text = "${t.soc}%"
        val socColorId = when {
            t.soc >= SOC_GREEN_MIN -> R.color.soc_green
            t.soc >= SOC_AMBER_MIN -> R.color.warning_amber
            else -> R.color.error_red
        }
        binding.tvSocPct.setTextColor(color(socColorId))
        fillColor(binding.socFill, color(socColorId), 8f)
        setBar(binding.socFill, binding.socSpacer, t.soc.toFloat())

        // Controller temp zone
        applyTempZone(
            t.controllerTemp, binding.tvControllerTemp, binding.ctrlFill, binding.ctrlSpacer,
            CTRL_GREEN_MAX, CTRL_AMBER_MAX, CTRL_BAR_SCALE
        )
        // Motor temp zone
        applyTempZone(
            t.motorTemp, binding.tvMotorTemp, binding.motFill, binding.motSpacer,
            MOTOR_GREEN_MAX, MOTOR_AMBER_MAX, MOTOR_BAR_SCALE
        )
    }

    private fun applyTempZone(
        v: Int, tv: TextView, fill: View, spacer: View,
        greenMax: Int, amberMax: Int, scale: Float
    ) {
        tv.text = valueUnit("$v", "°C")
        val zoneColorId = when {
            v < greenMax -> R.color.soc_green
            v < amberMax -> R.color.warning_amber
            else -> R.color.error_red
        }
        // Number turns red in the danger (red) zone, normal otherwise.
        tv.setTextColor(color(if (v >= amberMax) R.color.error_red else R.color.text_primary))
        fillColor(fill, color(zoneColorId), 4f)
        setBar(fill, spacer, v / scale * 100f)
    }

    /** Highlight the segment for [gear] (0=P,1,2,3); -1 = none. Display only. */
    private fun highlightGear(gear: Int) {
        val segs = listOf(
            Triple(binding.gearSeg0, binding.tvGear0Num, binding.tvGear0Sub),
            Triple(binding.gearSeg1, binding.tvGear1Num, binding.tvGear1Sub),
            Triple(binding.gearSeg2, binding.tvGear2Num, binding.tvGear2Sub),
            Triple(binding.gearSeg3, binding.tvGear3Num, binding.tvGear3Sub)
        )
        segs.forEachIndexed { i, (seg, num, sub) ->
            val on = i == gear
            seg.setBackgroundResource(if (on) R.drawable.dash_gear_on else R.drawable.dash_gear_off)
            num.setTextColor(color(if (on) R.color.text_primary else R.color.text_secondary))
            sub.setTextColor(if (on) color(R.color.text_primary) else color(R.color.text_hint))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
