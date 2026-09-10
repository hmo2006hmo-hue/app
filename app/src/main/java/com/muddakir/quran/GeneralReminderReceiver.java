package com.muddakir.quran;
import android.content.*;
public class GeneralReminderReceiver extends BroadcastReceiver{public void onReceive(Context c,Intent i){String t=i.getStringExtra("type"); if(t!=null)GeneralReminderWorker.show(c,t); GeneralReminderWorker.ensureScheduled(c);}}
