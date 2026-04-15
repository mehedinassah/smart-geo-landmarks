package com.smartlandmarks.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.*
import com.smartlandmarks.data.local.AppDatabase
import com.smartlandmarks.data.model.Landmark
import com.smartlandmarks.data.model.VisitResponse
import com.smartlandmarks.data.repository.LandmarkRepository
import com.smartlandmarks.data.repository.Result
import com.smartlandmarks.data.api.RetrofitClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

private const val TAG = "LandmarkViewModel"

class LandmarkViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val repository = LandmarkRepository(
        apiService = RetrofitClient.instance,
        landmarkDao = db.landmarkDao(),
        visitHistoryDao = db.visitHistoryDao(),
        pendingVisitDao = db.pendingVisitDao(),
        context = application
    )

    // LiveData from Room (always fresh)
    val landmarksFromDb = repository.landmarksLive
    val visitHistory = repository.visitHistoryLive

    // UI state
    private val _landmarks = MutableLiveData<List<Landmark>>()
    val landmarks: LiveData<List<Landmark>> = _landmarks

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _successMessage = MutableLiveData<String?>()
    val successMessage: LiveData<String?> = _successMessage

    private val _visitResult = MutableLiveData<VisitResponse?>()
    val visitResult: LiveData<VisitResponse?> = _visitResult

    // Filter & Sort state
    private val _minScore = MutableLiveData(0.0)
    val minScore: LiveData<Double> = _minScore

    private val _sortDescending = MutableLiveData(true)
    val sortDescending: LiveData<Boolean> = _sortDescending

    private var allLandmarks: List<Landmark> = emptyList()

    init {
        Log.d(TAG, "init: Initializing ViewModel")
        // Start fetch in background - completely non-blocking
        viewModelScope.launch {
            fetchLandmarksInternal()
        }
    }

    fun fetchLandmarks() {
        Log.d(TAG, "fetchLandmarks: Manual fetch requested")
        viewModelScope.launch {
            fetchLandmarksInternal()
        }
    }

    private suspend fun fetchLandmarksInternal() {
        Log.d(TAG, "fetchLandmarksInternal: Starting fetch")
        _isLoading.value = true
        when (val result = repository.fetchLandmarks()) {
            is Result.Success -> {
                allLandmarks = result.data
                Log.d(TAG, "fetchLandmarks: Success! Got ${allLandmarks.size} landmarks")
                applyFilterSort()
                _error.value = null
            }
            is Result.Error -> {
                Log.e(TAG, "fetchLandmarks: Error - ${result.message}")
                _error.value = result.message
            }
            else -> {
                Log.d(TAG, "fetchLandmarks: Loading state")
            }
        }
        _isLoading.value = false
    }

    fun setMinScore(score: Double) {
        _minScore.value = score
        applyFilterSort()
    }

    fun setSortDescending(descending: Boolean) {
        _sortDescending.value = descending
        applyFilterSort()
    }

    private fun applyFilterSort() {
        val min = _minScore.value ?: 0.0
        val desc = _sortDescending.value ?: true
        var filtered = allLandmarks.filter { it.score >= min }
        filtered = if (desc) filtered.sortedByDescending { it.score }
        else filtered.sortedBy { it.score }
        Log.d(TAG, "applyFilterSort: Filtered ${filtered.size} of ${allLandmarks.size} landmarks (min score=$min, desc=$desc)")
        _landmarks.value = filtered
    }

    fun visitLandmark(landmarkId: Int, landmarkName: String, userLat: Double, userLon: Double) {
        Log.d(TAG, "visitLandmark: Starting visit for $landmarkName (id=$landmarkId)")
        viewModelScope.launch {
            try {
                _isLoading.value = true
                
                // Step 1: Record the visit
                Log.d(TAG, "visitLandmark: Recording visit to API...")
                when (val result = repository.visitLandmark(landmarkId, landmarkName, userLat, userLon)) {
                    is Result.Success -> {
                        _visitResult.value = result.data
                        val dist = result.data.distance
                        Log.d(TAG, "visitLandmark: Visit recorded! Distance=${dist}")
                        _successMessage.value = if (dist != null)
                            "Visit recorded! Distance: ${"%.2f".format(dist)} km"
                        else "Visit recorded successfully!"
                        
                        // Step 2: Wait for server to process the visit and update scores
                        Log.d(TAG, "visitLandmark: Waiting 1000ms for server to process...")
                        delay(1000)
                        
                        // Step 3: Fetch fresh landmark list with updated scores
                        Log.d(TAG, "visitLandmark: Fetching updated landmarks from API...")
                        when (val refreshResult = repository.fetchLandmarks()) {
                            is Result.Success -> {
                                Log.d(TAG, "visitLandmark: Fetched ${refreshResult.data.size} landmarks")
                                
                                // Find the updated landmark to log its new score
                                val updatedLandmark = refreshResult.data.find { it.id == landmarkId }
                                Log.d(TAG, "visitLandmark: Updated landmark $landmarkName - Score: ${updatedLandmark?.score}, Visits: ${updatedLandmark?.visitCount}")
                                
                                // Update the local list
                                allLandmarks = refreshResult.data
                                Log.d(TAG, "visitLandmark: Updated allLandmarks list")
                                
                                // Reapply filter/sort to update UI
                                applyFilterSort()
                                Log.d(TAG, "visitLandmark: UI refreshed with new rankings!")
                            }
                            is Result.Error -> {
                                Log.e(TAG, "visitLandmark: Refresh failed - ${refreshResult.message}")
                                // Don't mark as error - visit was recorded successfully, just refresh failed
                            }
                            else -> {
                                Log.d(TAG, "visitLandmark: Refresh loading state")
                            }
                        }
                    }
                    is Result.Error -> {
                        Log.e(TAG, "visitLandmark: Visit recording failed - ${result.message}")
                        _error.value = result.message
                        _visitResult.value = null
                    }
                    else -> {
                        Log.d(TAG, "visitLandmark: Loading state")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "visitLandmark: Unexpected error - ${e.message}", e)
                _error.value = "Error: ${e.message}"
            } finally {
                _isLoading.value = false
                Log.d(TAG, "visitLandmark: Complete")
            }
        }
    }


    fun createLandmark(title: String, lat: Double, lon: Double, imageFile: File) {
        Log.d(TAG, "createLandmark: Creating landmark=$title at ($lat,$lon) with image=${imageFile.name}")
        viewModelScope.launch {
            _isLoading.value = true
            when (val result = repository.createLandmark(title, lat, lon, imageFile)) {
                is Result.Success -> {
                    Log.d(TAG, "createLandmark: Success!")
                    _successMessage.value = "Landmark created successfully!"
                    // Wait for API to process, then refresh
                    delay(1000)
                    fetchLandmarks()
                }
                is Result.Error -> {
                    Log.e(TAG, "createLandmark: Error - ${result.message}")
                    _error.value = result.message
                }
                else -> {
                    Log.d(TAG, "createLandmark: Loading state")
                }
            }
            _isLoading.value = false
        }
    }

    fun deleteLandmark(id: Int) {
        Log.d(TAG, "deleteLandmark: Deleting landmark id=$id")
        // Simply remove from local list immediately - don't wait for API
        allLandmarks = allLandmarks.filter { it.id != id }
        applyFilterSort()
        _successMessage.value = "Landmark deleted."
        
        // Delete in background without blocking UI
        viewModelScope.launch {
            try {
                repository.deleteLandmark(id)
                Log.d(TAG, "deleteLandmark: API deletion complete")
            } catch (e: Exception) {
                Log.e(TAG, "deleteLandmark: Background error - ${e.message}")
            }
        }
    }

    fun restoreLandmark(id: Int) {
        Log.d(TAG, "restoreLandmark: Restoring landmark id=$id")
        viewModelScope.launch {
            _isLoading.value = true
            when (val result = repository.restoreLandmark(id)) {
                is Result.Success -> {
                    Log.d(TAG, "restoreLandmark: Success!")
                    _successMessage.value = "Landmark restored."
                    // Refresh to get the restored landmark back
                    fetchLandmarks()
                }
                is Result.Error -> {
                    Log.e(TAG, "restoreLandmark: Error - ${result.message}")
                    _error.value = result.message
                }
                else -> {
                    Log.d(TAG, "restoreLandmark: Loading state")
                }
            }
            _isLoading.value = false
        }
    }

    fun clearError() { _error.value = null }
    fun clearSuccess() { _successMessage.value = null }
    fun clearVisitResult() { _visitResult.value = null }
}
