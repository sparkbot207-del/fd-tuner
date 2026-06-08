package com.bretthalliday.fdtuner.ui.params

import android.os.Bundle
import android.widget.Toast
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.core.view.MenuProvider
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bretthalliday.fdtuner.R
import com.bretthalliday.fdtuner.databinding.FragmentParamsMenuBinding
import com.bretthalliday.fdtuner.databinding.ItemParamBinding
import com.bretthalliday.fdtuner.databinding.ItemParamsSectionBinding
import com.bretthalliday.fdtuner.model.ParamDef
import com.bretthalliday.fdtuner.model.PidTuning
import kotlinx.coroutines.launch

class ParamsMenuFragment : Fragment() {

    private var _binding: FragmentParamsMenuBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ParamsViewModel by activityViewModels()

    // Search adapter for flat filtered results (Feature 5)
    private lateinit var searchAdapter: FlatParamAdapter
    private var backPressedCallback: OnBackPressedCallback? = null

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

        // ---- Section list (existing) ----
        val sectionAdapter = SectionAdapter { section ->
            findNavController().navigate(
                R.id.action_paramsMenu_to_paramsSection,
                bundleOf("section" to section)
            )
        }
        binding.recyclerSections.apply {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter = sectionAdapter
        }
        sectionAdapter.submitList(viewModel.sections)

        // ---- Feature 3: add history menu via MenuProvider ----
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.params_menu_toolbar, menu)
            }
            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_changelog -> {
                        ChangeLogBottomSheet().show(
                            childFragmentManager, ChangeLogBottomSheet.TAG
                        )
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)

        // ---- Feature 5: search / filter ----
        searchAdapter = FlatParamAdapter { param ->
            when {
                // Derived KP is never independently editable (KP = KI x ratio).
                param.name in PidTuning.KP_NAMES -> {
                    Toast.makeText(
                        requireContext(),
                        "${param.name} is derived from its KI (KP = KI x ${PidTuning.KP_KI_RATIO}). Edit the KI instead.",
                        Toast.LENGTH_LONG
                    ).show()
                }
                param.isWritable -> {
                    ParamEditDialog.show(
                        fragment = this,
                        param = param,
                        currentRaw = viewModel.getRawWord(param),
                        onConfirm = { newDisplayVal -> viewModel.writeParam(param, newDisplayVal) }
                    )
                }
            }
        }
        binding.recyclerSearchResults.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = searchAdapter
        }

        // Observe rawParams to refresh search results when values change
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.rawParams.collect { rawMap ->
                    val query = binding.etSearch.text?.toString() ?: ""
                    if (query.isNotEmpty()) {
                        updateSearchResults(query, rawMap)
                    }
                }
            }
        }

        binding.etSearch.addTextChangedListener { text ->
            val query = text?.toString() ?: ""
            if (query.isEmpty()) {
                showSectionView()
            } else {
                showSearchResults(query)
            }
        }

        // Back press: clear search if active
        backPressedCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                clearSearch()
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner, backPressedCallback!!
        )
    }

    private fun showSectionView() {
        binding.recyclerSections.visibility = View.VISIBLE
        binding.recyclerSearchResults.visibility = View.GONE
        backPressedCallback?.isEnabled = false
    }

    private fun showSearchResults(query: String) {
        binding.recyclerSections.visibility = View.GONE
        binding.recyclerSearchResults.visibility = View.VISIBLE
        backPressedCallback?.isEnabled = true
        updateSearchResults(query, viewModel.rawParams.value)
    }

    private fun updateSearchResults(query: String, rawMap: Map<Int, Int>) {
        val filtered = viewModel.allParams.filter {
            it.name.contains(query, ignoreCase = true)
        }.map { param ->
            FlatParamDisplay(
                param = param,
                displayValue = param.addr?.let { addr ->
                    rawMap[addr]?.let { raw -> param.formatDisplay(raw) }
                        ?: if (rawMap.isEmpty()) "Connect to read" else "—"
                } ?: "Addr unknown"
            )
        }
        searchAdapter.submitList(filtered)
    }

    private fun clearSearch() {
        binding.etSearch.setText("")
        showSectionView()
        // Hide keyboard
        val imm = requireContext().getSystemService(InputMethodManager::class.java)
        imm.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ---- Section adapter (existing) ----

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

    // ---- Flat search results adapter (Feature 5) ----

    data class FlatParamDisplay(val param: ParamDef, val displayValue: String)

    private class FlatParamAdapter(
        private val onClick: (ParamDef) -> Unit
    ) : ListAdapter<FlatParamDisplay, FlatParamAdapter.VH>(FLAT_DIFF) {

        inner class VH(private val b: ItemParamBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(item: FlatParamDisplay) {
                b.tvParamName.text = item.param.name
                b.tvParamValue.text = item.displayValue
                b.tvParamUnit.text = item.param.unit
                b.tvParamNotes.text = item.param.section
                b.tvParamNotes.visibility = View.VISIBLE // show section as subtitle

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
            val FLAT_DIFF = object : DiffUtil.ItemCallback<FlatParamDisplay>() {
                override fun areItemsTheSame(a: FlatParamDisplay, b: FlatParamDisplay) =
                    a.param.name == b.param.name && a.param.section == b.param.section
                override fun areContentsTheSame(a: FlatParamDisplay, b: FlatParamDisplay) = a == b
            }
        }
    }
}
