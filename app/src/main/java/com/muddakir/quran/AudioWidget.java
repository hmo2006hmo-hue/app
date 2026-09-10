package com.muddakir.quran;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

public class AudioWidget extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        int layoutId = context.getResources().getIdentifier("widget_audio_layout", "layout", context.getPackageName());
        int btnPlayId = context.getResources().getIdentifier("btn_widget_play", "id", context.getPackageName());

        for (int appWidgetId : appWidgetIds) {
            RemoteViews views = new RemoteViews(context.getPackageName(), layoutId);

            Intent intent = new Intent(context, MainActivity.class);
            intent.putExtra("auto_play", true);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            
            PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            views.setOnClickPendingIntent(btnPlayId, pendingIntent);

            appWidgetManager.updateAppWidget(appWidgetId, views);
        }
    }
}