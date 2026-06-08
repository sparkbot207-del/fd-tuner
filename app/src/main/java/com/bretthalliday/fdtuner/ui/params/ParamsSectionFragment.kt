package com.bretthalliday.fdtuner.ui.params

import android.app.AlertDialog
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bretthalliday.fdtuner.R
import com.bretthalliday.fdtuner.data.ParamDefinitions
import com.bretthalliday.fdtuner.databinding.FragmentParamsSectionBinding
import com.bretthalliday.fdtuner.databinding.ItemParamBinding
import com.bretthalliday.fdtuner.model.ParamDef
import com.bretthalliday.fdtuner.model.PidTuning
import kotlinx.coroutines.launch

class ParamsSectionFragment : Fragment() {

    private var _binding: FragmentParamsSectionBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ParamsViewModel by activityViewModels()
    private lateinit var paramAdapter: ParamAdapter

    private val sectionName: String
        get() = arguments?.getString("section") ?: ParamDefinitions.SECTION_PARAMETERS

    private val isPidSection: Boolean get() = sectionName == ParamDefinitions.SECTION_PID

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentParamsSectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvSectionTitle.text = sectionName

        // PID warning banner + read-only factory reference, PID section only
        binding.layoutPidWarning.visibility = if (isPidSection) View.VISIBLE else View.GONE
        if (isPidSection) {
            binding.btnTunePresets.visibility = View.VISIBLE
            binding.btnTunePresets.text = "📋  Factory PID Table"
            binding.btnTunePresets.setOnClickListener { showPidReference() }
        }

        paramAdapter = ParamAdapter { param -> onParamTapped(param) }

        binding.recyclerParams.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = paramAdapter
        }

        val params = viewModel.paramsForSection(sectionName)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.rawParams.collect { rawMap ->
                    val items = params.map { p -> buildDisplay(p, params, rawMap) }
                    paramAdapter.submitList(items)
                }
            }
        }
    }

    /** Build a display row, deriving KP values (KP = KI × ratio) and read-only state. */
    private fun buildDisplay(
        p: ParamDef,
        sectionParams: List<ParamDef>,
        rawMap: Map<Int, Int>
    ): ParamDisplay {
        val isDerivedKp = isPidSection && p.name in PidTuning.KP_NAMES
        val display: String = if (isDerivedKp) {
            // KP is derived from its paired KI; not read from the controller directly.
            val kiName = PidTuning.KI_FOR_KP[p.name]
            val kiDef = sectionParams.firstOrNull { it.name == kiName }
            val kiRaw = kiDef?.addr?.let { rawMap[it] }
            when {
                kiDef != null && kiRaw != null -> (kiDef.extractValue(kiRaw) * PidTuning.KP_KI_RATIO).toString()
                rawMap.isEmpty() -> "Connect to read"
                else -> "—"
            }
        } else {
            p.addr?.let { addr ->
                rawMap[addr]?.let { raw -> p.formatDisplay(raw) }
                    ?: if (rawMap.isEmpty()) "Connect to read" else "—"
            } ?: "Addr unknown"
        }

        val note = if (isDerivedKp) "Read-only · KP = KI × ${PidTuning.KP_KI_RATIO}" else p.notes
        val editable = !isDerivedKp && p.isWritable
        return ParamDisplay(p, display, editable, note)
    }

    /** Handle a tap on a param row. KP rows are read-only; KI rows also write the derived KP. */
    private fun onParamTapped(param: ParamDef) {
        // Derived KP — never independently editable.
        if (isPidSection && param.name in PidTuning.KP_NAMES) return

        val isPidKi = isPidSection && param.name in PidTuning.KI_NAMES
        if (isPidKi) {
            val kpName = PidTuning.KP_FOR_KI[param.name]
            val kpParam = kpName?.let { viewModel.pidParamByName(it) }
            ParamEditDialog.show(
                fragment = this,
                param = param,
                currentRaw = viewModel.getRawWord(param),
                onConfirm = { newKi ->
                    // Write KI, then the derived paired KP, via the existing confirmed path.
                    viewModel.writeParam(param, newKi)
                    if (kpParam != null) viewModel.writeParam(kpParam, newKi * PidTuning.KP_KI_RATIO)
                },
                extraWarning = PidTuning.WARNING,
                derivedConfirmLine = { newKi ->
                    val newKp = newKi * PidTuning.KP_KI_RATIO
                    val curKp = kpParam?.let { kp -> viewModel.getRawWord(kp)?.let { kp.extractValue(it) } }
                    "Also sets ${kpName}: ${curKp ?: "—"} → $newKp  (KP = KI × ${PidTuning.KP_KI_RATIO})"
                }
            )
            return
        }

        if (param.isWritable) {
            ParamEditDialog.show(
                fragment = this,
                param = param,
                currentRaw = viewModel.getRawWord(param),
                onConfirm = { newDisplayVal -> viewModel.writeParam(param, newDisplayVal) }
            )
        }
    }

    /** Read-only factory PID reference ladder + warning. No apply, no staging, no write. */
    private fun showPidReference() {
        val header = String.format("%-3s %-12s %-13s %s", "Set", "KI S/M/Max", "KP S/M/Max", "Label")
        val divider = "-".repeat(46)
        val rows = PidTuning.REFERENCE.joinToString("\n") { r ->
            String.format(
                "%-3d %-12s %-13s %s",
                r.set,
                "${r.startKI}/${r.midKI}/${r.maxKI}",
                "${r.startKP}/${r.midKP}/${r.maxKP}",
                r.label
            )
        }
        val table = "$header\n$divider\n$rows"
        val message = "⚠️ ${PidTuning.WARNING}\n\n$divider\n$table"

        val tv = TextView(requireContext()).apply {
            typeface = Typeface.MONOSPACE
            textSize = 12f
            setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
            setPadding(48, 32, 48, 24)
            text = message
        }
        val scroll = ScrollView(requireContext()).apply { addView(tv) }

        AlertDialog.Builder(requireContext())
            .setTitle("Factory PID Reference (read-only)")
            .setView(scroll)
            .setPositiveButton("Close", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    data class ParamDisplay(
        val param: ParamDef,
        val displayValue: String,
        val editable: Boolean,
        val note: String
    )

    private class ParamAdapter(
        private val onClick: (ParamDef) -> Unit
    ) : ListAdapter<ParamDisplay, ParamAdapter.VH>(DIFF) {

        inner class VH(private val b: ItemParamBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(item: ParamDisplay) {
                b.tvParamName.text = item.param.name
                b.tvParamValue.text = item.displayValue
                b.tvParamUnit.text = item.param.unit
                b.tvParamNotes.text = item.note.ifEmpty { null }
                b.tvParamNotes.visibility = if (item.note.isEmpty()) View.GONE else View.VISIBLE

                b.root.isEnabled = item.editable
                b.root.alpha = if (item.editable) 1.0f else 0.55f
                b.root.setOnClickListener {
                    if (item.editable) onClick(item.param)
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val b = ItemParamBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(b)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(getItem(position))
        }

        companion object {
            val DIFF = object : DiffUtil.ItemCallback<ParamDisplay>() {
                override fun areItemsTheSame(a: ParamDisplay, b: ParamDisplay) =
                    a.param.name == b.param.name && a.param.section == b.param.section
                override fun areContentsTheSame(a: ParamDisplay, b: ParamDisplay) = a == b
            }
        }
    }
}
