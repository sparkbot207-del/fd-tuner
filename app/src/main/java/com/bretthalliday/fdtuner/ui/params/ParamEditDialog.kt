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
        binding.tvDialogNotes.text = param.notes.ifEmpty { "—" }

        // Show current value
        val currentDisplay = if (currentRaw != null) {
            val extracted = param.extractValue(currentRaw)
            if (param.scale != 1f) "%.2f".format(extracted / param.scale)
            else extracted.toString()
        } else {
            "—"
        }
        binding.tvCurrentValue.text = "Current: $currentDisplay ${param.unit}"

        // Range hint
        val minDisplay = if (param.scale != 1f) "%.1f".format(param.minVal / param.scale) else param.minVal.toString()
        val maxDisplay = if (param.scale != 1f) "%.1f".format(param.maxVal / param.scale) else param.maxVal.toString()
        binding.tvRange.text = "Range: $minDisplay – $maxDisplay ${param.unit}"

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

                // Validate against min/max (compare at raw level)
                if (rawInt < param.minVal || rawInt > param.maxVal) {
                    binding.etNewValue.error = "Must be between $minDisplay and $maxDisplay"
                    return@setOnClickListener
                }

                // Show explicit confirm: current → new before writing
                val newDisplayStr = if (param.scale != 1f) "%.2f".format(rawInt / param.scale) else rawInt.toString()
                androidx.appcompat.app.AlertDialog.Builder(context)
                    .setTitle("Confirm Write")
                    .setMessage(
                        "${param.name}\n\n" +
                        "Current: $currentDisplay ${param.unit}\n" +
                        "New:     $newDisplayStr ${param.unit}\n\n" +
                        if (param.isSafetyCritical) "⚠️ Safety-critical — are you sure?" else "Write this value to the controller?"
                    )
                    .setPositiveButton("Write") { _, _ ->
                        onConfirm(rawInt)
                        Toast.makeText(context, "Writing ${param.name}…", Toast.LENGTH_SHORT).show()
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
