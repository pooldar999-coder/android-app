package com.phoneguard.app

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

class ThreatFeedWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : Worker(appContext, workerParams) {
    override fun doWork(): Result {
        return try {
            ThreatFeedManager(applicationContext).update()
            Result.success()
        } catch (_: Exception) {
            if (runAttemptCount < 5) Result.retry() else Result.failure()
        }
    }
}
