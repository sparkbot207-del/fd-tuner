package com.bretthalliday.fdtuner.ui.params

import android.app.AlertDialog
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
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
import com.bretthalliday.fdtuner.databinding.ItemParamSubheaderBinding
import com.bretthalliday.fdtuner.model.ParamDef
import com.bretthalliday.fdtuner.model.PidTuning
import kotlinx.coroutines.launch

class ParamsSectionFragment : Fragment() {

    private var _binding: FragmentParamsSectionBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ParamsViewModel by activityViewModels()
    private lateinit var rowAdapter: RowAdapter

    /** Grouped (sub-header + param) skeleton for this section, computed once. */
    private lateinit var groupedRows: List<ParamDefinitions.SectionRow>
    private lateinit var sectionParams: List<ParamDef>

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

        binding.layoutPidWarning.visibility = if (isPidSection) View.VISIBLE else View.GONE
        if (isPidSection) {
            binding.btnTunePresets.visibility = View.VISIBLE
            binding.btnTunePresets.text = "📋  Factory PID Table"
            binding.btnTunePresets.setOnClickListener { showPidReference() }
        }

        groupedRows = ParamDefinitions.groupedSection(sectionName)
        sectionParams = groupedRows.mapNotNull { (it as? ParamDefinitions.SectionRow.Item)?.param }
        logLayoutMisses()

        rowAdapter = RowAdapter { param -> onParamTapped(param) }
        binding.recyclerParams.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = rowAdapter
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.rawParams.collect { rawMap ->
                    val rows = groupedRows.map { row ->
                        when (row) {
                            is ParamDefinitions.SectionRow.Header -> DisplayRow.Header(row.title)
                            is ParamDefinitions.SectionRow.Item ->
                                DisplayRow.Param(buildDisplay(row.param, sectionParams, rawMap))
                        }
                    }
                    rowAdapter.submitList(rows)
                }
            }
        }
    }

    /** Log any layout names that matched nothing — a quick typo detector during development. */
    private fun logLayoutMisses() {
        ParamDefinitions.layoutUnmatched[sectionName]?.let { misses ->
            if (misses.isNotEmpty()) {
                Log.d("ParamsLayout", "Section \"$sectionName\" — layout names with no matching param: $misses")
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
            } ?: "Unmapped"
        }

        val note = if (isDerivedKp) "Read-only · KP = KI × ${PidTuning.KP_KI_RATIO}" else p.notes
        val editable = !isDerivedKp && p.isWritable
        return ParamDisplay(p, display, editable, note)
    }

    private fun onParamTapped(param: ParamDef) {
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

    /** Rows can be a sub-heading or a param. */
    sealed class DisplayRow {
        data class Header(val title: String) : DisplayRow()
        data class Param(val display: ParamDisplay) : DisplayRow()
    }

    private class RowAdapter(
        private val onClick: (ParamDef) -> Unit
    ) : ListAdapter<DisplayRow, RecyclerView.ViewHolder>(DIFF) {

        override fun getItemViewType(position: Int): Int =
            if (getItem(position) is DisplayRow.Header) TYPE_HEADER else TYPE_PARAM

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return if (viewType == TYPE_HEADER) {
                HeaderVH(ItemParamSubheaderBinding.inflate(inflater, parent, false))
            } else {
                ParamVH(ItemParamBinding.inflate(inflater, parent, false), onClick)
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val item = getItem(position)) {
                is DisplayRow.Header -> (holder as HeaderVH).bind(item)
                is DisplayRow.Param -> (holder as ParamVH).bind(item.display)
            }
        }

        class HeaderVH(private val b: ItemParamSubheaderBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(item: DisplayRow.Header) { b.tvSubHeader.text = item.title }
        }

        class ParamVH(
            private val b: ItemParamBinding,
            private val onClick: (ParamDef) -> Unit
        ) : RecyclerView.ViewHolder(b.root) {
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

        companion object {
            private const val TYPE_HEADER = 0
            private const val TYPE_PARAM = 1

            val DIFF = object : DiffUtil.ItemCallback<DisplayRow>() {
                override fun areItemsTheSame(a: DisplayRow, b: DisplayRow): Boolean = when {
                    a is DisplayRow.Header && b is DisplayRow.Header -> a.title == b.title
                    a is DisplayRow.Param && b is DisplayRow.Param ->
                        a.display.param.name == b.display.param.name &&
                            a.display.param.section == b.display.param.section
                    else -> false
                }
                override fun areContentsTheSame(a: DisplayRow, b: DisplayRow) = a == b
            }
        }
    }
}
