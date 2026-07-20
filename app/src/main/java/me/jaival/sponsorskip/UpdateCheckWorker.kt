/*
 * Sponsor Skip - Auto-skips SponsorBlock segments in YouTube videos
 * Copyright (C) 2026 Jaival
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.jaival.sponsorskip

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class UpdateCheckWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        AppLogger.log("[UPDATER] Periodic 24h update check worker started.")
        val success = UpdateManager.checkUpdateBackground(appContext)
        return if (success) {
            Result.success()
        } else {
            AppLogger.log("[UPDATER] Periodic 24h update check failed (no internet or error), retrying later.")
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "periodic_update_check"

        fun schedule(context: Context) {
            try {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val workRequest = PeriodicWorkRequestBuilder<UpdateCheckWorker>(24, TimeUnit.HOURS)
                    .setConstraints(constraints)
                    .build()

                WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    workRequest
                )
                AppLogger.log("[UPDATER] Scheduled 24h periodic update check with WorkManager.")
            } catch (e: Exception) {
                AppLogger.log("[UPDATER] Failed to schedule periodic WorkManager job: ${e.message}")
            }
        }

        fun runNow(context: Context) {
            try {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val workRequest = androidx.work.OneTimeWorkRequestBuilder<UpdateCheckWorker>()
                    .setConstraints(constraints)
                    .build()

                WorkManager.getInstance(context.applicationContext).enqueue(workRequest)
                AppLogger.log("[UPDATER] Enqueued immediate one-time background update check worker.")
            } catch (e: Exception) {
                AppLogger.log("[UPDATER] Failed to enqueue immediate worker: ${e.message}")
            }
        }
    }
}
