package com.muddakir.quran;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.RemoteViews;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/** Offline widget: each verse lives in its own small asset: assets/quran/1.json ... 6236.json. */
public final class AyahWorker {
    static final String PREFS_NAME="ayah_widget_prefs", KEY_LAST_AYAH="last_ayah_text", KEY_LAST_TITLE="last_title_text", KEY_LAST_SURAH_NO="last_surah_no", KEY_LAST_AYAH_NO="last_ayah_no_in_surah";
    static final int TOTAL_AYAHS=6236; private static final SecureRandom RNG=new SecureRandom();
    public static void fetchAndUpdateWidgets(Context c,int[] ids){if(ids==null||ids.length==0)return;SharedPreferences p=c.getSharedPreferences(PREFS_NAME,0);try{
        int global=RNG.nextInt(TOTAL_AYAHS)+1;JSONObject o=readLocal(c,global);String text=o.getString("text");String surah=o.getString("surah_name");int sn=o.getInt("surah_number"), an=o.getInt("ayah_number");String title="📖 سورة "+surah+" - آية "+an;
        p.edit().putString(KEY_LAST_AYAH,text).putString(KEY_LAST_TITLE,title).putInt(KEY_LAST_SURAH_NO,sn).putInt(KEY_LAST_AYAH_NO,an).apply();update(c,ids,title,text,sn,an);
    }catch(Exception e){String text=p.getString(KEY_LAST_AYAH,"تعذر قراءة الآية المحلية حاليًا، سيُعاد المحاولة لاحقًا.");update(c,ids,p.getString(KEY_LAST_TITLE,"📖 آية اليوم"),text,p.getInt(KEY_LAST_SURAH_NO,1),p.getInt(KEY_LAST_AYAH_NO,1));}}
    private static JSONObject readLocal(Context c,int n)throws Exception{
        JSONObject index;
        try(InputStream in=c.getAssets().open("quran/ayah-index.json");BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){StringBuilder b=new StringBuilder();String s;while((s=r.readLine())!=null)b.append(s);index=new JSONObject(b.toString());}
        JSONObject ref=index.getJSONObject("items").getJSONObject(String.valueOf(n));
        int page=ref.getInt("page"), offset=ref.getInt("offset");
        try(InputStream in=c.getAssets().open("quran/page-"+page+".json");BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){StringBuilder b=new StringBuilder();String s;while((s=r.readLine())!=null)b.append(s);return new JSONObject(b.toString()).getJSONArray("ayahs").getJSONObject(offset);}
    }
    private static void update(Context c,int[] ids,String title,String text,int sn,int an){AppWidgetManager m=AppWidgetManager.getInstance(c);for(int id:ids){RemoteViews v=new RemoteViews(c.getPackageName(),R.layout.widget_ayah_layout);v.setTextViewText(R.id.txt_title,title);v.setTextViewText(R.id.txt_ayah,text);Intent open=new Intent(c,MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);PendingIntent pi=PendingIntent.getActivity(c,id,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);v.setOnClickPendingIntent(R.id.txt_title,pi);v.setOnClickPendingIntent(R.id.txt_ayah,pi);Intent t=new Intent(c,MainActivity.class).putExtra(MainActivity.EXTRA_OPEN_TAFSIR,true).putExtra(MainActivity.EXTRA_SURAH,sn).putExtra(MainActivity.EXTRA_AYAH,an).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);v.setOnClickPendingIntent(R.id.btn_tafsir_mizan,PendingIntent.getActivity(c,10000+id,t,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));m.updateAppWidget(id,v);}}
}
