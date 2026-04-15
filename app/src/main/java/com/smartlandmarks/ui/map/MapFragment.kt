package com.smartlandmarks.ui.map

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.ItemizedIconOverlay
import org.osmdroid.views.overlay.OverlayItem
import com.smartlandmarks.R
import com.smartlandmarks.data.model.Landmark
import com.smartlandmarks.databinding.FragmentMapBinding
import com.smartlandmarks.utils.Constants
import com.smartlandmarks.viewmodel.LandmarkViewModel

private const val TAG = "MapFragment"

class MapFragment : Fragment() {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!
    private val viewModel: LandmarkViewModel by activityViewModels()

    private var mapView: MapView? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set Osmdroid user agent
        Configuration.getInstance().userAgentValue = "SmartLandmarks/1.0"

        // Initialize map
        mapView = binding.root.findViewById(R.id.map)
        mapView?.apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)

            // Center on Bangladesh
            val bangladesh = GeoPoint(Constants.BD_LAT, Constants.BD_LON)
            controller.setCenter(bangladesh)
            controller.setZoom(Constants.DEFAULT_ZOOM.toDouble())
        }

        // Observe landmarks and update markers
        viewModel.landmarks.observe(viewLifecycleOwner) { landmarks ->
            Log.d(TAG, "onViewCreated: Landmarks updated, count = ${landmarks.size}")
            updateMarkers(landmarks)
        }
    }

    private fun updateMarkers(landmarks: List<Landmark>) {
        Log.d(TAG, "updateMarkers: Starting with ${landmarks.size} landmarks")
        mapView?.let { map ->
            // Remove all overlays
            map.overlays.clear()

            // Create items for markers
            val items = mutableListOf<OverlayItem>()

            for (landmark in landmarks) {
                val point = GeoPoint(landmark.lat, landmark.lon)
                val color = getMarkerColor(landmark.score)
                val title = "${landmark.title} (Score: ${"%.1f".format(landmark.score)})"

                val item = OverlayItem(title, landmark.title, point)
                item.markerHotspot = OverlayItem.HotspotPlace.CENTER
                items.add(item)
                Log.d(TAG, "updateMarkers: Added marker at $title")
            }

            // Create overlay with items
            val overlay = ItemizedIconOverlay(items, object : ItemizedIconOverlay.OnItemGestureListener<OverlayItem> {
                override fun onItemSingleTapUp(index: Int, item: OverlayItem?): Boolean {
                    Log.d(TAG, "onItemSingleTapUp: Tapped marker at index=$index, item=$item")
                    if (item != null) {
                        val landmark = landmarks.getOrNull(index)
                        Log.d(TAG, "onItemSingleTapUp: Landmark = $landmark")
                        landmark?.let { showLandmarkInfo(it) }
                    }
                    return true
                }

                override fun onItemLongPress(index: Int, item: OverlayItem?): Boolean {
                    return false
                }
            }, requireContext())

            map.overlays.add(overlay)
            map.invalidate()
            Log.d(TAG, "updateMarkers: Finished, overlays count = ${map.overlays.size}")
        }
    }

    private fun getMarkerColor(score: Double): String {
        return when {
            score < Constants.SCORE_LOW_THRESHOLD -> "#FF0000" // Red
            score < Constants.SCORE_MED_THRESHOLD -> "#FFA500" // Orange
            else -> "#00AA00" // Green
        }
    }

    private fun showLandmarkInfo(landmark: Landmark) {
        Log.d(TAG, "showLandmarkInfo: Showing dialog for landmark=${landmark.title}, image=${landmark.image}")
        val dialog = LandmarkDetailDialog.newInstance(landmark)
        dialog.show(childFragmentManager, "landmark_detail")
    }

    override fun onResume() {
        super.onResume()
        mapView?.onResume()
    }

    override fun onPause() {
        mapView?.onPause()
        super.onPause()
    }

    override fun onDestroyView() {
        mapView?.onDetach()
        super.onDestroyView()
        _binding = null
    }
}


