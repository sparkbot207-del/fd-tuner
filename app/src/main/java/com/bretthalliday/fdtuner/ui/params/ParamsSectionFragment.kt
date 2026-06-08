package com.bretthalliday.fdtuner.ui.params

import android.app.AlertDialog
import android.os.Bundle
import android.widget.Toast
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bretthalliday.fdtuner.data.ParamDefinitions
import com.bretthalliday.fdtuner.databinding.FragmentParamsSectionBinding
import com.bretthalliday.fdtuner.databinding.ItemParamBinding
import com.bretthalliday.fdtuner.model.ParamDef
import com.bretthalliday.fdtuner.model.ParamDocs
import com.bretthalliday.fdtuner.model.PidPreset
import kotlinx.coroutines.launch

class ParamsSectionFragment : Fragment() {

    private var _binding: FragmentParamsSectionBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ParamsViewModel by activityViewModels()
    private lateinit var paramAdapter: ParamAdapter

    private val sectionName: String
        get() = arguments?.getString("section") ?: ParamDefinitions.SECTION_PARAMETERS

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

        // Show PID warning banner only on the PID section
        binding.layoutPidWarning.visibility =
            if (sectionName == ParamDefinitions.SECTION_PID) View.VISIBLE else View.GONE

        // Tune Presets entry — PID section only
        if (sectionName == ParamDefinitions.SECTION_PID) {
            binding.btnTunePresets.visibility = View.VISIBLE
            binding.btnTunePresets.setOnClickListener { showPidPresetPicker() }
        }

        paramAdapter = ParamAdapter { param ->
            if (param.isWritable) {
                ParamEditDialog.show(
                    fragment = this,
                    param = param,
                    currentRaw = viewModel.getRawWord(param),
                    onConfirm = { newDisplayVal ->
                        viewModel.writeParam(param, newDisplayVal)
                    }
                )
            }
        }

        binding.recyclerParams.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = paramAdapter
        }

        val params = viewModel.paramsForSection(sectionName)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.rawParams.collect { rawMap ->
                    val items = params.map { p ->
                        ParamDisplay(
                            param = p,
                            displayValue = p.addr?.let { addr ->
                                rawMap[addr]?.let { raw -> p.formatDisplay(raw) }
                                    ?: if (rawMap.isEmpty()) "Connect to read" else "—"
                            } ?: "Addr unknown"
                        )
                    }
                    paramAdapter.submitList(items)
                }
            }
        }
    }

    // ---- PID tune presets ----

    private fun showPidPresetPicker() {
        val presets = ParamDocs.pidPresets
        val labels = presets.map { p ->
            "${p.name}\n   KI ${p.startKI}/${p.midKI}/${p.maxKI}   KP ${p.startKP}/${p.midKP}/${p.maxKP}"
        }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle("PID Tune Presets")
            .setItems(labels) { _, which -> confirmAndWritePreset(presets[which]) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** Stage the six values for review, list old -> new, then write via the confirmed path. */
    private fun confirmAndWritePreset(preset: PidPreset) {
        val mapping = listOf(
            "StartKI" to preset.startKI, "MidKI" to preset.midKI, "MaxKI" to preset.maxKI,
            "StartKP" to preset.startKP, "MidKP" to preset.midKP, "MaxKP" to preset.maxKP
        )
        val items = mapping.mapNotNull { (nm, v) -> viewModel.pidParamByName(nm)?.let { it to v } }
        if (items.isEmpty()) {
            Toast.makeText(requireContext(), "PID parameters not found", Toast.LENGTH_SHORT).show()
            return
        }
        val changes = items.joinToString("\n") { (param, newVal) ->
            val cur = viewModel.getRawWord(param)?.let { param.extractValue(it).toString() } ?: "—"
            "${param.name}:  $cur → $newVal"
        }
        val demoNote = if (viewModel.isDemo) "\n\n(Demo mode — values update locally only.)" else ""
        AlertDialog.Builder(requireContext())
            .setTitle("Apply \"${preset.name}\"?")
            .setMessage("This writes ${items.size} PID values:\n\n$changes$demoNote")
            .setPositiveButton("Write all") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val res = viewModel.writePidPreset(items)
                    val ok = res.notWritten.isEmpty()
                    val msg = if (ok)
                        "Wrote ${res.written.size} values: ${res.written.joinToString(", ")}"
                    else
                        "Wrote: ${res.written.joinToString(", ").ifEmpty { "none" }}\n\n" +
                        "NOT written (write failed — BLE drop?): ${res.notWritten.joinToString(", ")}"
                    if (!isAdded) return@launch
                    AlertDialog.Builder(requireContext())
                        .setTitle(if (ok) "PID preset applied" else "Partial write")
                        .setMessage(msg)
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    data class ParamDisplay(
        val param: ParamDef,
        val displayValue: String
    )

    private class ParamAdapter(
        private val onClick: (ParamDef) -> Unit
    ) : ListAdapter<ParamDisplay, ParamAdapter.VH>(DIFF) {

        inner class VH(private val b: ItemParamBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(item: ParamDisplay) {
                b.tvParamName.text = item.param.name
                b.tvParamValue.text = item.displayValue
                b.tvParamUnit.text = item.param.unit
                b.tvParamNotes.text = item.param.notes.ifEmpty { null }
                b.tvParamNotes.visibility = if (item.param.notes.isEmpty()) View.GONE else View.VISIBLE

                b.root.isEnabled = item.param.isWritable
                b.root.alpha = if (item.param.isWritable) 1.0f else 0.55f
                b.root.setOnClickListener {
                    if (item.param.isWritable) onClick(item.param)
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
