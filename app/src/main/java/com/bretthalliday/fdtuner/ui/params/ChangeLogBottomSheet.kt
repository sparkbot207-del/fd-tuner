package com.bretthalliday.fdtuner.ui.params

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bretthalliday.fdtuner.data.ChangeLogEntry
import com.bretthalliday.fdtuner.data.ChangeLogManager
import com.bretthalliday.fdtuner.databinding.BottomSheetChangelogBinding
import com.bretthalliday.fdtuner.databinding.ItemChangelogEntryBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Bottom sheet that shows the param change audit trail.
 * Entries are shown newest-first, grouped by date header.
 * Demo entries are prefixed with "[DEMO]" and shown in cyan.
 */
class ChangeLogBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetChangelogBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetChangelogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = ChangeLogAdapter()
        binding.recyclerChangelog.apply {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter = adapter
        }

        binding.btnClearLog.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Clear Change Log?")
                .setMessage("This will permanently delete all ${adapter.itemCount} log entries.")
                .setPositiveButton("Clear") { _, _ ->
                    ChangeLogManager.clear(requireContext())
                    adapter.submitList(emptyList())
                    binding.tvEmptyLog.visibility = View.VISIBLE
                    binding.recyclerChangelog.visibility = View.GONE
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        refreshList(adapter)
    }

    private fun refreshList(adapter: ChangeLogAdapter) {
        val entries = ChangeLogManager.getAll(requireContext())
        val items = buildListItems(entries)
        adapter.submitList(items)

        val isEmpty = entries.isEmpty()
        binding.tvEmptyLog.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.recyclerChangelog.visibility = if (isEmpty) View.GONE else View.VISIBLE
        binding.btnClearLog.isEnabled = !isEmpty
    }

    private fun buildListItems(entries: List<ChangeLogEntry>): List<ChangeLogListItem> {
        val result = mutableListOf<ChangeLogListItem>()
        var lastDate = ""
        for (entry in entries) {
            val date = entry.timestamp.take(10) // "yyyy-MM-dd"
            if (date != lastDate) {
                result.add(ChangeLogListItem.Header(date))
                lastDate = date
            }
            result.add(ChangeLogListItem.Entry(entry))
        }
        return result
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ---- List item types ----

    sealed class ChangeLogListItem {
        data class Header(val date: String) : ChangeLogListItem()
        data class Entry(val entry: ChangeLogEntry) : ChangeLogListItem()
    }

    // ---- Adapter ----

    private inner class ChangeLogAdapter :
        ListAdapter<ChangeLogListItem, RecyclerView.ViewHolder>(DIFF) {

        override fun getItemViewType(position: Int) = when (getItem(position)) {
            is ChangeLogListItem.Header -> VIEW_TYPE_HEADER
            is ChangeLogListItem.Entry -> VIEW_TYPE_ENTRY
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return when (viewType) {
                VIEW_TYPE_HEADER -> {
                    val tv = LayoutInflater.from(parent.context)
                        .inflate(android.R.layout.simple_list_item_1, parent, false)
                    HeaderVH(tv)
                }
                else -> {
                    val b = ItemChangelogEntryBinding.inflate(
                        LayoutInflater.from(parent.context), parent, false
                    )
                    EntryVH(b)
                }
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val item = getItem(position)) {
                is ChangeLogListItem.Header -> (holder as HeaderVH).bind(item.date)
                is ChangeLogListItem.Entry -> (holder as EntryVH).bind(item.entry)
            }
        }
    }

    private class HeaderVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(date: String) {
            (itemView as android.widget.TextView).apply {
                text = date
                setTextColor(context.getColor(com.bretthalliday.fdtuner.R.color.pink_primary))
                textSize = 12f
                setPadding(32, 20, 32, 4)
                setTypeface(null, android.graphics.Typeface.BOLD)
                setBackgroundColor(context.getColor(com.bretthalliday.fdtuner.R.color.bg_dark))
                letterSpacing = 0.08f
            }
        }
    }

    private inner class EntryVH(private val b: ItemChangelogEntryBinding) :
        RecyclerView.ViewHolder(b.root) {
        fun bind(entry: ChangeLogEntry) {
            val time = entry.timestamp.substring(11) // "HH:mm:ss"
            b.tvTimestamp.text = time

            val prefix = if (entry.isDemo) "[DEMO] " else ""
            b.tvParamChange.text = "$prefix${entry.paramName}: ${entry.oldValue} → ${entry.newValue}"

            val textColor = if (entry.isDemo) {
                requireContext().getColor(com.bretthalliday.fdtuner.R.color.cyan_accent)
            } else {
                requireContext().getColor(com.bretthalliday.fdtuner.R.color.text_primary)
            }
            b.tvParamChange.setTextColor(textColor)
        }
    }

    companion object {
        const val TAG = "ChangeLogBottomSheet"
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_ENTRY = 1

        val DIFF = object : DiffUtil.ItemCallback<ChangeLogListItem>() {
            override fun areItemsTheSame(a: ChangeLogListItem, b: ChangeLogListItem): Boolean {
                return when {
                    a is ChangeLogListItem.Header && b is ChangeLogListItem.Header -> a.date == b.date
                    a is ChangeLogListItem.Entry && b is ChangeLogListItem.Entry ->
                        a.entry.timestamp == b.entry.timestamp && a.entry.paramAddr == b.entry.paramAddr
                    else -> false
                }
            }
            override fun areContentsTheSame(a: ChangeLogListItem, b: ChangeLogListItem) = a == b
        }
    }
}
