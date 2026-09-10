package com.muddakir.quran;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import java.util.Calendar;

/** Exact daily adhkar reminders. Source metadata is kept explicit for the Shi'a sources. */
public final class AdhkarReminderWorker {
    public static final String PREFS_NAME = "adhkar_reminder_prefs";
    public static final String TYPE_MORNING = "morning", TYPE_EVENING = "evening", TYPE_SLEEP = "sleep";
    private static final String CHANNEL_ID = "adhkar_reminders";
    private AdhkarReminderWorker() {}

    public static void setReminder(Context c, String type, boolean enabled, int hour, int minute) {
        if (!valid(type)) return;
        hour=Math.max(0,Math.min(23,hour)); minute=Math.max(0,Math.min(59,minute));
        c.getSharedPreferences(PREFS_NAME,0).edit().putBoolean("enabled_"+type,enabled).putInt("hour_"+type,hour).putInt("minute_"+type,minute).apply();
        if(enabled) schedule(c,type,hour,minute); else cancel(c,type);
    }
    public static void ensureScheduled(Context c){
        for(String t:new String[]{TYPE_MORNING,TYPE_EVENING,TYPE_SLEEP}){
            SharedPreferences p=c.getSharedPreferences(PREFS_NAME,0);
            if(p.getBoolean("enabled_"+t,false)) schedule(c,t,p.getInt("hour_"+t,defaultHour(t)),p.getInt("minute_"+t,0)); else cancel(c,t);
        }
    }
    public static boolean isEnabled(Context c,String t){return valid(t)&&c.getSharedPreferences(PREFS_NAME,0).getBoolean("enabled_"+t,false);}
    public static String getTime(Context c,String t){SharedPreferences p=c.getSharedPreferences(PREFS_NAME,0);return String.format(java.util.Locale.US,"%02d:%02d",p.getInt("hour_"+t,defaultHour(t)),p.getInt("minute_"+t,0));}
    private static void schedule(Context c,String t,int h,int m){
        AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE); if(am==null)return;
        Calendar x=Calendar.getInstance(); x.set(Calendar.HOUR_OF_DAY,h);x.set(Calendar.MINUTE,m);x.set(Calendar.SECOND,0);x.set(Calendar.MILLISECOND,0); if(x.getTimeInMillis()<=System.currentTimeMillis())x.add(Calendar.DAY_OF_YEAR,1);
        PendingIntent pi=pending(c,t);
        if(Build.VERSION.SDK_INT>=31 && !am.canScheduleExactAlarms()) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,x.getTimeInMillis(),pi);
        else if(Build.VERSION.SDK_INT>=23) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,x.getTimeInMillis(),pi);
        else am.setExact(AlarmManager.RTC_WAKEUP,x.getTimeInMillis(),pi);
    }
    private static void cancel(Context c,String t){AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);if(am!=null)am.cancel(pending(c,t));}
    private static PendingIntent pending(Context c,String t){Intent i=new Intent(c,AdhkarReminderReceiver.class).setAction("com.muddakir.quran.ADHKAR."+t).putExtra("type",t);return PendingIntent.getBroadcast(c,100+id(t),i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    public static void showReminderNow(Context c,String t){ if(!c.getSharedPreferences("native_settings",Context.MODE_PRIVATE).getBoolean("notifications",true))return;
        if(!isEnabled(c,t))return;
        if(Build.VERSION.SDK_INT>=33&&c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;
        channel(c);
        Intent open=new Intent(c,MainActivity.class).putExtra(MainActivity.EXTRA_OPEN_ADHKAR_TYPE,t).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi=PendingIntent.getActivity(c,4100+id(t),open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(c,CHANNEL_ID):new Notification.Builder(c);
        b.setSmallIcon(android.R.drawable.ic_popup_reminder).setContentTitle(title(t)).setContentText(text(t)).setStyle(new Notification.BigTextStyle().bigText(text(t))).setAutoCancel(true).setContentIntent(pi).setPriority(Notification.PRIORITY_DEFAULT);
        NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);if(nm!=null)nm.notify(4100+id(t),b.build());
    }
    private static void channel(Context c){if(Build.VERSION.SDK_INT<26)return;NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);if(nm!=null)nm.createNotificationChannel(new NotificationChannel(CHANNEL_ID,"تذكيرات الأذكار",NotificationManager.IMPORTANCE_DEFAULT));}
    private static String title(String t){return TYPE_MORNING.equals(t)?"أذكار الصباح":TYPE_EVENING.equals(t)?"أذكار المساء":"ذكر قبل النوم";}
    private static String text(String t){
        if(TYPE_SLEEP.equals(t))return "حان وقت ذكر الله قبل النوم. افتح التطبيق لقراءة الأذكار الموثقة.";
        return "تذكير: لا تنس ذكر الله في "+(TYPE_MORNING.equals(t)?"الصباح":"المساء")+". المصدر المرجعي: الكافي، كتاب الدعاء، باب القول عند الصباح والمساء.";
    }
    private static boolean valid(String t){return TYPE_MORNING.equals(t)||TYPE_EVENING.equals(t)||TYPE_SLEEP.equals(t);}
    private static int id(String t){return TYPE_MORNING.equals(t)?1:TYPE_EVENING.equals(t)?2:3;}
    private static int defaultHour(String t){return TYPE_MORNING.equals(t)?7:TYPE_EVENING.equals(t)?18:22;}
}
