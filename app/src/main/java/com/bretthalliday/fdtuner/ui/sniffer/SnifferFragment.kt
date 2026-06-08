package com.bretthalliday.fdtuner.ui.sniffer

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.os.bundleOf
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
import com.bretthalliday.fdtuner.ble.SniffPacket
import com.bretthalliday.fdtuner.databinding.FragmentSnifferBinding
import com.bretthalliday.fdtuner.databinding.ItemSnifferBlockBinding
import kotlinx.coroutines.launch

class SnifferFragment : Fragment() {

    private var _binding: FragmentSnifferBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SnifferViewModel by activityViewModels()
    private lateinit var adapter: BlockAdapter

    // Addresses that are currently flashing (orange highlight)
    private val flashingAddresses = mutableSetOf<Int>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSnifferBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = BlockAdapter(flashingAddresses) { baseAddr ->
            findNavController().navigate(
                R.id.action_snifferFragment_to_snifferDetailFragment,
                bundleOf("baseAddr" to baseAddr)
            )
        }

        binding.rvSnifferBlocks.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@SnifferFragment.adapter
            setHasFixedSize(true)
        }

        // Initial list of all known addresses (items will show "—" until data arrives)
        adapter.submitList(viewModel.blockAddresses.map { addr ->
            BlockItem(addr, viewModel.latestByAddress.value[addr])
        })

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                // Update rows when packets arrive
                launch {
                    viewModel.latestByAddress.collect { latest ->
                        adapter.submitList(viewModel.blockAddresses.map { addr ->
                            BlockItem(addr, latest[addr])
                        })
                    }
                }

                // Flash rows when words change
                launch {
                    viewModel.changedAddresses.collect { baseAddr ->
                        flashingAddresses.add(baseAddr)
                        val idx = viewModel.blockAddresses.indexOf(baseAddr)
                        if (idx >= 0) adapter.notifyItemChanged(idx)
                        // Clear flash after 500 ms
                        view.postDelayed({
                            if (isAdded) {
                                flashingAddresses.remove(baseAddr)
                                if (idx >= 0) adapter.notifyItemChanged(idx)
                            }
                        }, 500)
                    }
                }

                // Packet counter subtitle
                launch {
                    viewModel.packetCount.collect { count ->
                        binding.tvPacketCount.text =
                            if (count == 0) "Waiting for packets…"
                            else "$count packets captured"
                    }
                }
            }
        }

        // Toolbar actions
        binding.btnExportCsv.setOnClickListener {
            val intent = viewModel.buildShareIntent(requireContext())
            startActivity(android.content.Intent.createChooser(intent, "Export sniffer CSV"))
        }

        binding.btnClearSession.setOnClickListener {
            viewModel.clearSession()
        }

        // Annotation: free-text done key
        binding.etAnnotation.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                val text = binding.etAnnotation.text.toString().trim()
                if (text.isNotEmpty()) {
                    viewModel.addAnnotation(text)
                    binding.etAnnotation.text?.clear()
                }
                true
            } else false
        }

        binding.btnAnnotateFreeText.setOnClickListener {
            val text = binding.etAnnotation.text.toString().trim()
            if (text.isNotEmpty()) {
                viewModel.addAnnotation(text)
                binding.etAnnotation.text?.clear()
            }
        }

        // Quick-tag buttons
        binding.btnTagBrake.setOnClickListener    { viewModel.addAnnotation("brake on") }
        binding.btnTagThrottle25.setOnClickListener { viewModel.addAnnotation("throttle 25%") }
        binding.btnTagThrottle50.setOnClickListener { viewModel.addAnnotation("throttle 50%") }
        binding.btnTagThrottle100.setOnClickListener { viewModel.addAnnotation("throttle 100% WOT") }
        binding.btnTagRolling.setOnClickListener  { viewModel.addAnnotation("rolling") }
        binding.btnTagStopped.setOnClickListener  { viewModel.addAnnotation("stopped") }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ---- Adapter ----

    data class BlockItem(val baseAddr: Int, val packet: SniffPacket?)

    private class BlockAdapter(
        private val flashingAddresses: Set<Int>,
        private val onClick: (Int) -> Unit
    ) : ListAdapter<BlockItem, BlockAdapter.VH>(DIFF) {

        private val FLASH_COLOR = Color.parseColor("#4DFF6600")   // ~30 % orange
        private val NORMAL_COLOR = Color.TRANSPARENT

        inner class VH(private val b: ItemSnifferBlockBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(item: BlockItem) {
                val addr = item.baseAddr
                b.tvBlockAddr.text = "0x%02X".format(addr)
                b.cardBlock.setCardBackgroundColor(
                    if (flashingAddresses.contains(addr)) FLASH_COLOR else NORMAL_COLOR
                )

                val words = item.packet?.words
                if (words == null) {
                    b.tvWords03.text = "-- : ----  -- : ----  -- : ----"
                    b.tvWords36.text = "-- : ----  -- : ----  -- : ----"
                    b.tvWords03.setTextColor(Color.parseColor("#666666"))
                    b.tvWords36.setTextColor(Color.parseColor("#666666"))
                } else {
                    val sorted = words.entries.sortedBy { it.key }
                    fun fmt(e: Map.Entry<Int, Int>?) =
                        if (e != null) "%02X:%04X".format(e.key and 0xFF, e.value) else "--:----"
                    b.tvWords03.text = "${fmt(sorted.getOrNull(0))}  ${fmt(sorted.getOrNull(1))}  ${fmt(sorted.getOrNull(2))}"
                    b.tvWords36.text = "${fmt(sorted.getOrNull(3))}  ${fmt(sorted.getOrNull(4))}  ${fmt(sorted.getOrNull(5))}"
                    b.tvWords03.setTextColor(Color.WHITE)
                    b.tvWords36.setTextColor(Color.parseColor("#AAAAAA"))
                }

                b.root.setOnClickListener { onClick(addr) }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val b = ItemSnifferBlockBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return VH(b)
        }

        override fun onBindViewHolder(holder: VH, position: Int) =
            holder.bind(getItem(position))

        companion object {
            val DIFF = object : DiffUtil.ItemCallback<BlockItem>() {
                override fun areItemsTheSame(a: BlockItem, b: BlockItem) = a.baseAddr == b.baseAddr
                override fun areContentsTheSame(a: BlockItem, b: BlockItem) =
                    a.packet?.words == b.packet?.words
            }
        }
    }
}
