package com.smartlandmarks.ui.landmarks

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.smartlandmarks.R
import com.smartlandmarks.data.model.Landmark
import com.smartlandmarks.databinding.FragmentLandmarksBinding
import com.smartlandmarks.viewmodel.LandmarkViewModel

class LandmarksFragment : Fragment() {

    private var _binding: FragmentLandmarksBinding? = null
    private val binding get() = _binding!!
    private val viewModel: LandmarkViewModel by activityViewModels()
    private lateinit var adapter: LandmarkAdapter
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLandmarksBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        setupRecyclerView()
        setupFilterSort()
        observeViewModel()

        binding.swipeRefresh.setOnRefreshListener {
            viewModel.fetchLandmarks()
        }
    }

    private fun setupRecyclerView() {
        adapter = LandmarkAdapter(
            onVisitClick = { landmark -> visitLandmark(landmark) },
            onDeleteClick = { landmark -> showDeleteConfirm(landmark) }
        )
        binding.rvLandmarks.adapter = adapter
    }

    private fun setupFilterSort() {
        binding.sliderMinScore.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                viewModel.setMinScore(value.toDouble())
                binding.tvMinScore.text = "Min Score: ${value.toInt()}"
            }
        }

        binding.btnSortAsc.setOnClickListener {
            viewModel.setSortDescending(false)
            binding.btnSortAsc.isSelected = true
            binding.btnSortDesc.isSelected = false
        }

        binding.btnSortDesc.setOnClickListener {
            viewModel.setSortDescending(true)
            binding.btnSortAsc.isSelected = false
            binding.btnSortDesc.isSelected = true
        }
        binding.btnSortDesc.isSelected = true
    }

    private fun observeViewModel() {
        viewModel.landmarks.observe(viewLifecycleOwner) { landmarks ->
            adapter.submitList(landmarks)
            binding.tvEmpty.visibility = if (landmarks.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.swipeRefresh.isRefreshing = loading
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let {
                showErrorDialog(it)
                viewModel.clearError()
            }
        }

        viewModel.successMessage.observe(viewLifecycleOwner) { msg ->
            msg?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
                viewModel.clearSuccess()
            }
        }
    }

    private fun visitLandmark(landmark: Landmark) {
        if (ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1001)
            return
        }
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                viewModel.visitLandmark(landmark.id, landmark.title, location.latitude, location.longitude)
            } else {
                Toast.makeText(requireContext(), "Unable to get location. Try again.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showDeleteConfirm(landmark: Landmark) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Landmark")
            .setMessage("Delete \"${landmark.title}\"?")
            .setPositiveButton("Delete") { _, _ -> viewModel.deleteLandmark(landmark.id) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showErrorDialog(message: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Error")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
