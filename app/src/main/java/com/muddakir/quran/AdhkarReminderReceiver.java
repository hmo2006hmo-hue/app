package com.muddakir.quran;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class AdhkarReminderReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        String type = intent != null ? intent.getStringExtra("type") : null;
        if (type != null) AdhkarReminderWorker.showReminderNow(context, type);
        AdhkarReminderWorker.ensureScheduled(context); // schedule next exact occurrence
    }
}
