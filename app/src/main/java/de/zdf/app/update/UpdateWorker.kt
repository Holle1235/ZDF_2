package de.zdf.app.update

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** Sucht im Hintergrund (alle 6 h) nach Updates und installiert sie, wenn das ohne Rückfrage möglich ist. */
class UpdateWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (!UpdatePrefs.auto(ctx)) return Result.success()
        val rel = Updater.check(ctx) ?: return Result.success()
        if (Updater.canUpdateSilently(ctx)) Updater.downloadAndInstall(ctx, rel)
        return Result.success()
    }

    companion object {
        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<UpdateWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build())
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("zdf-update", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}

object UpdatePrefs {
    fun auto(ctx: Context) = ctx.getSharedPreferences("zdf_update", 0).getBoolean("auto", true)
    fun setAuto(ctx: Context, on: Boolean) = ctx.getSharedPreferences("zdf_update", 0).edit().putBoolean("auto", on).apply()
}
