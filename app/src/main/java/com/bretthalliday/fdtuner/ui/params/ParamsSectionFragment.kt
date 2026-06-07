package com.bretthalliday.fdtuner.ui.params

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
import com.bretthalliday.fdtuner.data.ParamDefinitions
import com.bretthalliday.fdtuner.databinding.FragmentParamsSectionBinding
import com.bretthalliday.fdtuner.databinding.ItemParamBinding
import com.bretthalliday.fdtuner.model.ParamDef
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

        paramAdapter = ParamAdapter { param ->
            if (viewModel.isDemo) {
                android.widget.Toast.makeText(
                    requireContext(),
                    "Demo mode — connect to a real controller to write params",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                return@ParamAdapter
            }
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
