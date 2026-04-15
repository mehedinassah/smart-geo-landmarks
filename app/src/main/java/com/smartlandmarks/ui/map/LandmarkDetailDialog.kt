package com.smartlandmarks.ui.map

import android.Manifest
import android.app.Dialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.bumptech.glide.Glide
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.smartlandmarks.R
import com.smartlandmarks.data.model.Landmark
import com.smartlandmarks.databinding.DialogLandmarkDetailBinding
import com.smartlandmarks.viewmodel.LandmarkViewModel

private const val TAG = "LandmarkDetailDialog"

class LandmarkDetailDialog : DialogFragment() {

    private var _binding: DialogLandmarkDetailBinding? = null
    private val binding get() = _binding!!
    private val viewModel: LandmarkViewModel by activityViewModels()
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var landmark: Landmark? = null

    companion object {
        private const val ARG_LANDMARK_ID = "landmark_id"
        private const val ARG_LANDMARK_TITLE = "landmark_title"
        private const val ARG_LANDMARK_SCORE = "landmark_score"
        private const val ARG_LANDMARK_IMAGE = "landmark_image"
        private const val ARG_LANDMARK_VISITS = "landmark_visits"

        fun newInstance(landmark: Landmark): LandmarkDetailDialog {
            return LandmarkDetailDialog().apply {
                arguments = Bundle().apply {
                    putInt(ARG_LANDMARK_ID, landmark.id)
                    putString(ARG_LANDMARK_TITLE, landmark.title)
                    putDouble(ARG_LANDMARK_SCORE, landmark.score)
                    putString(ARG_LANDMARK_IMAGE, landmark.image)
                    putInt(ARG_LANDMARK_VISITS, landmark.visitCount)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogLandmarkDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        val id = arguments?.getInt(ARG_LANDMARK_ID) ?: return
        val title = arguments?.getString(ARG_LANDMARK_TITLE) ?: ""
        val score = arguments?.getDouble(ARG_LANDMARK_SCORE) ?: 0.0
        val image = arguments?.getString(ARG_LANDMARK_IMAGE)
        val visits = arguments?.getInt(ARG_LANDMARK_VISITS) ?: 0

        Log.d(TAG, "onViewCreated: Loading landmark id=$id, title=$title")

        binding.tvTitle.text = title
        binding.tvScore.text = "Score: ${"%.1f".format(score)}"
        binding.tvVisits.text = "Visits: $visits"

        // Load image
        loadLandmarkImage(image)

        binding.btnVisit.setOnClickListener {
            visitLandmark(id, title)
        }

        binding.btnDelete.setOnClickListener {
            showDeleteConfirm(id, title)
        }

        binding.btnClose.setOnClickListener { dismiss() }

        // Listen for landmarks list updates to refresh score/visit count in dialog
        var showSuccessAutoDismiss = false
        viewModel.landmarks.observe(viewLifecycleOwner) { landmarks ->
            Log.d(TAG, "onViewCreated: Landmarks updated, checking for landmark id=$id")
            val updatedLandmark = landmarks.find { it.id == id }
            if (updatedLandmark != null) {
                Log.d(TAG, "onViewCreated: Found updated landmark - Score: ${updatedLandmark.score}, Visits: ${updatedLandmark.visitCount}")
                binding.tvScore.text = "Score: ${"%.1f".format(updatedLandmark.score)}"
                binding.tvVisits.text = "Visits: ${updatedLandmark.visitCount}"
                
                // If this was triggered by a visit, auto-dismiss after showing updated values
                if (showSuccessAutoDismiss) {
                    Log.d(TAG, "onViewCreated: Auto-dismissing dialog after update")
                    binding.root.postDelayed({ dismiss() }, 1500) // Give user time to see updated values
                    showSuccessAutoDismiss = false
                }
            }
        }

        viewModel.successMessage.observe(viewLifecycleOwner) { msg ->
            msg?.let {
                Log.d(TAG, "onViewCreated: Success message: $it")
                Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
                viewModel.clearSuccess()
                // Mark that we should auto-dismiss when landmarks update
                showSuccessAutoDismiss = true
            }
        }

        viewModel.error.observe(viewLifecycleOwner) { err ->
            err?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
                viewModel.clearError()
            }
        }
    }

    private fun loadLandmarkImage(image: String?) {
        if (image.isNullOrEmpty()) {
            Log.d(TAG, "loadLandmarkImage: No image URL provided")
            binding.ivLandmark.setImageResource(R.drawable.ic_landmark_placeholder)
            return
        }

        // Construct full image URL
        val imageUrl = if (image.startsWith("http")) {
            image // Already a full URL
        } else {
            "https://labs.anontech.info/cse489/exm3/$image" // Construct from relative path
        }

        Log.d(TAG, "loadLandmarkImage: Loading from URL: $imageUrl")

        Glide.with(this)
            .load(imageUrl)
            .placeholder(R.drawable.ic_landmark_placeholder)
            .error(R.drawable.ic_landmark_placeholder)
            .into(binding.ivLandmark)
    }

    private fun visitLandmark(id: Int, title: String) {
        if (ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(requireContext(), "Location permission required", Toast.LENGTH_SHORT).show()
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                viewModel.visitLandmark(id, title, location.latitude, location.longitude)
            } else {
                Toast.makeText(requireContext(), "Cannot get location. Try again.", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener {
            Toast.makeText(requireContext(), "Location error: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showDeleteConfirm(id: Int, title: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Landmark")
            .setMessage("Are you sure you want to delete \"$title\"?")
            .setPositiveButton("Delete") { _, _ -> viewModel.deleteLandmark(id) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
