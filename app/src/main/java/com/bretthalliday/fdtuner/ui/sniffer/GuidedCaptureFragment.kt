package com.bretthalliday.fdtuner.ui.sniffer

import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.bretthalliday.fdtuner.R
import com.bretthalliday.fdtuner.databinding.FragmentGuidedCaptureBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlin.math.ceil

/**
 * Guided Capture wizard — walks the operator through a fixed capture sequence in the Packet
 * Sniffer, auto-inserting the annotation tag at each step so they never fumble the free-text
 * field mid-throttle.
 *
 * STRICTLY READ-ONLY: it only inserts tags via the EXISTING annotation path
 * ([SnifferViewModel.addAnnotation]). It issues no BLE writes and does not pause, clear, or
 * duplicate the capture. It shares the same SnifferViewModel (and therefore the same live
 * session + annotation list) as the SnifferFragment via activityViewModels().
 */
class GuidedCaptureFragment : Fragment() {

    private var _binding: FragmentGuidedCaptureBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SnifferViewModel by activityViewModels()

    private var timer: CountDownTimer? = null
    private var stepIndex = 0
    private var finished = false

    /** Fixed capture sequence: (tag, instruction, holdSeconds). */
    private data class Step(val tag: String, val instruction: String, val holdSeconds: Int)

    private val steps = listOf(
        Step("stopped",       "Key on. No throttle/brake, wheel still.",                 5),
        Step("brake",         "Apply brake and hold.",                                   5),
        Step("stopped",       "Release brake.",                                          5),
        Step("park",          "Engage Park / parking mode.",                             5),
        Step("stopped",       "Disengage Park.",                                         5),
        Step("throttle low",  "Light throttle — wheel spinning slowly on the stand.",    5),
        Step("throttle mid",  "Moderate throttle — clearly faster than low.",            5),
        Step("throttle high", "Hard throttle — clearly faster than mid.",                5),
        Step("WOT",           "Wide-open throttle — briefly!",                           3),
        Step("throttle 0",    "Release throttle, let the wheel coast (still spinning).", 5),
        Step("rolling",       "Let the wheel coast down freely, no throttle.",           6),
        Step("gear 1",        "Set gear/mode 1 (ECO).",                                  5),
        Step("gear 2",        "Set gear/mode 2.",                                        5),
        Step("gear 3",        "Set gear/mode 3 (Sport).",                                5),
        Step("reverse",       "If equipped: engage reverse.",                            5),
        Step("warm",          "After a few min under load, throttle released.",          5)
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGuidedCaptureBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnGcNext.setOnClickListener { advance(record = true) }
        binding.btnGcSkip.setOnClickListener { advance(record = false) }
        binding.btnGcStop.setOnClickListener { finish() }

        // Enter the first step (records its tag).
        enterStep(0, record = true)
    }

    /** Move to the next step. [record] = whether the step we land on inserts its tag. */
    private fun advance(record: Boolean) {
        enterStep(stepIndex + 1, record)
    }

    private fun enterStep(index: Int, record: Boolean) {
        timer?.cancel()
        timer = null

        if (index >= steps.size) {
            finish()
            return
        }
        stepIndex = index
        val step = steps[index]

        // Insert this step's tag via the SAME annotation path the manual quick-tags use.
        if (record) viewModel.addAnnotation(step.tag)

        binding.tvStepCounter.text = "Step ${index + 1} of ${steps.size}"
        binding.tvTag.text = step.tag
        binding.tvInstruction.text = step.instruction
        binding.tvCountdown.text = step.holdSeconds.toString()

        timer = object : CountDownTimer(step.holdSeconds * 1000L, 250L) {
            override fun onTick(msLeft: Long) {
                _binding?.tvCountdown?.text = ceil(msLeft / 1000.0).toInt().coerceAtLeast(0).toString()
            }
            override fun onFinish() {
                _binding?.tvCountdown?.text = "0"
                // Auto-advance, recording the next step normally.
                advance(record = true)
            }
        }.start()
    }

    private fun finish() {
        if (finished) return
        finished = true
        timer?.cancel()
        timer = null

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Capture complete")
            .setMessage("Done — open Analyze to decode the capture?")
            .setPositiveButton("Analyze") { _, _ ->
                findNavController().navigate(
                    R.id.action_guidedCaptureFragment_to_analysisFragment
                )
            }
            .setNegativeButton("Back to Sniffer") { _, _ ->
                findNavController().popBackStack()
            }
            .setCancelable(false)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        timer?.cancel()
        timer = null
        _binding = null
    }
}
