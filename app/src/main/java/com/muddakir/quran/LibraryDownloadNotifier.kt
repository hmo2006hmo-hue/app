package com.muddakir.quran

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/** Android system progress notification for long library book downloads. */
object LibraryDownloadNotifier {
    private const val CHANNEL_ID = "library_downloads"
    private const val CHANNEL_NAME = "تنزيلات المكتبة"

    private fun canNotify(context: Context): Boolean =
        android.os.Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun ensureChannel(context: Context) {
        if (android.os.Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun id(bookId: Long): Int = 27000 + (bookId and 0x3fff).toInt()

    fun start(context: Context, bookId: Long, title: String, expectedBytes: Long?) {
        update(context, bookId, title, 0f, expectedBytes, indeterminate = expectedBytes == null)
    }

    fun update(context: Context, bookId: Long, title: String, fraction: Float, expectedBytes: Long?, indeterminate: Boolean = false) {
        if (!canNotify(context)) return
        ensureChannel(context)
        val safe = fraction.coerceIn(0f, 1f)
        val percent = (safe * 100f).toInt()
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("تنزيل الكتاب")
            .setContentText("$title • $percent٪")
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setProgress(100, percent, indeterminate)
        if (expectedBytes != null && expectedBytes > 0L) {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText("$title\nتقدم التنزيل: $percent٪\nالحجم: ${formatBytes(safe * expectedBytes)} / ${formatBytes(expectedBytes.toFloat())}"))
        }
        NotificationManagerCompat.from(context).notify(id(bookId), builder.build())
    }

    fun success(context: Context, bookId: Long, title: String) {
        if (!canNotify(context)) return
        ensureChannel(context)
        NotificationManagerCompat.from(context).notify(
            id(bookId),
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("اكتمل تنزيل الكتاب")
                .setContentText(title)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
        )
    }

    fun failure(context: Context, bookId: Long, title: String, error: String) {
        if (!canNotify(context)) return
        ensureChannel(context)
        NotificationManagerCompat.from(context).notify(
            id(bookId),
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle("فشل تنزيل الكتاب")
                .setContentText(title)
                .setStyle(NotificationCompat.BigTextStyle().bigText("$title\n$error"))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
        )
    }

    fun cancel(context: Context, bookId: Long) {
        NotificationManagerCompat.from(context).cancel(id(bookId))
    }

    private fun formatBytes(bytes: Float): String = when {
        bytes >= 1024f * 1024f -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1048576f)
        bytes >= 1024f -> String.format(java.util.Locale.US, "%.1f KB", bytes / 1024f)
        else -> String.format(java.util.Locale.US, "%.0f B", bytes)
    }
}
