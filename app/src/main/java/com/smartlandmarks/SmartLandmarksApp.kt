package com.smartlandmarks

import android.app.Application
import com.smartlandmarks.worker.SyncWorker

class SmartLandmarksApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Schedule periodic background sync
        SyncWorker.schedulePeriodicSync(this)
    }
}
