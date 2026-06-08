package com.bretthalliday.fdtuner.ui.sniffer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.bretthalliday.fdtuner.analysis.CaptureAnalyzer
import com.bretthalliday.fdtuner.databinding.FragmentAnalysisBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import android.content.Intent

/**
 * Full-screen capture analysis view.
 *
 * Runs [CaptureAnalyzer] on the current [SnifferViewModel] session snapshot
 * (in a background coroutine so the UI thread is never blocked), then shows
 * the monospace report text in a scrollable view.
 *
 * Read-only: no BLE writes anywhere in this file.
 */
class AnalysisFragment : Fragment() {

    private var _binding: FragmentAnalysisBinding? = null
    private val binding get() = _binding!!

    private val snifferVm: SnifferViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAnalysisBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Run analysis on Dispatchers.Default so long sessions don't jank the main thread
        viewLifecycleOwner.lifecycleScope.launch {
            val result: CaptureAnalyzer.AnalysisResult = withContext(Dispatchers.Default) {
                snifferVm.runAnalysis()
            }
            showResult(result)
        }
    }

    private fun showResult(result: CaptureAnalyzer.AnalysisResult) {
        if (_binding == null) return  // fragment was detached before analysis finished

        // Hide loading, show report
        binding.llLoading.visibility    = View.GONE
        binding.scrollAnalysis.visibility = View.VISIBLE

        if (result.segments.all { it.tag == "(untagged)" }) {
            // All packets ended up in (untagged) — no annotation tags in the session
            binding.tvAnalysisReport.text =
                "No tags captured — run a tagged capture first.\n\n" +
                "Use the annotation bar at the bottom of the Sniffer screen:\n" +
                "  • Quick-tag buttons (Brake / Throttle % / Rolling / Stop)\n" +
                "  • Free-text field for custom labels\n\n" +
                "Once you have at least two labelled segments, run Analyze again."
        } else {
            binding.tvAnalysisReport.text = result.reportText
        }

        // Enable share once we have a report
        binding.btnShareAnalysis.isEnabled = true
        binding.btnShareAnalysis.setOnClickListener {
            shareReport(result.reportText)
        }
    }

    private fun shareReport(text: String) {
        val ctx      = requireContext()
        val fileName = "fd_analysis_${System.currentTimeMillis()}.txt"
        val file     = File(ctx.cacheDir, fileName)
        file.writeText(text)
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type    = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "FarDriver capture analysis")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share analysis"))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
