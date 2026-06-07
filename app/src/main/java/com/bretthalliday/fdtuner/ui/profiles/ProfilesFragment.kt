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

        // Show/hide empty state
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                paramsViewModel.rawParams.collect {
                    binding.btnSaveProfile.isEnabled = it.isNotEmpty()
                    binding.tvSaveHint.text = if (it.isEmpty())
                        "Connect to a controller and tap Read All to load params before saving."
                    else
                        "${it.size} parameters ready to save."
                }
            }
        }
    }

    private fun refreshList() {
        val profiles = ProfileManager.listProfiles(requireContext())
        adapter.submitList(profiles)
        binding.tvNoProfiles.visibility = if (profiles.isEmpty()) View.VISIBLE else View.GONE
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
                        val exists = ProfileManager.profileExists(requireContext(), name)
                        if (exists) {
                            // Confirm overwrite of saved profile
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
            Toast.makeText(requireContext(), "No params to save — read from controller first", Toast.LENGTH_SHORT).show()
            return
        }
        ProfileManager.save(requireContext(), name, params)
        Toast.makeText(requireContext(), "Profile \"$name\" saved (${params.size} params)", Toast.LENGTH_SHORT).show()
        refreshList()
    }

    private fun confirmLoad(profile: SavedProfile) {
        AlertDialog.Builder(requireContext())
            .setTitle("⚠️ Load Profile?")
            .setMessage(
                "Loading \"${profile.name}\" will immediately write ${profile.paramCount} parameters to your controller.\n\n" +
                "This will overwrite ALL current controller settings.\n\n" +
                "Make sure the bike is stationary and safe before continuing."
            )
            .setPositiveButton("Load & Write") { _, _ -> doLoad(profile) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun doLoad(profile: SavedProfile) {
        val params = ProfileManager.load(requireContext(), profile.name) ?: run {
            Toast.makeText(requireContext(), "Failed to load profile", Toast.LENGTH_SHORT).show()
            return
        }

        if (paramsViewModel.isDemo) {
            Toast.makeText(requireContext(), "Demo mode — connect to a real controller to load profiles", Toast.LENGTH_SHORT).show()
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
                ProfileManager.delete(requireContext(), profile.name)
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
            b.btnLoad.setOnClickListener { onLoad(profile) }
            b.btnDelete.setOnClickListener { onDelete(profile) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemProfileBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))
}
