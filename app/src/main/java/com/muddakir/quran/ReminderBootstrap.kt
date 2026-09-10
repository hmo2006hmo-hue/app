package com.muddakir.quran

import android.content.Context

object ReminderBootstrap {
    fun ensureScheduled(context: Context) {
        runCatching { AdhkarReminderWorker.ensureScheduled(context) }
        runCatching { DailyContentReminderWorker.ensureScheduled(context) }
        runCatching { GeneralReminderWorker.ensureScheduled(context) }
    }
}
