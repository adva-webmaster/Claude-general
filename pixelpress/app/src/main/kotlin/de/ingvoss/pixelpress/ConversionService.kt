package de.ingvoss.pixelpress

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Vordergrunddienst: hält den Prozess und den Prozessor wach, solange umgewandelt wird, und zeigt den Stand in einer
 * Benachrichtigung (mit "Abbrechen"). Die Arbeit selbst macht der [ConversionController].
 */
class ConversionService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watcher: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val controller = (application as PixelPressApp).controller
        if (intent?.action == ACTION_CANCEL) {
            controller.cancel()
            return START_NOT_STICKY
        }
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.notification_preparing), null), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        if (wakeLock == null) {
            val power = getSystemService(PowerManager::class.java)
            wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PixelPress:convert").apply { acquire(MAX_WAKE_MS) }
        }
        watcher?.cancel()
        watcher = scope.launch {
            controller.state.collectLatest { state ->
                if (!state.running && !state.preparing) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return@collectLatest
                }
                val active = state.items.filter { it.selected && it.state != ItemState.SKIPPED }
                val finished = active.count { it.state == ItemState.DONE || it.state == ItemState.FAILED }
                val current = active.firstOrNull { it.state == ItemState.RUNNING }
                val overall = if (active.isEmpty()) null else (finished + (current?.progress ?: 0f)) / active.size
                val text = getString(R.string.notification_progress, finished, active.size)
                getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(text, overall))
            }
        }
        return START_NOT_STICKY
    }

    /** Android 15 begrenzt Datensynchronisations-Dienste in der Dauer: dann sauber abbrechen. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        (application as PixelPressApp).controller.cancel()
        stopSelf()
    }

    override fun onDestroy() {
        watcher?.cancel()
        scope.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW))
    }

    private fun buildNotification(text: String, progress: Float?): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val cancel = PendingIntent.getService(
            this, 1, Intent(this, ConversionService::class.java).setAction(ACTION_CANCEL), PendingIntent.FLAG_IMMUTABLE
        )
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(null, getString(R.string.action_cancel), cancel).build())
        if (progress != null) builder.setProgress(PROGRESS_MAX, (progress * PROGRESS_MAX).toInt(), false) else builder.setProgress(0, 0, true)
        return builder.build()
    }

    private companion object {
        const val CHANNEL_ID = "convert"
        const val NOTIFICATION_ID = 1
        const val ACTION_CANCEL = "de.ingvoss.pixelpress.CANCEL"
        const val PROGRESS_MAX = 1000
        const val MAX_WAKE_MS = 6L * 60 * 60 * 1000
    }
}
