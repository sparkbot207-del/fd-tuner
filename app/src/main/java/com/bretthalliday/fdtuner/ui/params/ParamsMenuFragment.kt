package com.bretthalliday.fdtuner.ui.params

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bretthalliday.fdtuner.R
import com.bretthalliday.fdtuner.databinding.FragmentParamsMenuBinding
import com.bretthalliday.fdtuner.databinding.ItemParamsSectionBinding

class ParamsMenuFragment : Fragment() {

    private var _binding: FragmentParamsMenuBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ParamsViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentParamsMenuBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = SectionAdapter { section ->
            findNavController().navigate(
                R.id.action_paramsMenu_to_paramsSection,
                bundleOf("section" to section)
            )
        }

        binding.recyclerSections.apply {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter = adapter
        }

        adapter.submitList(viewModel.sections)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private class SectionAdapter(
        private val onClick: (String) -> Unit
    ) : ListAdapter<String, SectionAdapter.VH>(DIFF) {

        inner class VH(private val b: ItemParamsSectionBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(section: String) {
                b.tvSectionName.text = section
                b.root.setOnClickListener { onClick(section) }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val b = ItemParamsSectionBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return VH(b)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(getItem(position))
        }

        companion object {
            val DIFF = object : DiffUtil.ItemCallback<String>() {
                override fun areItemsTheSame(a: String, b: String) = a == b
                override fun areContentsTheSame(a: String, b: String) = a == b
            }
        }
    }
}
