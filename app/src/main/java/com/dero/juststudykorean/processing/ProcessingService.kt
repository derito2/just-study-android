package com.dero.juststudykorean.processing

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.dero.juststudykorean.R
import com.dero.juststudykorean.data.documents.STAGE_CHUNKING
import com.dero.juststudykorean.data.documents.STAGE_EXTRACTING
import com.dero.juststudykorean.data.documents.STAGE_INDEXING
import com.dero.juststudykorean.data.documents.STAGE_PARSING
import com.dero.juststudykorean.data.processing.ProcessingQueue
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Processes the queue in a foreground service (type dataSync) with a progress notification, one PDF at a time.
 * If ColorOS kills the app, the queue is still in Room: the next time the app opens it goes on from the last saved stage.
 * Without POST_NOTIFICATIONS the PDF is processed the same, without the notification.
 */
@AndroidEntryPoint
class ProcessingService : Service() {
    @Inject lateinit var queue: ProcessingQueue

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var run: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, notification(getString(R.string.processing_starting), 0), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        if (run?.isActive != true) {
            run = scope.launch {
                try {
                    do {
                        queue.runAll { _, stage ->
                            val step = STEPS.indexOf(stage).coerceAtLeast(0)
                            getSystemService(NotificationManager::class.java)
                                .notify(NOTIFICATION_ID, notification(getString(STAGE_NAMES.getValue(stage)), (step + 1) * 100 / STEPS.size))
                        }
                    } while (queue.hasWork()) // a PDF queued while the last one finished
                } finally {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY // the queue is in Room: the app starts it again when it opens
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    /** No file names or note text: the stage and how far it is. */
    private fun notification(text: String, percent: Int) = NotificationCompat.Builder(this, CHANNEL)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle(getString(R.string.processing_title))
        .setContentText(text)
        .setProgress(100, percent, percent == 0)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .build()

    companion object {
        private const val CHANNEL = "processing"
        private const val NOTIFICATION_ID = 4
        private val STEPS = listOf(STAGE_EXTRACTING, STAGE_PARSING, STAGE_CHUNKING, STAGE_INDEXING)
        private val STAGE_NAMES = mapOf(
            STAGE_EXTRACTING to R.string.stage_extracting, STAGE_PARSING to R.string.stage_parsing,
            STAGE_CHUNKING to R.string.stage_chunking, STAGE_INDEXING to R.string.stage_indexing,
        )

        /** Starts processing what is queued; only from the foreground (an upload, Reprocess, or opening the app). */
        fun start(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.processing_channel), NotificationManager.IMPORTANCE_LOW))
            ContextCompat.startForegroundService(context, Intent(context, ProcessingService::class.java))
        }
    }
}
