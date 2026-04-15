package com.smartlandmarks.worker

import android.content.Context
import androidx.work.*
import com.smartlandmarks.data.api.RetrofitClient
import com.smartlandmarks.data.local.AppDatabase
import com.smartlandmarks.data.model.VisitRequest
import com.smartlandmarks.utils.Constants

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val db = AppDatabase.getInstance(applicationContext)
        val pendingDao = db.pendingVisitDao()
        val historyDao = db.visitHistoryDao()
        val api = RetrofitClient.instance

        val pending = pendingDao.getAllPending()
        for (pv in pending) {
            try {
                val response = api.visitLandmark(
                    key = Constants.API_KEY,
                    body = VisitRequest(pv.landmarkId, pv.userLat, pv.userLon)
                )
                if (response.isSuccessful) {
                    pendingDao.deleteById(pv.id)
                }
            } catch (_: Exception) {
                // Will retry on next sync
            }
        }
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork("sync_visits", ExistingWorkPolicy.REPLACE, request)
        }

        fun schedulePeriodicSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<SyncWorker>(
                15, java.util.concurrent.TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    "periodic_sync",
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
        }
    }
}
