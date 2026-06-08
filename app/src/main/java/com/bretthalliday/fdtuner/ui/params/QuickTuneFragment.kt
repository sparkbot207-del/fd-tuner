package com.bretthalliday.fdtuner.ui.params

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bretthalliday.fdtuner.R
import com.bretthalliday.fdtuner.databinding.FragmentQuickTuneBinding
import com.bretthalliday.fdtuner.model.ParamDef
import com.bretthalliday.fdtuner.model.ParamDocs
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Quick Tune — gathers the most-tuned params. Reads existing decoded values and writes through the
 * confirmed single-param path (viewModel.writeParam). The ONLY batch action is "reset curve to
 * 100%" (one confirm, then sequential grouped writes with partial-failure reporting). No other
 * bulk/apply-all anywhere.
 */
class QuickTuneFragment : Fragment() {

    private var _binding: FragmentQuickTuneBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ParamsViewModel by activityViewModels()

    private val curveParams get() = viewModel.fwCurveParams
    private val limitParam by lazy { viewModel.paramByName("LimitSpeed") }
    private val lineParam by lazy { viewModel.paramByName("MaxLineCurr") }
    private val phaseParam by lazy { viewModel.paramByName("MaxPhaseCurr") }
    private val accParam by lazy { viewModel.paramByName("MaxAcc") }
    private val decParam by lazy { viewModel.paramByName("MaxDec") }
    private val weakParam by lazy { viewModel.paramByName("WeakResponse") }

    private var selectedCurve = -1

