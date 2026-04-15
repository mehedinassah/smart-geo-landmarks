package com.smartlandmarks.data.local

import androidx.lifecycle.LiveData
import androidx.room.*

// ==================== ENTITIES ====================

@Entity(tableName = "landmarks")
data class LandmarkEntity(
    @PrimaryKey val id: Int,
    val title: String,
    val lat: Double,
    val lon: Double,
    val image: String?,
    val score: Double,
    val visitCount: Int = 0,
    val avgDistance: Double? = null,
    val deleted: Int = 0,
    val cachedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "visit_history")
data class VisitHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val landmarkId: Int,
    val landmarkName: String,
    val visitTime: Long = System.currentTimeMillis(),
    val distance: Double?,
    val synced: Boolean = true
)

@Entity(tableName = "pending_visits")
data class PendingVisitEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val landmarkId: Int,
    val landmarkName: String,
    val userLat: Double,
    val userLon: Double,
    val createdAt: Long = System.currentTimeMillis()
)

// ==================== DAOs ====================

@Dao
interface LandmarkDao {
    @Query("SELECT * FROM landmarks WHERE deleted = 0 ORDER BY score DESC")
    fun getAllLandmarks(): LiveData<List<LandmarkEntity>>

    @Query("SELECT * FROM landmarks WHERE deleted = 0 ORDER BY score DESC")
    suspend fun getAllLandmarksList(): List<LandmarkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(landmarks: List<LandmarkEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(landmark: LandmarkEntity)

    @Query("UPDATE landmarks SET score = :score, visitCount = :visitCount WHERE id = :id AND deleted = 0")
    suspend fun updateScoreAndVisits(id: Int, score: Double, visitCount: Int)

    @Query("UPDATE landmarks SET deleted = 1 WHERE id = :id")
    suspend fun softDelete(id: Int)

    @Query("UPDATE landmarks SET deleted = 0 WHERE id = :id")
    suspend fun restore(id: Int)

    @Query("DELETE FROM landmarks")
    suspend fun deleteAll()

    @Query("SELECT * FROM landmarks WHERE id = :id")
    suspend fun getById(id: Int): LandmarkEntity?
}

@Dao
interface VisitHistoryDao {
    @Query("SELECT * FROM visit_history ORDER BY visitTime DESC")
    fun getAllVisits(): LiveData<List<VisitHistoryEntity>>

    @Insert
    suspend fun insert(visit: VisitHistoryEntity)

    @Query("SELECT COUNT(*) FROM visit_history")
    suspend fun getCount(): Int

    @Query("DELETE FROM visit_history WHERE id NOT IN (SELECT id FROM visit_history ORDER BY visitTime DESC LIMIT 20)")
    suspend fun deleteOldestBeyond20()

    @Query("DELETE FROM visit_history")
    suspend fun deleteAll()
}

@Dao
interface PendingVisitDao {
    @Query("SELECT * FROM pending_visits ORDER BY createdAt ASC")
    suspend fun getAllPending(): List<PendingVisitEntity>

    @Insert
    suspend fun insert(visit: PendingVisitEntity)

    @Delete
    suspend fun delete(visit: PendingVisitEntity)

    @Query("DELETE FROM pending_visits WHERE id = :id")
    suspend fun deleteById(id: Int)
}
