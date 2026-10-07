package com.blackfalcon.subhsadiq;

import android.content.Context;
import android.text.format.DateFormat;

import java.util.Calendar;
import java.util.Locale;

/** English / Urdu / Arabic helpers shared by the app and the widget. */
public final class Lang {
    private Lang() { }

    public static final int EN = 0, UR = 1, AR = 2;

    public static int get(Context c) { return c.getSharedPreferences("dawn", Context.MODE_PRIVATE).getInt("lang", EN); }
    public static void set(Context c, int l) { c.getSharedPreferences("dawn", Context.MODE_PRIVATE).edit().putInt("lang", l).apply(); }
    public static boolean rtl(int l) { return l != EN; }
    public static String t(int l, String en, String ur, String ar) { return l == UR ? ur : (l == AR ? ar : en); }

    private static final String[] DAYS_EN = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
    private static final String[] DAYS_UR = {"\u0627\u062A\u0648\u0627\u0631", "\u067E\u06CC\u0631", "\u0645\u0646\u06AF\u0644", "\u0628\u062F\u06BE", "\u062C\u0645\u0639\u0631\u0627\u062A", "\u062C\u0645\u0639\u06C1", "\u06C1\u0641\u062A\u06C1"};
    private static final String[] DAYS_AR = {"\u0627\u0644\u0623\u062D\u062F", "\u0627\u0644\u0625\u062B\u0646\u064A\u0646", "\u0627\u0644\u062B\u0644\u0627\u062B\u0627\u0621", "\u0627\u0644\u0623\u0631\u0628\u0639\u0627\u0621", "\u0627\u0644\u062E\u0645\u064A\u0633", "\u0627\u0644\u062C\u0645\u0639\u0629", "\u0627\u0644\u0633\u0628\u062A"};
    private static final String[] MON_EN = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
    private static final String[] MON_UR = {"\u062C\u0646\u0648\u0631\u06CC", "\u0641\u0631\u0648\u0631\u06CC", "\u0645\u0627\u0631\u0686", "\u0627\u067E\u0631\u06CC\u0644", "\u0645\u0626\u06CC", "\u062C\u0648\u0646",
            "\u062C\u0648\u0644\u0627\u0626\u06CC", "\u0627\u06AF\u0633\u062A", "\u0633\u062A\u0645\u0628\u0631", "\u0627\u06A9\u062A\u0648\u0628\u0631", "\u0646\u0648\u0645\u0628\u0631", "\u062F\u0633\u0645\u0628\u0631"};
    private static final String[] MON_AR = {"\u064A\u0646\u0627\u064A\u0631", "\u0641\u0628\u0631\u0627\u064A\u0631", "\u0645\u0627\u0631\u0633", "\u0623\u0628\u0631\u064A\u0644", "\u0645\u0627\u064A\u0648", "\u064A\u0648\u0646\u064A\u0648",
            "\u064A\u0648\u0644\u064A\u0648", "\u0623\u063A\u0633\u0637\u0633", "\u0633\u0628\u062A\u0645\u0628\u0631", "\u0623\u0643\u062A\u0648\u0628\u0631", "\u0646\u0648\u0641\u0645\u0628\u0631", "\u062F\u064A\u0633\u0645\u0628\u0631"};

    /** e.g. "Tue, 7 Oct 2026" in the chosen language (Latin digits). */
    public static String date(int l, Calendar c) {
        String[] d = l == UR ? DAYS_UR : (l == AR ? DAYS_AR : DAYS_EN);
        String[] m = l == UR ? MON_UR : (l == AR ? MON_AR : MON_EN);
        return d[c.get(Calendar.DAY_OF_WEEK) - 1] + (l == EN ? ", " : "\u060C ") + c.get(Calendar.DAY_OF_MONTH) + " "
                + m[c.get(Calendar.MONTH)] + " " + c.get(Calendar.YEAR);
    }

    /** Clock time cut down to the whole minute, 12 or 24 hour as the phone is set. */
    public static String time(Context ctx, int l, long ms) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(ms - (ms % 60000L));
        int h = c.get(Calendar.HOUR_OF_DAY), m = c.get(Calendar.MINUTE);
        if (DateFormat.is24HourFormat(ctx)) return String.format(Locale.US, "%02d:%02d", h, m);
        int h12 = h % 12 == 0 ? 12 : h % 12;
        String suffix = l == AR ? (h < 12 ? "\u0635" : "\u0645") : (h < 12 ? "AM" : "PM");
        return String.format(Locale.US, "%d:%02d %s", h12, m, suffix);
    }
}
