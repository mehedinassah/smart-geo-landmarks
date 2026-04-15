package com.smartlandmarks.data.api

import com.smartlandmarks.data.model.GenericResponse
import com.smartlandmarks.data.model.LandmarksResponse
import com.smartlandmarks.data.model.VisitRequest
import com.smartlandmarks.data.model.VisitResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

interface LandmarkApiService {

    @GET("api.php")
    suspend fun getLandmarks(
        @Query("action") action: String = "get_landmarks",
        @Query("key") key: String
    ): Response<LandmarksResponse>

    @POST("api.php")
    suspend fun visitLandmark(
        @Query("action") action: String = "visit_landmark",
        @Query("key") key: String,
        @Body body: VisitRequest
    ): Response<VisitResponse>

    @Multipart
    @POST("api.php")
    suspend fun createLandmark(
        @Query("action") action: String = "create_landmark",
        @Query("key") key: String,
        @Part("title") title: RequestBody,
        @Part("lat") lat: RequestBody,
        @Part("lon") lon: RequestBody,
        @Part image: MultipartBody.Part
    ): Response<GenericResponse>

    @FormUrlEncoded
    @POST("api.php")
    suspend fun deleteLandmark(
        @Query("action") action: String = "delete_landmark",
        @Query("key") key: String,
        @Field("id") id: String
    ): Response<GenericResponse>

    @FormUrlEncoded
    @POST("api.php")
    suspend fun restoreLandmark(
        @Query("action") action: String = "restore_landmark",
        @Query("key") key: String,
        @Field("id") id: String
    ): Response<GenericResponse>
}
