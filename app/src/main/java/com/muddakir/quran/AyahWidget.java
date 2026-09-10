package com.muddakir.quran;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

public class AyahWidget extends AppWidgetProvider {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if (AppWidgetManager.ACTION_APPWIDGET_UPDATE.equals(action)) {
            // نأخذ إذنًا لتنفيذ عمل غير متزامن (اتصال شبكي) خارج الخيط الرئيسي
            // قبل أن يعتبر النظام البث "منتهيًا".
            final android.content.BroadcastReceiver.PendingResult pendingResult = goAsync();

            AppWidgetManager manager = AppWidgetManager.getInstance(context);
            final int[] ids = manager.getAppWidgetIds(new ComponentName(context, AyahWidget.class));
            final Context appContext = context.getApplicationContext();

            new Thread(() -> {
                try {
                    AyahWorker.fetchAndUpdateWidgets(appContext, ids);
                } finally {
                    pendingResult.finish();
                }
            }).start();
        } else {
            // نترك المعالجة الافتراضية لبقية الأحداث (enabled/disabled/deleted...)
            super.onReceive(context, intent);
        }
    }
}
