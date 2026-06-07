package com.bretthalliday.fdtuner.ui.errors

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bretthalliday.fdtuner.R
import com.bretthalliday.fdtuner.databinding.FragmentErrorCodesBinding
import com.bretthalliday.fdtuner.databinding.ItemErrorCodeBinding

data class ErrorCode(
    val beeps: String,         // "1", "2" ... "15", or "Special"
    val title: String,
    val severity: Severity,
    val meaning: String,
    val checks: List<String>,
    val isSpecial: Boolean = false
)

enum class Severity { INFO, WARNING, SERIOUS, CRITICAL }

object FardriverErrors {
    val beepCodes = listOf(
        ErrorCode("1", "Hall Failure", Severity.SERIOUS,
            "Hall sensor disconnected or not detected by the controller.",
            listOf("Hall sensor wiring and connectors", "Hall power supply (5V)", "Hall connector pins", "Motor hall sensors themselves")),
        ErrorCode("2", "Throttle Failure", Severity.WARNING,
            "Throttle was not at zero position during startup, or throttle voltage is out of range.",
            listOf("Throttle fully released before powering on", "Throttle voltage range (ThrottleLow / ThrottleHigh settings)", "Throttle wiring and connector", "Throttle sensor for damage")),
        ErrorCode("3", "Current Protection", Severity.WARNING,
            "Overcurrent protection was triggered — motor drew more current than the limit allows.",
            listOf("MaxLineCurr and MaxPhaseCurr settings", "Load on motor (stall conditions)", "Motor winding resistance", "Battery output capability")),
        ErrorCode("4", "Phase Overcurrent", Severity.SERIOUS,
            "Phase current exceeded the programmed limit.",
            listOf("MaxPhaseCurr setting", "Motor phase wiring", "Controller heat — may need cooling", "BoostPhaseCurr if boost is active")),
        ErrorCode("5", "Voltage Failure", Severity.WARNING,
            "Battery voltage is too high or too low — outside the controller's protection window.",
            listOf("Battery voltage vs RatedVoltage setting", "LowVolProtect and HighVolProtect settings", "Battery BMS and cell balance", "Charging system for overcharge")),
        ErrorCode("6", "Anti-Theft Alarm", Severity.INFO,
            "Anti-theft function is active and preventing operation.",
            listOf("Anti-theft is armed — disarm via app or key", "AntiTheft pin wiring", "Anti-theft settings in Functions menu")),
        ErrorCode("7", "Motor Overtemp", Severity.SERIOUS,
            "Motor temperature exceeded the protection threshold.",
            listOf("Motor temperature sensor wiring", "Motor cooling and airflow", "MotorTempProtect setting", "Riding conditions — may need to cool down", "TempSensor setting matches your sensor type")),
        ErrorCode("8", "Controller Overtemp", Severity.SERIOUS,
            "Controller MOSFET temperature exceeded the protection threshold.",
            listOf("Airflow around controller", "Controller mounting surface", "MosTempProtect setting", "MaxLineCurr and MaxPhaseCurr — may be set too high")),
        ErrorCode("9", "Phase Current Overflow", Severity.SERIOUS,
            "Internal phase current overflow — current sensing exceeded internal limits.",
            listOf("MaxPhaseCurr setting", "Phase wiring for shorts or damage", "Controller mounting and heat", "PID settings — improper tuning can cause current spikes")),
        ErrorCode("10", "Phase Zero Fault", Severity.SERIOUS,
            "Current sensing fault — phase current sensor is reading zero when it shouldn't be.",
            listOf("Phase current sensor", "Controller PCB for damage", "Phase wiring connections", "May indicate internal controller damage")),
        ErrorCode("11", "Phase Short", Severity.CRITICAL,
            "Short circuit detected in motor phases. One of the more serious faults.",
            listOf("Phase wires — look for damage or contact between phases", "Motor winding continuity", "Water intrusion into motor or controller", "Phase connectors and insulation")),
        ErrorCode("12", "Line Zero Fault", Severity.SERIOUS,
            "Battery line current sensing fault — reading zero when it shouldn't.",
            listOf("Battery current sensor", "Battery wiring and connectors", "Controller PCB for damage")),
        ErrorCode("13", "Upper MOSFET Failure", Severity.CRITICAL,
            "High-side MOSFET has failed. Usually indicates internal hardware damage.",
            listOf("Controller repair or replacement may be required", "Check for signs of burning or damage on controller PCB", "Verify the fault is repeatable before condemning controller")),
        ErrorCode("14", "Lower MOSFET Failure", Severity.CRITICAL,
            "Low-side MOSFET has failed. Usually indicates internal hardware damage.",
            listOf("Controller repair or replacement may be required", "Check for signs of burning or damage on controller PCB", "Verify the fault is repeatable before condemning controller")),
        ErrorCode("15", "Peak Current Protection", Severity.SERIOUS,
            "Hardware-level current protection activated — current spike exceeded physical hardware limits.",
            listOf("MaxPhaseCurr and MaxLineCurr settings", "Motor for mechanical binding or stall", "Phase wiring for intermittent shorts", "PID tuning — aggressive settings can spike current"))
    )

