package com.gn.gestion.data.sync;

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.gn.gestion.services.SyncEngine;
import java.util.concurrent.TimeUnit

class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val resultado = SyncEngine.sincronizar(applicationContext)
        return if (resultado.ok || resultado.detalle == "Sin sesión") {
            Result.success()
        } else {
            Result.retry()
        }
    }

    companion object {

        private const val UNICO = "sync_inmediato"
        private const val PERIODICO = "sync_periodico"

        fun encolar(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(UNICO, ExistingWorkPolicy.KEEP, request)
        }

        fun programar(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniquePeriodicWork(
                    PERIODICO,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
        }
    }
}