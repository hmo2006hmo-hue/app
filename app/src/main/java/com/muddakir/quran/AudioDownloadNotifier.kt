package com.muddakir.quran

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import java.util.concurrent.atomic.AtomicInteger

/** System notification progress for audio downloads. */
object AudioDownloadNotifier {
    private const val CHANNEL_ID = "audio_downloads"
    private const val CHANNEL_NAME = "تحميل الصوتيات"
    private const val CHANNEL_DESCRIPTION = "متابعة تقدم تحميل تلاوات القرآن"
    private val nextId = AtomicInteger(51_000)

    class Task internal constructor(
        private val context: Context,
        private val notificationId: Int,
        private val title: String
    ) {
        private var lastPercent = -1
        private var lastPublishedAt = 0L
        private var finished = false

        fun update(percent: Int, detail: String = "") {
            if (finished) return
            val safePercent = percent.coerceIn(0, 100)
            val now = System.currentTimeMillis()
            val shouldPublish = safePercent != lastPercent &&
                (safePercent == 0 || safePercent == 100 || now - lastPublishedAt >= 250L || safePercent - lastPercent >= 2)
            if (!shouldPublish) return
            lastPercent = safePercent
            lastPublishedAt = now
            publish(
                ongoing = safePercent < 100,
                percent = safePercent,
                detail = detail.ifBlank { "التقدم: $safePercent%" }
            )
        }

        fun updateBytes(downloadedBytes: Long, totalBytes: Long, detail: String = "") {
            if (totalBytes > 0L) {
                val percent = ((downloadedBytes.toDouble() / totalBytes.toDouble()) * 100.0).toInt()
                update(percent, detail.ifBlank { "جارٍ التحميل: $percent%" })
            }
        }

        fun complete(detail: String = "اكتمل التحميل ✅") {
            if (finished) return
            finished = true
            publish(ongoing = false, percent = 100, detail = detail)
        }

        fun fail(detail: String = "فشل التحميل") {
            if (finished) return
            finished = true
            publish(ongoing = false, percent = 0, detail = detail)
        }

        private fun publish(ongoing: Boolean, percent: Int, detail: String) {
            if (!canNotify(context)) return
            ensureChannel(context)
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(title)
                .setContentText(detail)
                .setOnlyAlertOnce(true)
                .setOngoing(ongoing)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)

            if (ongoing || percent > 0) {
                builder.setProgress(100, percent, false)
            }
            NotificationManagerCompatHolder.notify(context, notificationId, builder.build())
        }
    }

    fun start(context: Context, title: String): Task? {
        if (!canNotify(context)) return null
        ensureChannel(context)
        val task = Task(context.applicationContext, nextId.getAndIncrement(), title)
        task.update(0, "جاري بدء التحميل…")
        return task
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW).apply {
                description = CHANNEL_DESCRIPTION
            }
        )
    }

    private fun canNotify(context: Context): Boolean {
        return Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    private object NotificationManagerCompatHolder {
        fun notify(context: Context, id: Int, notification: android.app.Notification) {
            androidx.core.app.NotificationManagerCompat.from(context).notify(id, notification)
        }
    }
}
