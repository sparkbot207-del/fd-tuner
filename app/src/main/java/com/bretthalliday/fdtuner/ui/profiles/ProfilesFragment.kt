package com.bretthalliday.fdtuner.ui.profiles

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bretthalliday.fdtuner.data.ProfileManager
import com.bretthalliday.fdtuner.data.SavedProfile
import com.bretthalliday.fdtuner.databinding.FragmentProfilesBinding
import com.bretthalliday.fdtuner.databinding.ItemProfileBinding
import com.bretthalliday.fdtuner.ui.params.ParamsViewModel
import kotlinx.coroutines.launch

class ProfilesFragment : Fragment() {

    private var _binding: FragmentProfilesBinding? = null
    private val binding get() = _binding!!
    private val paramsViewModel: ParamsViewModel by activityViewModels()
    private lateinit var adapter: ProfileAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfilesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = ProfileAdapter(
            onLoad = { profile -> confirmLoad(profile) },
            onDelete = { profile -> confirmDelete(profile) }
        )
        binding.recyclerProfiles.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerProfiles.adapter = adapter

        binding.btnSaveProfile.setOnClickListener { showSaveDialog() }

        refreshList()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                paramsViewModel.rawParams.collect { params ->
                    val inDemo = paramsViewModel.isDemo
                    binding.btnSaveProfile.isEnabled = params.isNotEmpty()
                    binding.tvSaveHint.text = when {
                        params.isEmpty() && inDemo ->
                            "In demo mode — edit some params then save as a demo profile."
                        params.isEmpty() ->
                            "Connect to a controller and read params before saving."
                        inDemo ->
                            "${params.size} demo params ready to save."
                        else ->
                            "${params.size} controller params ready to save."
                    }
                    // Update section header label
                    binding.tvProfilesHeader.text =
                        if (inDemo) "⚡ DEMO PROFILES" else "CONTROLLER PROFILES"
                }
            }
        }
    }

    private fun refreshList() {
        val inDemo = paramsViewModel.isDemo
        val profiles = ProfileManager.listProfiles(requireContext(), isDemo = inDemo)
        adapter.submitList(profiles)
        binding.tvNoProfiles.visibility = if (profiles.isEmpty()) View.VISIBLE else View.GONE
        binding.tvNoProfiles.text = if (inDemo)
            "No demo profiles saved yet.\nEdit params in demo mode then tap Save."
        else
            "No controller profiles saved yet.\nConnect, read params, then tap Save."
    }

    private fun showSaveDialog() {
        val input = EditText(requireContext()).apply {
            hint = "Profile name (e.g. Street Tune)"
            setSingleLine()
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Save Profile")
            .setMessage("Name this tuning profile:")
            .setView(input)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()
            .also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val name = input.text.toString().trim()
                        if (name.isEmpty()) {
                            input.error = "Enter a name"
                            return@setOnClickListener
                        }
                        if (name.contains("|")) {
                            input.error = "Name cannot contain |"
                            return@setOnClickListener
                        }
                        val inDemo = paramsViewModel.isDemo
                        val exists = ProfileManager.profileExists(requireContext(), name, isDemo = inDemo)
                        if (exists) {
                            AlertDialog.Builder(requireContext())
                                .setTitle("Overwrite Profile?")
                                .setMessage("\"$name\" already exists. Replace it?")
                                .setPositiveButton("Overwrite") { _, _ ->
                                    doSave(name)
                                    dialog.dismiss()
                                }
                                .setNegativeButton("Cancel", null)
                                .show()
                        } else {
                            doSave(name)
                            dialog.dismiss()
                        }
                    }
                }
                dialog.show()
            }
    }

    private fun doSave(name: String) {
        val params = paramsViewModel.rawParams.value
        if (params.isEmpty()) {
            Toast.makeText(requireContext(), "No params to save", Toast.LENGTH_SHORT).show()
            return
        }
        val inDemo = paramsViewModel.isDemo
        ProfileManager.save(requireContext(), name, params, isDemo = inDemo)
        val label = if (inDemo) "demo profile" else "profile"
        Toast.makeText(requireContext(), "Saved $label \"$name\" (${params.size} params)", Toast.LENGTH_SHORT).show()
        refreshList()
    }

    private fun confirmLoad(profile: SavedProfile) {
        val inDemo = paramsViewModel.isDemo
        val (title, message, btnLabel) = if (inDemo) {
            Triple(
                "Load Demo Profile?",
                "Loading \"${profile.name}\" will apply ${profile.paramCount} parameters to the demo session.\n\nNo data will be written to any controller.",
                "Load"
            )
        } else {
            Triple(
                "⚠️ Load Profile?",
                "Loading \"${profile.name}\" will immediately write ${profile.paramCount} parameters to your controller.\n\n" +
                "This will overwrite ALL current controller settings.\n\n" +
                "Make sure the bike is stationary and safe before continuing.",
                "Load & Write"
            )
        }
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(btnLabel) { _, _ -> doLoad(profile) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun doLoad(profile: SavedProfile) {
        val inDemo = paramsViewModel.isDemo
        val params = ProfileManager.load(requireContext(), profile.name, isDemo = inDemo) ?: run {
            Toast.makeText(requireContext(), "Failed to load profile", Toast.LENGTH_SHORT).show()
            return
        }

        // Demo mode: bulk-apply all params in one shot to avoid race with collect coroutine
        if (inDemo) {
            (requireActivity() as? com.bretthalliday.fdtuner.MainActivity)
                ?.bleManager?.bulkUpdateDemoParams(params)
            Toast.makeText(requireContext(), "⚡ Demo profile \"${profile.name}\" loaded", Toast.LENGTH_SHORT).show()
            return
        }

        binding.progressLoad.visibility = View.VISIBLE
        binding.progressLoad.progress = 0
        binding.btnSaveProfile.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch {
            val success = paramsViewModel.loadProfile(params) { done, total ->
                requireActivity().runOnUiThread {
                    binding.progressLoad.max = total
                    binding.progressLoad.progress = done
                }
            }
            requireActivity().runOnUiThread {
                binding.progressLoad.visibility = View.GONE
                binding.btnSaveProfile.isEnabled = true
                if (success) {
                    Toast.makeText(requireContext(), "✓ Profile \"${profile.name}\" loaded successfully", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(requireContext(), "Write failed — check BLE connection", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun confirmDelete(profile: SavedProfile) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Profile?")
            .setMessage("Delete \"${profile.name}\"? This cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                ProfileManager.delete(requireContext(), profile.name, isDemo = profile.isDemo)
                Toast.makeText(requireContext(), "Deleted \"${profile.name}\"", Toast.LENGTH_SHORT).show()
                refreshList()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

private val PROFILE_DIFF = object : DiffUtil.ItemCallback<SavedProfile>() {
    override fun areItemsTheSame(a: SavedProfile, b: SavedProfile) = a.name == b.name
    override fun areContentsTheSame(a: SavedProfile, b: SavedProfile) = a == b
}

class ProfileAdapter(
    private val onLoad: (SavedProfile) -> Unit,
    private val onDelete: (SavedProfile) -> Unit
) : ListAdapter<SavedProfile, ProfileAdapter.VH>(PROFILE_DIFF) {

    inner class VH(private val b: ItemProfileBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(profile: SavedProfile) {
            b.tvProfileName.text = profile.name
            b.tvProfileMeta.text = "${profile.paramCount} params  ·  ${profile.savedAt}"
            b.tvProfileIcon.text = if (profile.isDemo) "⚡" else "💾"
            b.btnLoad.setOnClickListener { onLoad(profile) }
            b.btnDelete.setOnClickListener { onDelete(profile) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemProfileBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))
}
