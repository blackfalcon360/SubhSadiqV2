package com.blackfalcon.subhsadiq;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.widget.RemoteViews;

import java.util.Calendar;

/** Home-screen widget: next Subh Sadiq, the Subh Kazib window and sunrise. */
public class DawnWidget extends AppWidgetProvider {

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        for (int id : ids) render(c, m, id);
    }

    @Override
    public void onReceive(Context c, Intent i) {
        super.onReceive(c, i);
        String a = i.getAction();
        if (Intent.ACTION_DATE_CHANGED.equals(a) || Intent.ACTION_TIMEZONE_CHANGED.equals(a)) updateAll(c);
    }

    public static void updateAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        int[] ids = m.getAppWidgetIds(new ComponentName(c, DawnWidget.class));
        for (int id : ids) render(c, m, id);
    }

    private static long[] day(SharedPreferences sp, Calendar cal) {
        int idx = sp.getInt("method", 0);
        double angle = idx == Dawn.CUSTOM ? sp.getFloat("custom", 18f) : Dawn.METHOD_ANGLES[idx];
        double lat = Double.longBitsToDouble(sp.getLong("lat", 0));
        double lon = Double.longBitsToDouble(sp.getLong("lon", 0));
        return Dawn.day(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH), lat, lon, angle,
                sp.getFloat("kstart", (float) Dawn.DEFAULT_KAZIB_START), sp.getFloat("kend", (float) Dawn.DEFAULT_KAZIB_END));
    }

    static void render(Context c, AppWidgetManager m, int id) {
        SharedPreferences sp = c.getSharedPreferences("dawn", Context.MODE_PRIVATE);
        int lang = sp.getInt("lang", 0);
        RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget_dawn);
        rv.setTextViewText(R.id.w_title, Lang.t(lang, "Subh Sadiq & Subh Kazib",
                "\u0635\u0628\u062D \u0635\u0627\u062F\u0642 \u0627\u0648\u0631 \u0635\u0628\u062D \u06A9\u0627\u0630\u0628",
                "\u0627\u0644\u0641\u062C\u0631 \u0627\u0644\u0635\u0627\u062F\u0642 \u0648\u0627\u0644\u0641\u062C\u0631 \u0627\u0644\u0643\u0627\u0630\u0628"));

        if (!sp.contains("lat") || !sp.contains("lon")) {
            rv.setTextViewText(R.id.w_label, "");
            rv.setTextViewText(R.id.w_sadiq, "\u2014");
            rv.setTextViewText(R.id.w_kazib, Lang.t(lang, "Open the app once to set your location",
                    "\u0627\u067E\u0646\u0627 \u0645\u0642\u0627\u0645 \u0645\u062D\u0641\u0648\u0638 \u06A9\u0631\u0646\u06D2 \u06A9\u06D2 \u0644\u06CC\u06D2 \u0627\u06CC\u067E \u0627\u06CC\u06A9 \u0628\u0627\u0631 \u06A9\u06BE\u0648\u0644\u06CC\u06BA",
                    "\u0627\u0641\u062A\u062D \u0627\u0644\u062A\u0637\u0628\u064A\u0642 \u0645\u0631\u0629 \u0648\u0627\u062D\u062F\u0629 \u0644\u062A\u062D\u062F\u064A\u062F \u0645\u0648\u0642\u0639\u0643"));
            rv.setTextViewText(R.id.w_sunrise, "");
        } else {
            Calendar today = Calendar.getInstance();
            long now = System.currentTimeMillis();
            long[] ev = day(sp, today);
            boolean tomorrow = false;
            // stay on today until sunrise has passed, then look ahead to tomorrow
            if (ev[3] != Dawn.NONE && now >= ev[3]) {
                Calendar tm = Calendar.getInstance();
                tm.add(Calendar.DAY_OF_YEAR, 1);
                ev = day(sp, tm);
                tomorrow = true;
            }
            if (ev[2] == Dawn.NONE) {
                rv.setTextViewText(R.id.w_label, "");
                rv.setTextViewText(R.id.w_sadiq, "\u2014");
                rv.setTextViewText(R.id.w_kazib, Lang.t(lang, "No true dawn at this angle",
                        "\u0627\u0633 \u0632\u0627\u0648\u06CC\u06D2 \u067E\u0631 \u0635\u0628\u062D \u0635\u0627\u062F\u0642 \u0646\u06C1\u06CC\u06BA \u0628\u0646\u062A\u06CC",
                        "\u0644\u0627 \u064A\u0648\u062C\u062F \u0641\u062C\u0631 \u0635\u0627\u062F\u0642 \u0628\u0647\u0630\u0647 \u0627\u0644\u0632\u0627\u0648\u064A\u0629"));
                rv.setTextViewText(R.id.w_sunrise, "");
            } else {
                String day = tomorrow ? Lang.t(lang, "tomorrow", "\u06A9\u0644", "\u063A\u062F\u0627\u064B") : Lang.t(lang, "today", "\u0622\u062C", "\u0627\u0644\u064A\u0648\u0645");
                rv.setTextViewText(R.id.w_label, Lang.t(lang, "Subh Sadiq (Fajr) \u2022 ", "\u0635\u0628\u062D \u0635\u0627\u062F\u0642 (\u0641\u062C\u0631) \u2022 ", "\u0627\u0644\u0641\u062C\u0631 \u0627\u0644\u0635\u0627\u062F\u0642 \u2022 ") + day);
                rv.setTextViewText(R.id.w_sadiq, Lang.time(c, lang, ev[2]));
                if (ev[0] != Dawn.NONE && ev[1] != Dawn.NONE) {
                    rv.setTextViewText(R.id.w_kazib, Lang.t(lang, "Subh Kazib: ", "\u0635\u0628\u062D \u06A9\u0627\u0630\u0628: ", "\u0627\u0644\u0641\u062C\u0631 \u0627\u0644\u0643\u0627\u0630\u0628: ")
                            + Lang.time(c, lang, ev[0]) + " \u2013 " + Lang.time(c, lang, ev[1]));
                } else {
                    rv.setTextViewText(R.id.w_kazib, "");
                }
                rv.setTextViewText(R.id.w_sunrise, Lang.t(lang, "Sunrise: ", "\u0637\u0644\u0648\u0639 \u0622\u0641\u062A\u0627\u0628: ", "\u0627\u0644\u0634\u0631\u0648\u0642: ") + Lang.time(c, lang, ev[3]));
            }
        }

        int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        PendingIntent pi = PendingIntent.getActivity(c, 0, new Intent(c, SplashActivity.class), flags);
        rv.setOnClickPendingIntent(R.id.w_root, pi);
        m.updateAppWidget(id, rv);
    }
}
