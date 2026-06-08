package com.bretthalliday.fdtuner.ui.params

import android.app.AlertDialog
import android.content.Context
import android.text.InputFilter
import android.text.InputType
import android.view.LayoutInflater
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.bretthalliday.fdtuner.databinding.DialogParamEditBinding
import com.bretthalliday.fdtuner.model.ParamDef
import com.bretthalliday.fdtuner.model.ParamDocs

/**
 * Dialog for editing a single parameter value.
 * Shows current value, min/max bounds, units, and notes.
 * Validates input before calling [onConfirm].
 */
object ParamEditDialog {

    fun show(
        fragment: Fragment,
        param: ParamDef,
        currentRaw: Int?,
        onConfirm: (Int) -> Unit
    ) {
        val context = fragment.requireContext()
        val binding = DialogParamEditBinding.inflate(LayoutInflater.from(context))

        // Safety warning for critical params
        if (param.isSafetyCritical) {
            binding.layoutSafetyWarning.visibility = android.view.View.VISIBLE
        }

        // Populate header
        binding.tvDialogParamName.text = param.name
        binding.tvDialogUnit.text = param.unit

        // Rich factory-app docs (keyed by official name); fall back to plain notes.
        val doc = ParamDocs.byName[param.name]
        binding.tvDialogNotes.text =
            doc?.help?.takeIf { it.isNotBlank() } ?: param.notes.ifEmpty { "—" }

        val rec = doc?.recommendation?.takeIf { it.isNotBlank() }
        binding.tvDialogRecommend.visibility =
            if (rec != null) android.view.View.VISIBLE else android.view.View.GONE
        if (rec != null) binding.tvDialogRecommend.text = "Recommended: $rec"

        val ex = doc?.examples?.takeIf { it.isNotBlank() }
        binding.tvDialogExamples.visibility =
            if (ex != null) android.view.View.VISIBLE else android.view.View.GONE
        if (ex != null) binding.tvDialogExamples.text = "Examples: $ex"

        val warn = doc?.warning?.takeIf { it.isNotBlank() }
        binding.layoutDocWarning.visibility =
            if (warn != null) android.view.View.VISIBLE else android.view.View.GONE
        if (warn != null) binding.tvDocWarning.text = warn

        // Show current value
        val currentDisplay = if (currentRaw != null) {
            val extracted = param.extractValue(currentRaw)
            if (param.scale != 1f) "%.2f".format(extracted / param.scale)
            else extracted.toString()
        } else {
            "—"
        }
        binding.tvCurrentValue.text = "Current: $currentDisplay ${param.unit}"

        // Suggested range — informational only, controller enforces its own limits
        val minDisplay = if (param.scale != 1f) "%.1f".format(param.minVal / param.scale) else param.minVal.toString()
        val maxDisplay = if (param.scale != 1f) "%.1f".format(param.maxVal / param.scale) else param.maxVal.toString()
        binding.tvRange.text = "Suggested: $minDisplay – $maxDisplay ${param.unit}"

        // Input field
        binding.etNewValue.inputType = if (param.minVal < 0) {
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
        } else {
            InputType.TYPE_CLASS_NUMBER
        }
        if (param.scale != 1f) {
            binding.etNewValue.inputType = binding.etNewValue.inputType or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }

        // Pre-fill with current display value if available
        if (currentRaw != null) {
            val extracted = param.extractValue(currentRaw)
            val preStr = if (param.scale != 1f) "%.2f".format(extracted / param.scale) else extracted.toString()
            binding.etNewValue.setText(preStr)
            binding.etNewValue.selectAll()
        }

        val dialog = AlertDialog.Builder(context)
            .setTitle("Edit ${param.name}")
            .setView(binding.root)
            .setPositiveButton("Write", null) // override below to prevent auto-dismiss on error
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val inputStr = binding.etNewValue.text.toString().trim()
                if (inputStr.isEmpty()) {
                    binding.etNewValue.error = "Enter a value"
                    return@setOnClickListener
                }

                val displayFloat = inputStr.toFloatOrNull()
                if (displayFloat == null) {
                    binding.etNewValue.error = "Invalid number"
                    return@setOnClickListener
                }

                // Convert display value → raw integer value (reverse of scale)
                val rawInt = (displayFloat * param.scale).toInt()

                // Hard block only on data type overflow (int16 range)
                // Controller enforces its own limits — we don't second-guess it
                val dataTypeMax = if (param.minVal < 0) 32767 else 65535
                val dataTypeMin = if (param.minVal < 0) -32768 else 0
                if (rawInt < dataTypeMin || rawInt > dataTypeMax) {
                    binding.etNewValue.error = "Value out of range (${dataTypeMin}–${dataTypeMax})"
                    return@setOnClickListener
                }

                // Soft warning if outside suggested range
                val outsideSuggested = rawInt < param.minVal || rawInt > param.maxVal

                val newDisplayStr = if (param.scale != 1f) "%.2f".format(rawInt / param.scale) else rawInt.toString()
                val warningLine = when {
                    param.isSafetyCritical && outsideSuggested ->
                        "⚠️ Safety-critical + outside suggested range ($minDisplay–$maxDisplay). Proceed?"
                    param.isSafetyCritical ->
                        "⚠️ Safety-critical parameter. Are you sure?"
                    outsideSuggested ->
                        "⚠️ Outside suggested range ($minDisplay–$maxDisplay). Controller will decide if it accepts this value."
                    else -> "Apply this value?"
                }

                androidx.appcompat.app.AlertDialog.Builder(context)
                    .setTitle("Confirm Write")
                    .setMessage(
                        "${param.name}\n\n" +
                        "Current: $currentDisplay ${param.unit}\n" +
                        "New:     $newDisplayStr ${param.unit}\n\n" +
                        warningLine
                    )
                    .setPositiveButton("Apply") { _, _ ->
                        onConfirm(rawInt)
                        Toast.makeText(context, "${param.name} updated", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }

        dialog.show()
    }
}

// Extension: create binding from context with a named layout
// (Using a simple container-based approach — the actual binding is used above)