    val specialPatterns = listOf(
        ErrorCode("Special", "Normal Startup", Severity.INFO,
            "1 beep at power-on — controller started successfully.",
            listOf(), isSpecial = true),
        ErrorCode("Special", "Brake + Throttle Conflict", Severity.WARNING,
            "Continuous long beep — brake and throttle are active at the same time.",
            listOf("Release brake fully before applying throttle", "Brake sensor wiring", "Brake lever adjustment"), isSpecial = true),
        ErrorCode("Special", "Auto Learn Active", Severity.INFO,
            "2 short + 1 long (repeating) — controller is in Auto Learn / angle detection mode.",
            listOf("Follow the Auto Learn procedure: secure bike, apply and hold throttle until motor spins forward then reverse then stops"), isSpecial = true),
        ErrorCode("Special", "Firmware Verification Failed", Severity.CRITICAL,
            "2 short, pause, 1 short (repeating) — firmware CRC check failed.",
            listOf("Re-flash firmware", "Verify firmware file matches controller model", "Controller may need service"), isSpecial = true),
        ErrorCode("Special", "Firmware Mismatch", Severity.CRITICAL,
            "4 short + 1 long + 5 short (repeating) — firmware does not match controller hardware.",
            listOf("Re-flash with correct firmware for your controller model", "Verify controller model number"), isSpecial = true)
    )
}

private val ITEM_DIFF = object : DiffUtil.ItemCallback<ErrorCode>() {
    override fun areItemsTheSame(a: ErrorCode, b: ErrorCode) = a.beeps == b.beeps && a.title == b.title
    override fun areContentsTheSame(a: ErrorCode, b: ErrorCode) = a == b
}

class ErrorCodesFragment : Fragment() {

    private var _binding: FragmentErrorCodesBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: ErrorAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentErrorCodesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = ErrorAdapter()
        binding.recyclerErrors.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerErrors.adapter = adapter

        val allItems = FardriverErrors.beepCodes + FardriverErrors.specialPatterns
        adapter.submitList(allItems)

        // Beep counter buttons
        binding.btnBeepMinus.setOnClickListener {
            val current = binding.tvBeepCount.text.toString().toIntOrNull() ?: 1
            if (current > 1) {
                val newVal = current - 1
                binding.tvBeepCount.text = newVal.toString()
                highlightError(newVal)
            }
        }
        binding.btnBeepPlus.setOnClickListener {
            val current = binding.tvBeepCount.text.toString().toIntOrNull() ?: 0
            if (current < 15) {
                val newVal = current + 1
                binding.tvBeepCount.text = newVal.toString()
                highlightError(newVal)
            }
        }
        binding.btnBeepReset.setOnClickListener {
            binding.tvBeepCount.text = "?"
            adapter.setHighlighted(null)
        }
    }

    private fun highlightError(beeps: Int) {
        adapter.setHighlighted(beeps.toString())
        val index = FardriverErrors.beepCodes.indexOfFirst { it.beeps == beeps.toString() }
        if (index >= 0) binding.recyclerErrors.smoothScrollToPosition(index)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    inner class ErrorAdapter : ListAdapter<ErrorCode, ErrorAdapter.VH>(ITEM_DIFF) {

        private var highlighted: String? = null

        fun setHighlighted(beeps: String?) {
            highlighted = beeps
            notifyDataSetChanged()
        }

        inner class VH(private val b: ItemErrorCodeBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(item: ErrorCode, isHighlighted: Boolean) {
                b.tvBeepCount.text = if (item.isSpecial) "★" else item.beeps
                b.tvErrorTitle.text = item.title
                b.tvErrorMeaning.text = item.meaning

                // Severity color
                val (beepColor, borderColor) = when (item.severity) {
                    Severity.INFO     -> Pair(R.color.cyan_accent,   R.color.cyan_accent)
                    Severity.WARNING  -> Pair(R.color.warning_amber, R.color.warning_amber)
                    Severity.SERIOUS  -> Pair(R.color.eye_red,       R.color.eye_red)
                    Severity.CRITICAL -> Pair(R.color.error_red,     R.color.error_red)
                }
                b.tvBeepCount.setTextColor(ContextCompat.getColor(requireContext(), beepColor))

                // Build check list
                if (item.checks.isEmpty()) {
                    b.tvChecks.visibility = View.GONE
                } else {
                    b.tvChecks.visibility = View.VISIBLE
                    b.tvChecks.text = item.checks.joinToString("\n") { "• $it" }
                }

                // Highlight selected
                b.cardError.setCardBackgroundColor(
                    ContextCompat.getColor(
                        requireContext(),
                        if (isHighlighted) R.color.bg_elevated else R.color.bg_card
                    )
                )
                b.cardError.strokeWidth = if (isHighlighted) 3 else 0
                b.cardError.strokeColor = ContextCompat.getColor(requireContext(),
                    if (isHighlighted) borderColor else R.color.bg_card)

                // Expand/collapse checks on tap
                b.cardError.setOnClickListener {
                    b.tvChecks.visibility = if (b.tvChecks.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(ItemErrorCodeBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = getItem(position)
            holder.bind(item, item.beeps == highlighted)
        }


    }
}
