package com.muddakir.quran;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        AdhkarReminderWorker.ensureScheduled(context);
        DailyContentReminderWorker.ensureScheduled(context);
        GeneralReminderWorker.ensureScheduled(context);
    }
}
