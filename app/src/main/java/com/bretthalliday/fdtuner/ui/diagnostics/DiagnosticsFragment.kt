package com.bretthalliday.fdtuner.ui.diagnostics

import android.os.Bundle
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
import com.bretthalliday.fdtuner.databinding.FragmentDiagnosticsBinding
import com.bretthalliday.fdtuner.databinding.ItemParamBinding
import com.bretthalliday.fdtuner.model.ParamDef
import com.bretthalliday.fdtuner.ui.params.ParamsViewModel
import kotlinx.coroutines.launch

/**
 * Read-only live state / telemetry view.
 *
 * Lists every SECTION_DIAGNOSTICS param (the ~121 live state and telemetry bits from blocks
 * D6/E2/E8/EE/F4/FA) with values pulled from the same rawParams decode the sniffer uses.
 * There are deliberately NO write controls or edit dialogs here — diagnostics params are
 * read-only and must never reach the write path.
 */
class DiagnosticsFragment : Fragment() {

    private var _binding: FragmentDiagnosticsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ParamsViewModel by activityViewModels()
    private lateinit var adapter: DiagnosticsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDiagnosticsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = DiagnosticsAdapter()
        binding.recyclerDiagnostics.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@DiagnosticsFragment.adapter
        }

        val params = viewModel.diagnosticsParams

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.rawParams.collect { rawMap ->
                    val items = params.map { p ->
                        DiagDisplay(
                            param = p,
                            displayValue = p.addr?.let { addr ->
                                rawMap[addr]?.let { raw -> p.formatDisplay(raw) }
                                    ?: if (rawMap.isEmpty()) "Connect to read" else "—"
                            } ?: "Addr unknown"
                        )
                    }
                    adapter.submitList(items)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    data class DiagDisplay(
        val param: ParamDef,
        val displayValue: String
    )

    /** Read-only adapter — no click listener, no edit path. */
    private class DiagnosticsAdapter :
        ListAdapter<DiagDisplay, DiagnosticsAdapter.VH>(DIFF) {

        inner class VH(private val b: ItemParamBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(item: DiagDisplay) {
                b.tvParamName.text = item.param.name
                b.tvParamValue.text = item.displayValue
                b.tvParamUnit.text = item.param.unit
                b.tvParamNotes.text = item.param.notes.ifEmpty { null }
                b.tvParamNotes.visibility =
                    if (item.param.notes.isEmpty()) View.GONE else View.VISIBLE

                // Read-only: not clickable, no edit dialog.
                b.root.isClickable = false
                b.root.isEnabled = true
                b.root.alpha = 1.0f
                b.root.setOnClickListener(null)
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val b = ItemParamBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return VH(b)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(getItem(position))
        }

        companion object {
            val DIFF = object : DiffUtil.ItemCallback<DiagDisplay>() {
                override fun areItemsTheSame(a: DiagDisplay, b: DiagDisplay) =
                    a.param.name == b.param.name && a.param.section == b.param.section
                override fun areContentsTheSame(a: DiagDisplay, b: DiagDisplay) = a == b
            }
        }
    }
}
