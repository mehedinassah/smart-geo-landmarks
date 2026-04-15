package com.smartlandmarks.data.model

import com.google.gson.annotations.SerializedName
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonDeserializationContext
import java.lang.reflect.Type

data class Landmark(
    @SerializedName("id") val id: Int,
    @SerializedName("title") val title: String,
    @SerializedName("lat") val lat: Double,
    @SerializedName("lon") val lon: Double,
    @SerializedName("image") val image: String?,
    @SerializedName("score") val score: Double,
    @SerializedName("visit_count") val visitCount: Int = 0,
    @SerializedName("avg_distance") val avgDistance: Double? = null,
    @SerializedName("deleted") val deleted: Int = 0
)

data class LandmarksResponse(
    @SerializedName("status") val status: String? = null,
    @SerializedName("landmarks") val landmarks: List<Landmark>? = null,
    @SerializedName("data") val data: List<Landmark>? = null
) {
    fun getLandmarkList(): List<Landmark> {
        return (landmarks ?: data ?: emptyList()).filter { it.deleted == 0 }
    }
}

// Custom deserializer to handle both array and object responses
class LandmarksResponseDeserializer : JsonDeserializer<LandmarksResponse> {
    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): LandmarksResponse {
        if (json == null) return LandmarksResponse()
        
        // If it's an array, wrap it in an object
        if (json.isJsonArray) {
            val landmarks = context?.deserialize<List<Landmark>>(json, object : com.google.gson.reflect.TypeToken<List<Landmark>>() {}.type)
            return LandmarksResponse(status = "success", landmarks = landmarks)
        }
        
        // If it's an object, deserialize normally
        if (json.isJsonObject) {
            val obj = json.asJsonObject
            val status = obj.get("status")?.asString
            val landmarks = context?.deserialize<List<Landmark>>(
                obj.get("landmarks"),
                object : com.google.gson.reflect.TypeToken<List<Landmark>>() {}.type
            )
            val data = context?.deserialize<List<Landmark>>(
                obj.get("data"),
                object : com.google.gson.reflect.TypeToken<List<Landmark>>() {}.type
            )
            return LandmarksResponse(status = status, landmarks = landmarks, data = data)
        }
        
        return LandmarksResponse()
    }
}

data class VisitRequest(
    @SerializedName("landmark_id") val landmarkId: Int,
    @SerializedName("user_lat") val userLat: Double,
    @SerializedName("user_lon") val userLon: Double
)

data class VisitResponse(
    @SerializedName("status") val status: String?,
    @SerializedName("message") val message: String?,
    @SerializedName("distance") val distance: Double?,
    @SerializedName("score") val score: Double?,
    @SerializedName("error") val error: String?
)

data class GenericResponse(
    @SerializedName("status") val status: String?,
    @SerializedName("message") val message: String?,
    @SerializedName("error") val error: String?,
    @SerializedName("id") val id: Int?
)
