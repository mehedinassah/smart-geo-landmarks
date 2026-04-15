package com.smartlandmarks.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.smartlandmarks.data.api.LandmarkApiService
import com.smartlandmarks.data.local.*
import com.smartlandmarks.data.model.*
import com.smartlandmarks.utils.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

private const val TAG = "LandmarkRepository"

sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val message: String) : Result<Nothing>()
    object Loading : Result<Nothing>()
}

class LandmarkRepository(
    private val apiService: LandmarkApiService,
    private val landmarkDao: LandmarkDao,
    private val visitHistoryDao: VisitHistoryDao,
    private val pendingVisitDao: PendingVisitDao,
    private val context: Context
) {

    val landmarksLive = landmarkDao.getAllLandmarks()
    val visitHistoryLive = visitHistoryDao.getAllVisits()

    private fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    suspend fun fetchLandmarks(): Result<List<Landmark>> = withContext(Dispatchers.IO) {
        Log.d(TAG, "fetchLandmarks: Starting...")
        
        // Always return cache first if available (instant load)
        val cached = landmarkDao.getAllLandmarksList()
        if (cached.isNotEmpty()) {
            Log.d(TAG, "fetchLandmarks: Returning cached ${cached.size} landmarks")
        }
        
        if (!isOnline()) {
            Log.d(TAG, "fetchLandmarks: Offline - using cache")
            return@withContext if (cached.isNotEmpty()) {
                Log.d(TAG, "fetchLandmarks: Found ${cached.size} cached landmarks")
                Result.Success(cached.map { it.toModel() })
            } else {
                Log.e(TAG, "fetchLandmarks: No internet and no cache")
                Result.Error("No internet connection and no cached data available.")
            }
        }
        
        try {
            Log.d(TAG, "fetchLandmarks: Fetching from API with key: ${Constants.API_KEY}")
            val response = apiService.getLandmarks(key = Constants.API_KEY)
            Log.d(TAG, "fetchLandmarks: Response code = ${response.code()}, isSuccessful = ${response.isSuccessful}")
            
            if (response.isSuccessful) {
                val body = response.body()
                Log.d(TAG, "fetchLandmarks: Response body = $body")
                val list = body?.getLandmarkList() ?: emptyList()
                Log.d(TAG, "fetchLandmarks: Got ${list.size} landmarks from API")
                // Insert/update into database - REPLACE strategy handles both
                if (list.isNotEmpty()) {
                    landmarkDao.insertAll(list.map { it.toEntity() })
                }
                // KEY FIX: Read ALL landmarks from database, not just API response
                // This keeps locally created landmarks visible
                val allFromDb = landmarkDao.getAllLandmarksList()
                Log.d(TAG, "fetchLandmarks: Returning ${allFromDb.size} landmarks from DB (includes local ones)")
                Result.Success(allFromDb.map { it.toModel() })
            } else {
                Log.e(TAG, "fetchLandmarks: API error ${response.code()}")
                // Return cache as fallback
                if (cached.isNotEmpty()) Result.Success(cached.map { it.toModel() })
                else Result.Error("API Error: ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchLandmarks: Exception = ${e.message}", e)
            // Return cache as fallback
            if (cached.isNotEmpty()) {
                Log.d(TAG, "fetchLandmarks: API failed, returning cached data")
                Result.Success(cached.map { it.toModel() })
            } else {
                Result.Error(e.message ?: "Unknown error")
            }
        }
    }

    suspend fun visitLandmark(
        landmarkId: Int,
        landmarkName: String,
        userLat: Double,
        userLon: Double
    ): Result<VisitResponse> = withContext(Dispatchers.IO) {
        Log.d(TAG, "visitLandmark: Starting visit for landmark=$landmarkName (id=$landmarkId), userLat=$userLat, userLon=$userLon")
        if (!isOnline()) {
            Log.d(TAG, "visitLandmark: Offline - queuing for sync")
            // Queue for later
            pendingVisitDao.insert(
                PendingVisitEntity(
                    landmarkId = landmarkId,
                    landmarkName = landmarkName,
                    userLat = userLat,
                    userLon = userLon
                )
            )
            // Save to history as pending
            visitHistoryDao.insert(
                VisitHistoryEntity(
                    landmarkId = landmarkId,
                    landmarkName = landmarkName,
                    distance = null,
                    synced = false
                )
            )
            return@withContext Result.Error("Offline: Visit queued for sync when internet returns.")
        }
        try {
            val request = VisitRequest(landmarkId, userLat, userLon)
            Log.d(TAG, "visitLandmark: Sending API request with landmarkId=$landmarkId, userLat=$userLat, userLon=$userLon")
            Log.d(TAG, "visitLandmark: Full request body=$request")
            val response = apiService.visitLandmark(key = Constants.API_KEY, body = request)
            Log.d(TAG, "visitLandmark: Response code = ${response.code()}, isSuccessful = ${response.isSuccessful}")
            Log.d(TAG, "visitLandmark: Response body = ${response.body()}")
            if (response.isSuccessful) {
                val body = response.body()
                if (body?.error != null) {
                    Log.e(TAG, "visitLandmark: API returned error = ${body.error}")
                    Result.Error(body.error)
                } else {
                    val distance = body?.distance
                    Log.d(TAG, "visitLandmark: Visit successful, distance = $distance km")
                    // Save to local history
                    visitHistoryDao.insert(
                        VisitHistoryEntity(
                            landmarkId = landmarkId,
                            landmarkName = landmarkName,
                            distance = body?.distance,
                            synced = true
                        )
                    )
                    // Keep only last 20 visits - delete oldest ones beyond 20
                    visitHistoryDao.deleteOldestBeyond20()
                    Log.d(TAG, "visitLandmark: Saved to history, keeping only last 20 visits")
                    Result.Success(body ?: VisitResponse(null, null, null, null, null))
                }
            } else {
                Log.e(TAG, "visitLandmark: API error ${response.code()}")
                Result.Error("Visit failed: ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "visitLandmark: Exception = ${e.message}", e)
            Result.Error(e.message ?: "Visit request failed")
        }
    }

    suspend fun createLandmark(
        title: String,
        lat: Double,
        lon: Double,
        imageFile: File
    ): Result<GenericResponse> = withContext(Dispatchers.IO) {
        try {
            val titleBody = title.toRequestBody("text/plain".toMediaTypeOrNull())
            val latBody = lat.toString().toRequestBody("text/plain".toMediaTypeOrNull())
            val lonBody = lon.toString().toRequestBody("text/plain".toMediaTypeOrNull())
            val imageBody = imageFile.asRequestBody("image/*".toMediaTypeOrNull())
            val imagePart = MultipartBody.Part.createFormData("image", imageFile.name, imageBody)

            val response = apiService.createLandmark(
                key = Constants.API_KEY,
                title = titleBody,
                lat = latBody,
                lon = lonBody,
                image = imagePart
            )
            if (response.isSuccessful) {
                Result.Success(response.body() ?: GenericResponse(null, null, null, null))
            } else {
                Result.Error("Create failed: ${response.code()}")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Failed to create landmark")
        }
    }

    suspend fun deleteLandmark(id: Int): Result<GenericResponse> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "deleteLandmark: Starting delete for id=$id")
            val response = apiService.deleteLandmark(key = Constants.API_KEY, id = id.toString())
            Log.d(TAG, "deleteLandmark: Response code = ${response.code()}, isSuccessful = ${response.isSuccessful}")
            Log.d(TAG, "deleteLandmark: Response body = ${response.body()}")
            if (response.isSuccessful) {
                landmarkDao.softDelete(id)
                Log.d(TAG, "deleteLandmark: Soft deleted id=$id from local DB")
                Result.Success(response.body() ?: GenericResponse(null, null, null, null))
            } else {
                Log.e(TAG, "deleteLandmark: API error ${response.code()}")
                Result.Error("Delete failed: ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "deleteLandmark: Exception = ${e.message}", e)
            Result.Error(e.message ?: "Delete failed")
        }
    }

    suspend fun restoreLandmark(id: Int): Result<GenericResponse> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "restoreLandmark: Starting restore for id=$id")
            val response = apiService.restoreLandmark(key = Constants.API_KEY, id = id.toString())
            Log.d(TAG, "restoreLandmark: Response code = ${response.code()}, isSuccessful = ${response.isSuccessful}")
            Log.d(TAG, "restoreLandmark: Response body = ${response.body()}")
            if (response.isSuccessful) {
                landmarkDao.restore(id)
                Log.d(TAG, "restoreLandmark: Restored id=$id in local DB")
                Result.Success(response.body() ?: GenericResponse(null, null, null, null))
            } else {
                Log.e(TAG, "restoreLandmark: API error ${response.code()}")
                Result.Error("Restore failed: ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "restoreLandmark: Exception = ${e.message}", e)
            Result.Error(e.message ?: "Restore failed")
        }
    }


    suspend fun syncPendingVisits(): Int = withContext(Dispatchers.IO) {
        if (!isOnline()) return@withContext 0
        val pending = pendingVisitDao.getAllPending()
        var synced = 0
        for (pv in pending) {
            try {
                val request = VisitRequest(pv.landmarkId, pv.userLat, pv.userLon)
                val response = apiService.visitLandmark(key = Constants.API_KEY, body = request)
                if (response.isSuccessful) {
                    pendingVisitDao.deleteById(pv.id)
                    synced++
                }
            } catch (_: Exception) {}
        }
        synced
    }

    // Extension functions
    private fun Landmark.toEntity() = LandmarkEntity(
        id = id, title = title, lat = lat, lon = lon,
        image = image, score = score, visitCount = visitCount,
        avgDistance = avgDistance, deleted = deleted
    )

    private fun LandmarkEntity.toModel() = Landmark(
        id = id, title = title, lat = lat, lon = lon,
        image = image, score = score, visitCount = visitCount,
        avgDistance = avgDistance, deleted = deleted
    )
}
