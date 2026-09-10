package com.muddakir.quran;
import android.appwidget.*;import android.content.*;import android.widget.RemoteViews;
public class BookmarkWidget extends AppWidgetProvider{
 public void onUpdate(Context c,AppWidgetManager m,int[] ids){update(c,m,ids);} public static void refreshAll(Context c){AppWidgetManager m=AppWidgetManager.getInstance(c);update(c,m,m.getAppWidgetIds(new ComponentName(c,BookmarkWidget.class)));}
 private static void update(Context c,AppWidgetManager m,int[] ids){SharedPreferences p=c.getSharedPreferences(MainActivity.BOOKMARK_PREFS,0);String title=p.getString(MainActivity.KEY_BOOKMARK_TITLE,"📖 سورة الفاتحة");int a=p.getInt(MainActivity.KEY_BOOKMARK_AYAH,1);for(int id:ids){RemoteViews v=new RemoteViews(c.getPackageName(),R.layout.widget_bookmark_layout);v.setTextViewText(R.id.txt_bookmark_info,title+" - الآية "+a);m.updateAppWidget(id,v);}}
}
