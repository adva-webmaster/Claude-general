@file:Suppress("unused", "UNUSED_PARAMETER")
package androidx.work

import android.content.Context
import java.util.concurrent.TimeUnit

class WorkerParameters

abstract class ListenableWorker(val applicationContext: Context, params: WorkerParameters) {
    abstract class Result {
        companion object {
            fun success(): Result = error("stub")
            fun retry(): Result = error("stub")
        }
    }
}

abstract class CoroutineWorker(appContext: Context, params: WorkerParameters) : ListenableWorker(appContext, params) {
    abstract suspend fun doWork(): Result
}

enum class ExistingWorkPolicy { REPLACE, KEEP, APPEND, APPEND_OR_REPLACE }
enum class NetworkType { NOT_REQUIRED, CONNECTED }
enum class BackoffPolicy { LINEAR, EXPONENTIAL }

class Constraints {
    class Builder {
        fun setRequiredNetworkType(type: NetworkType): Builder = this
        fun build(): Constraints = Constraints()
    }
}

class OneTimeWorkRequest {
    class Builder {
        fun setConstraints(constraints: Constraints): Builder = this
        fun setBackoffCriteria(policy: BackoffPolicy, delay: Long, unit: TimeUnit): Builder = this
        fun build(): OneTimeWorkRequest = OneTimeWorkRequest()
    }
}

inline fun <reified W : ListenableWorker> OneTimeWorkRequestBuilder(): OneTimeWorkRequest.Builder = OneTimeWorkRequest.Builder()

class WorkManager {
    fun enqueueUniqueWork(uniqueWorkName: String, policy: ExistingWorkPolicy, request: OneTimeWorkRequest) {}
    companion object {
        fun getInstance(context: Context): WorkManager = WorkManager()
    }
}
