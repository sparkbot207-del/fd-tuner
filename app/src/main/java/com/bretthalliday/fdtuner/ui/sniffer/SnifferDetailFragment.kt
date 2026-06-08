package com.bretthalliday.fdtuner.ui.sniffer

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bretthalliday.fdtuner.databinding.FragmentSnifferDetailBinding
import kotlinx.coroutines.launch

/**
 * Shows the 6 words of a single block live, with session min/max per word.
 * Argument: "baseAddr" (Int) — the block base address to display.
 */
class SnifferDetailFragment : Fragment() {

    private var _binding: FragmentSnifferDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SnifferViewModel by activityViewModels()
    private var baseAddr: Int = 0

    // 6 rows of TextViews (addr, hex, decimal, min, max), built dynamically
    private data class WordRow(
        val tvAddr: TextView,
        val tvHex: TextView,
        val tvDec: TextView,
        val tvMin: TextView,
        val tvMax: TextView
    )
    private val wordRows = mutableListOf<WordRow>()
    private val prevValues = mutableMapOf<Int, Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        baseAddr = arguments?.getInt("baseAddr", 0) ?: 0
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSnifferDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvDetailTitle.text = "Block 0x%02X".format(baseAddr)
        binding.tvDetailSubtitle.text = "words 0x%02X–0x%02X".format(baseAddr, baseAddr + 5)

        // Build 6 word rows programmatically inside llWordRows
        repeat(6) { offset ->
            val wordAddr = baseAddr + offset
            val (rowView, row) = buildWordRow(wordAddr)
            wordRows.add(row)
            binding.llWordRows.addView(rowView)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.latestByAddress.collect { latest ->
                    val packet = latest[baseAddr] ?: return@collect
                    binding.tvDetailRawHex.text = packet.rawHex

                    wordRows.forEachIndexed { offset, row ->
                        val wordAddr = baseAddr + offset
                        val value = packet.words[wordAddr]
                        val prev = prevValues[wordAddr]

                        if (value != null) {
                            row.tvHex.text = "0x%04X".format(value)
                            row.tvDec.text = value.toString()

                            val (mn, mx) = viewModel.getMinMax(wordAddr) ?: Pair(value, value)
                            row.tvMin.text = mn.toString()
                            row.tvMax.text = mx.toString()

                            // Flash hex value briefly when it changes
                            if (prev != null && prev != value) {
                                row.tvHex.setTextColor(Color.parseColor("#FFFF6600"))
                                row.tvHex.postDelayed({
                                    if (isAdded) row.tvHex.setTextColor(Color.WHITE)
                                }, 500)
                            } else {
                                row.tvHex.setTextColor(Color.WHITE)
                            }
                        } else {
                            row.tvHex.text = "——"
                            row.tvDec.text = "—"
                            row.tvMin.text = "—"
                            row.tvMax.text = "—"
                        }

                        if (value != null) prevValues[wordAddr] = value
                    }
                }
            }
        }
    }

    /** Build a horizontal row for one word address. Returns the row View + the [WordRow] handles. */
    private fun buildWordRow(wordAddr: Int): Pair<View, WordRow> {
        val ctx = requireContext()
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(48, 20, 48, 20)
        }

        fun tv(weight: Float, color: Int, bold: Boolean = false) =
            TextView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight)
                textSize = 13f
                setTextColor(color)
                typeface = if (bold) Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                            else Typeface.MONOSPACE
                text = "—"
            }

        val secondary = Color.parseColor("#888888")
        val tvAddr = tv(1.2f, Color.parseColor("#00BCD4"), bold = true)
            .also { it.text = "0x%02X".format(wordAddr) }
        val tvHex  = tv(1.5f, Color.WHITE)
        val tvDec  = tv(1.5f, secondary)
        val tvMin  = tv(1.0f, secondary)
        val tvMax  = tv(1.0f, secondary)

        row.addView(tvAddr)
        row.addView(tvHex)
        row.addView(tvDec)
        row.addView(tvMin)
        row.addView(tvMax)

        return Pair(row, WordRow(tvAddr, tvHex, tvDec, tvMin, tvMax))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