    // Staged stepper values (display units) + dirty flags so live updates don't stomp edits.
    private val staged = HashMap<String, Int>()
    private val dirty = HashSet<String>()

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _binding = FragmentQuickTuneBinding.inflate(i, c, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupCurve()
        setupWeakResponse()
        setupStepper(lineParam, binding.tvLineVal, binding.btnLineMinus, binding.btnLinePlus, binding.btnLineApply, 5, 0, 9999)
        setupStepper(phaseParam, binding.tvPhaseVal, binding.btnPhaseMinus, binding.btnPhasePlus, binding.btnPhaseApply, 5, 0, 9999)
        setupStepper(accParam, binding.tvAccVal, binding.btnAccMinus, binding.btnAccPlus, binding.btnAccApply, 2, 0, 224)
        setupStepper(decParam, binding.tvDecVal, binding.btnDecMinus, binding.btnDecPlus, binding.btnDecApply, 2, 0, 224)

        binding.btnResetCurve.setOnClickListener { resetCurve() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.rawParams.collect { refreshLive() }
            }
        }
    }

    // ---- helpers ----

    private fun displayOf(p: ParamDef, raw: Int): Int {
        val e = p.extractValue(raw)
        return if (p.scale != 1f) (e / p.scale).roundToInt() else e
    }

    private fun liveDisplay(p: ParamDef?): Int? =
        p?.let { d -> viewModel.getRawWord(d)?.let { displayOf(d, it) } }

    /** Confirm + write ONE param via the existing write path (writeParam has the isDemo guard). */
    private fun confirmAndWrite(param: ParamDef, newDisplay: Int, onWritten: (() -> Unit)? = null) {
        if (param.addr == null) {
            Toast.makeText(requireContext(), "${param.name} is not mapped yet", Toast.LENGTH_SHORT).show()
            return
        }
        val cur = liveDisplay(param)
        val raw = (newDisplay * param.scale).toInt()
        val doc = ParamDocs.byName[param.name]
        val warn = doc?.warning?.takeIf { it.isNotBlank() }
        val msg = buildString {
            append("${param.name}\n\n")
            append("Current: ${cur ?: "—"} ${param.unit}\n")
            append("New:     $newDisplay ${param.unit}")
            if (param.isSafetyCritical) append("\n\n⚠️ Safety-critical parameter.")
            if (warn != null) append("\n\n⚠️ $warn")
            if (viewModel.isDemo) append("\n\n(Demo mode — local only.)")
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Confirm Write")
            .setMessage(msg)
            .setPositiveButton("Write") { _, _ ->
                viewModel.writeParam(param, raw)
                Toast.makeText(requireContext(), "${param.name} → $newDisplay ${param.unit}", Toast.LENGTH_SHORT).show()
                onWritten?.invoke()
            }
            .setNegativeButton("Cancel", null)
            .setOnDismissListener { refreshLive() }
            .show()
    }

    // ---- FW curve ----

    private fun setupCurve() {
        binding.curveView.onSelect = { i ->
            selectedCurve = i
            binding.tvSelRpm.text = curveParams.getOrNull(i)?.name ?: "—"
            binding.etSelValue.isEnabled = true
            val v = liveDisplay(curveParams.getOrNull(i))
            binding.etSelValue.setText(v?.toString() ?: "")
        }
        binding.curveView.onPreview = { _, v ->
            binding.tvFwTag.text = "editing"
            binding.etSelValue.setText(v.toString())
        }
        binding.curveView.onCommit = { i, v ->
            binding.tvFwTag.text = "drag or type"
            curveParams.getOrNull(i)?.let { confirmAndWrite(it, v) }
        }
        binding.etSelValue.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE && selectedCurve >= 0) {
                val v = binding.etSelValue.text.toString().toIntOrNull()?.coerceIn(0, 100)
                if (v != null) {
                    binding.curveView.setPointValue(selectedCurve, v)
                    curveParams.getOrNull(selectedCurve)?.let { confirmAndWrite(it, v) }
                }
                true
            } else false
        }
    }

    private fun refreshCurve() {
        if (binding.curveView.isEditing()) return
        val values = curveParams.map { liveDisplay(it)?.toFloat() ?: 0f }
        val limitRpm = liveDisplay(limitParam)
        val n = curveParams.size
        val frac = if (limitRpm != null && n > 1) ((limitRpm / 125f) / (n - 1)).coerceIn(0f, 1f) else -1f
        binding.curveView.setData(values, frac)
        if (selectedCurve >= 0) binding.curveView.setSelected(selectedCurve)
    }

    private fun resetCurve() {
        val n = curveParams.size
        AlertDialog.Builder(requireContext())
            .setTitle("Reset field-weakening curve")
            .setMessage("Set all $n field-weakening curve points to 100%?\n\nEach point is written through the normal confirmed write path.")
            .setPositiveButton("Set all to 100%") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val res = viewModel.writeSameValueGrouped(curveParams, 100)
                    if (!isAdded) return@launch
                    val ok = res.notWritten.isEmpty()
                    AlertDialog.Builder(requireContext())
                        .setTitle(if (ok) "Curve reset" else "Partial write")
                        .setMessage(
                            if (ok) "Wrote ${res.written.size} curve points to 100%."
                            else "Wrote: ${res.written.size}.\n\nNOT written (BLE drop?): ${res.notWritten.joinToString(", ")}"
                        )
                        .setPositiveButton("OK", null)
                        .show()
                    refreshCurve()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---- WeakResponse segments ----

    private var wrSegViews = listOf<TextView>()

    private fun setupWeakResponse() {
        val container = binding.llWrSegs
        container.removeAllViews()
        val segs = mutableListOf<TextView>()
        for (i in 0..7) {
            val tv = TextView(requireContext()).apply {
                text = i.toString()
                gravity = android.view.Gravity.CENTER
                textSize = 14f
                setPadding(0, 18, 0, 18)
                setTextColor(requireContext().getColor(R.color.text_secondary))
                setBackgroundResource(R.drawable.dash_gear_off)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { if (i > 0) marginStart = 10 }
            }
            val value = i
            tv.setOnClickListener {
                val p = weakParam
                if (p?.addr == null) {
                    Toast.makeText(requireContext(), "WeakResponse not mapped yet — locate it with the sniffer.", Toast.LENGTH_SHORT).show()
                } else {
                    confirmAndWrite(p, value)
                }
            }
            segs += tv
            container.addView(tv)
        }
        wrSegViews = segs
    }

    private fun renderWeakResponse() {
        val p = weakParam
        val cur = liveDisplay(p)
        if (p?.addr == null) binding.tvWrNote.text = "unmapped — map with sniffer"
        wrSegViews.forEachIndexed { i, tv ->
            val on = (i == cur)
            tv.setBackgroundResource(if (on) R.drawable.dash_gear_on else R.drawable.dash_gear_off)
            tv.setTextColor(requireContext().getColor(if (on) R.color.text_primary else R.color.text_secondary))
            tv.alpha = if (p?.addr == null) 0.5f else 1f
        }
        if (p?.addr != null) {
            binding.tvWrNote.text = when (cur) { 7 -> "7 = OFF"; 0 -> "0 = MAX weakening"; else -> "lower = stronger" }
        }
    }

    // ---- steppers ----

    private val applyButtons = HashMap<String, View>()

    private fun setupStepper(param: ParamDef?, tv: TextView, minus: View, plus: View, apply: View, step: Int, lo: Int, hi: Int) {
        if (param == null) { tv.text = "—"; apply.isEnabled = false; return }
        val name = param.name
        applyButtons[name] = apply
        apply.isEnabled = false
        minus.setOnClickListener { stepStaged(param, tv, -step, lo, hi) }
        plus.setOnClickListener { stepStaged(param, tv, step, lo, hi) }
        apply.setOnClickListener {
            val v = staged[name] ?: return@setOnClickListener
            if (!rampValid(param, v)) {
                Toast.makeText(requireContext(), "DEC must stay greater than ACC.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            confirmAndWrite(param, v) { dirty.remove(name); apply.isEnabled = false }
        }
    }

    private fun stepStaged(param: ParamDef, tv: TextView, delta: Int, lo: Int, hi: Int) {
        val name = param.name
        val base = staged[name] ?: liveDisplay(param) ?: 0
        val next = (base + delta).coerceIn(lo, hi)
        staged[name] = next
        dirty += name
        applyButtons[name]?.isEnabled = true
        renderStepperText(param, tv)
        updateRampHint()
    }

    /** ACC must stay strictly below DEC. Validate a proposed value against the paired control. */
    private fun rampValid(param: ParamDef, value: Int): Boolean {
        val acc = if (param.name == "MaxAcc") value else stepperValue(accParam)
        val dec = if (param.name == "MaxDec") value else stepperValue(decParam)
        if (param.name == "MaxAcc" || param.name == "MaxDec") {
            if (acc != null && dec != null) return dec > acc
        }
        return true
    }

    private fun stepperValue(p: ParamDef?): Int? =
        p?.let { staged[it.name] ?: liveDisplay(it) }

    private fun renderStepperText(param: ParamDef, tv: TextView) {
        val v = staged[param.name] ?: liveDisplay(param)
        tv.text = if (v == null) "—" else if (param.unit.isNotEmpty()) "$v ${param.unit}" else "$v"
    }

    private fun updateRampHint() {
        val acc = stepperValue(accParam); val dec = stepperValue(decParam)
        binding.tvRampHint.text = when {
            acc == null || dec == null -> "Range 0–224. Tap a number to apply."
            dec > acc -> "DEC ($dec) is above ACC ($acc). Range 0–224. Tap a number to apply."
            else -> "⚠ DEC must stay higher than ACC."
        }
    }

    // ---- live refresh ----

    private fun refreshLive() {
        refreshCurve()
        renderWeakResponse()
        listOf(lineParam to binding.tvLineVal, phaseParam to binding.tvPhaseVal,
               accParam to binding.tvAccVal, decParam to binding.tvDecVal).forEach { (p, tv) ->
            if (p == null) { tv.text = "—"; return@forEach }
            if (p.name !in dirty) {                       // not mid-edit -> follow live
                staged.remove(p.name)
                applyButtons[p.name]?.isEnabled = false
            }
            renderStepperText(p, tv)
        }
        updateRampHint()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
