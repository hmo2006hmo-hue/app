package com.muddakir.quran;
import android.content.BroadcastReceiver; import android.content.Context; import android.content.Intent;
public class DailyContentReminderReceiver extends BroadcastReceiver { @Override public void onReceive(Context c, Intent i){ DailyContentReminderWorker.show(c); DailyContentReminderWorker.ensureScheduled(c); } }
