package com.smartlandmarks.ui.add

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.*
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.smartlandmarks.R
import com.smartlandmarks.databinding.FragmentAddLandmarkBinding
import com.smartlandmarks.viewmodel.LandmarkViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class AddLandmarkFragment : Fragment() {

    private var _binding: FragmentAddLandmarkBinding? = null
    private val binding get() = _binding!!
    private val viewModel: LandmarkViewModel by activityViewModels()
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var selectedImageFile: File? = null
    private var cameraImageUri: Uri? = null
    private var cameraImageFile: File? = null

    // Gallery picker
    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                val file = uriToFile(uri)
                if (file != null) {
                    selectedImageFile = file
                    binding.ivPreview.setImageURI(uri)
                    binding.tvImageName.text = file.name
                } else {
                    Toast.makeText(requireContext(), "Failed to load image", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Camera launcher
    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            cameraImageFile?.let { file ->
                selectedImageFile = file
                binding.ivPreview.setImageURI(cameraImageUri)
                binding.tvImageName.text = file.name
            }
        }
    }

    // Location permission
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            fetchLocation()
        } else {
            Toast.makeText(requireContext(), "Location permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddLandmarkBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        binding.btnGetLocation.setOnClickListener { checkAndFetchLocation() }
        binding.btnPickImage.setOnClickListener { showImageSourceDialog() }
        binding.btnSubmit.setOnClickListener { submitLandmark() }

        observeViewModel()
    }

    private fun checkAndFetchLocation() {
        val fine = Manifest.permission.ACCESS_FINE_LOCATION
        val coarse = Manifest.permission.ACCESS_COARSE_LOCATION
        if (ContextCompat.checkSelfPermission(requireContext(), fine) == PackageManager.PERMISSION_GRANTED) {
            fetchLocation()
        } else {
            locationPermissionLauncher.launch(arrayOf(fine, coarse))
        }
    }

    private fun fetchLocation() {
        if (ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        // Hardcoded Dhaka/Shantinagar location
        binding.etLat.setText("23.8103")
        binding.etLon.setText("90.3563")
        Toast.makeText(requireContext(), "Location: Dhaka, Shantinagar", Toast.LENGTH_SHORT).show()
    }

    private fun showImageSourceDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Select Image Source")
            .setItems(arrayOf("Camera", "Gallery")) { _, which ->
                when (which) {
                    0 -> launchCamera()
                    1 -> launchGallery()
                }
            }
            .show()
    }

    private fun launchCamera() {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        cameraImageFile = File(
            requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES),
            "LANDMARK_${timeStamp}.jpg"
        )
        cameraImageUri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.fileprovider",
            cameraImageFile!!
        )
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri)
        }
        cameraLauncher.launch(intent)
    }

    private fun launchGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        galleryLauncher.launch(intent)
    }

    private fun uriToFile(uri: Uri): File? {
        return try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val destFile = File(requireContext().cacheDir, "gallery_${timeStamp}.jpg")
            requireContext().contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            }
            destFile
        } catch (e: Exception) {
            null
        }
    }

    private fun submitLandmark() {
        val title = binding.etTitle.text.toString().trim()
        val latStr = binding.etLat.text.toString().trim()
        val lonStr = binding.etLon.text.toString().trim()

        if (title.isEmpty()) { binding.etTitle.error = "Title required"; return }
        if (latStr.isEmpty()) { binding.etLat.error = "Latitude required"; return }
        if (lonStr.isEmpty()) { binding.etLon.error = "Longitude required"; return }
        if (selectedImageFile == null) {
            Toast.makeText(requireContext(), "Please select an image", Toast.LENGTH_SHORT).show()
            return
        }

        val lat = latStr.toDoubleOrNull()
        val lon = lonStr.toDoubleOrNull()
        if (lat == null || lon == null) {
            Toast.makeText(requireContext(), "Invalid coordinates", Toast.LENGTH_SHORT).show()
            return
        }

        viewModel.createLandmark(title, lat, lon, selectedImageFile!!)
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.btnSubmit.isEnabled = !loading
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.successMessage.observe(viewLifecycleOwner) { msg ->
            msg?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
                viewModel.clearSuccess()
                clearForm()
            }
        }

        viewModel.error.observe(viewLifecycleOwner) { err ->
            err?.let {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Error")
                    .setMessage(it)
                    .setPositiveButton("OK", null)
                    .show()
                viewModel.clearError()
            }
        }
    }

    private fun clearForm() {
        binding.etTitle.text?.clear()
        binding.etLat.text?.clear()
        binding.etLon.text?.clear()
        binding.ivPreview.setImageResource(R.drawable.ic_landmark_placeholder)
        binding.tvImageName.text = "No image selected"
        selectedImageFile = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
