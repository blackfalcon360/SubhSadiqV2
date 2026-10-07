package com.blackfalcon.subhsadiq;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.Calendar;
import java.util.Locale;

public class MainActivity extends Activity implements LocationListener {

    private static final int GREEN = Color.parseColor("#2ECC71");
    private static final int AMBER = Color.parseColor("#FFB300");
    private static final int CARD = Color.parseColor("#12122A");
    private static final int GRAY = Color.parseColor("#A0A0C0");
    private static final String NONE = "\u2014";
    private static final int SEEK_CENTER = 576; // +/- 48 hours in 5 minute steps
    private static final long STEP5 = 5 * 60000L, HOUR = 3600000L, DAY = 86400000L;

    private static final String[] METHOD_UR = {
            "یونیورسٹی آف اسلامک سائنسز، کراچی", "رابطہ عالم اسلامی", "جامعہ ام القریٰ، مکہ مکرمہ",
            "مصری جنرل اتھارٹی آف سروے", "اسنا (شمالی امریکہ)", "انسٹیٹیوٹ آف جیوفزکس، تہران یونیورسٹی",
            "جامعہ القصیم کی میدانی تحقیق (مشاہدہ)", "اپنا مرضی کا زاویہ"};
    private static final String[] METHOD_AR = {
            "جامعة العلوم الإسلامية، كراتشي", "رابطة العالم الإسلامي", "جامعة أم القرى، مكة المكرمة",
            "الهيئة المصرية العامة للمساحة", "إيسنا (أمريكا الشمالية)", "معهد الجيوفيزياء، جامعة طهران",
            "دراسة جامعة القصيم الميدانية (رصد)", "زاوية مخصصة"};

    private int lang = Lang.EN;

    // settings
    private int methodIdx = 0;
    private double customAngle = 18.0, kStartOff = Dawn.DEFAULT_KAZIB_START, kEndOff = Dawn.DEFAULT_KAZIB_END;
    private long offsetMs = 0; // time cursor, relative to the real time

    // location
    private LocationManager lm;
    private SharedPreferences sp;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable timeout;
    private double lat, lon;
    private boolean hasLoc = false, searching = false, wasSearching = false;
    private int locStatus = 0; // 0 none,1 searching,2 live,3 saved,4 gps off,5 no permission,6 no fix

    // views
    private TextView dateTv, locTv, stateTv, countTv, sadiqTime, sadiqSub, kazibTime, kazibSub, sunriseTv,
            methodTv, angleTv, startTv, endTv, warnTv, cursorTv;
    private SeekBar seek;
    private TimelineView timeline;
    private boolean seekFromCode = false;

    private final Runnable tick = new Runnable() {
        @Override public void run() { refresh(); handler.postDelayed(this, 20000); }
    };

    private String t(String en, String ur, String ar) { return Lang.t(lang, en, ur, ar); }

    // ------------------------------------------------------------- lifecycle
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        sp = getSharedPreferences("dawn", MODE_PRIVATE);
        lang = Lang.get(this);
        methodIdx = sp.getInt("method", 0);
        customAngle = sp.getFloat("custom", 18f);
        kStartOff = sp.getFloat("kstart", (float) Dawn.DEFAULT_KAZIB_START);
        kEndOff = sp.getFloat("kend", (float) Dawn.DEFAULT_KAZIB_END);

        buildUi();

        if (sp.contains("lat") && sp.contains("lon")) {
            lat = Double.longBitsToDouble(sp.getLong("lat", 0));
            lon = Double.longBitsToDouble(sp.getLong("lon", 0));
            hasLoc = true;
            locStatus = 3;
        }
        if (hasPerm()) {
            refreshLocation();
        } else {
            locStatus = 5;
            if (Build.VERSION.SDK_INT >= 23) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            }
        }
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(tick);
        if (wasSearching) { wasSearching = false; refreshLocation(); }
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
        wasSearching = searching;
        stopLocation();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (hasPerm()) refreshLocation(); else { locStatus = 5; refresh(); }
    }

    private boolean hasPerm() {
        return Build.VERSION.SDK_INT < 23
                || checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    // -------------------------------------------------------------------- UI
    private void setLang(int l) {
        lang = l;
        Lang.set(this, l);
        buildUi();
        refresh();
        DawnWidget.updateAll(this);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#050510"));
        root.setLayoutDirection(Lang.rtl(lang) ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(14), dp(14), dp(10));

        // ---- title row with the language menu button ----
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView title = tv(21, Color.WHITE, true);
        title.setText("\uD83C\uDF05 " + t("Subh Sadiq & Subh Kazib", "صبح صادق اور صبح کاذب", "الفجر الصادق والفجر الكاذب"));
        TextView sub = tv(15, GRAY, false);
        sub.setText(t("صبح صادق • صبح کاذب", "الفجر الصادق • الفجر الكاذب", "Subh Sadiq • Subh Kazib"));
        titles.addView(title);
        titles.addView(sub);
        TextView langBtn = pill("\uD83C\uDF10 " + (lang == Lang.UR ? "اردو" : lang == Lang.AR ? "العربية" : "English") + " \u25BE", GREEN);
        langBtn.setTextSize(13);
        langBtn.setPadding(dp(12), dp(8), dp(12), dp(8));
        langBtn.setOnClickListener(v -> {
            PopupMenu pm = new PopupMenu(this, v);
            pm.getMenu().add(0, Lang.EN, 0, (lang == Lang.EN ? "\u2713 " : "") + "English");
            pm.getMenu().add(0, Lang.UR, 1, (lang == Lang.UR ? "\u2713 " : "") + "اردو");
            pm.getMenu().add(0, Lang.AR, 2, (lang == Lang.AR ? "\u2713 " : "") + "العربية");
            pm.setOnMenuItemClickListener(it -> { setLang(it.getItemId()); return true; });
            pm.show();
        });
        head.addView(titles, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(langBtn);
        box.addView(head);

        // ---- date row ----
        LinearLayout dateRow = new LinearLayout(this);
        dateRow.setGravity(Gravity.CENTER_VERTICAL);
        dateRow.setPadding(0, dp(10), 0, dp(4));
        boolean rtl = Lang.rtl(lang);
        TextView prev = smallBtn(rtl ? "\u25B6" : "\u25C0");
        TextView next = smallBtn(rtl ? "\u25C0" : "\u25B6");
        prev.setOnClickListener(v -> { offsetMs -= DAY; refresh(); });
        next.setOnClickListener(v -> { offsetMs += DAY; refresh(); });
        dateTv = tv(16, Color.WHITE, true);
        dateTv.setGravity(Gravity.CENTER);
        dateTv.setOnClickListener(v -> { offsetMs = 0; refresh(); });
        dateRow.addView(prev);
        dateRow.addView(dateTv, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        dateRow.addView(next);
        box.addView(dateRow);

        // ---- location line + refresh ----
        LinearLayout locRow = new LinearLayout(this);
        locRow.setGravity(Gravity.CENTER_VERTICAL);
        locTv = tv(12, GRAY, false);
        TextView refresh = pill(t("\u21BB Refresh location", "\u21BB مقام ریفریش کریں", "\u21BB تحديث الموقع"), GREEN);
        refresh.setTextSize(13);
        refresh.setPadding(dp(12), dp(8), dp(12), dp(8));
        refresh.setOnClickListener(v -> refreshLocation());
        locRow.addView(locTv, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        locRow.addView(refresh);
        box.addView(locRow);

        // ---- time slider card ----
        LinearLayout timeCard = card(Color.parseColor("#3A3A60"));
        TextView tct = tv(12, GRAY, false);
        tct.setText(t("Time slider — see the sky at another time", "وقت کی سلائیڈر — کسی اور وقت کا آسمان دیکھیں", "مؤشر الوقت — شاهد السماء في وقت آخر"));
        cursorTv = tv(16, Color.WHITE, true);
        cursorTv.setPadding(0, dp(2), 0, 0);
        seek = new SeekBar(this);
        seek.setMax(SEEK_CENTER * 2);
        seek.setProgress(SEEK_CENTER);
        seek.setProgressTintList(ColorStateList.valueOf(GREEN));
        seek.setThumbTintList(ColorStateList.valueOf(GREEN));
        seek.setLayoutDirection(View.LAYOUT_DIRECTION_LTR); // time always runs left to right
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (!fromUser || seekFromCode) return;
                offsetMs = (progress - SEEK_CENTER) * STEP5;
                refresh();
            }
            @Override public void onStartTrackingTouch(SeekBar sb) { }
            @Override public void onStopTrackingTouch(SeekBar sb) { }
        });
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setLayoutDirection(View.LAYOUT_DIRECTION_LTR); // keep  -1h  -5m  Now  +5m  +1h in the same order
        String[] labels = {
                t("\u22121 h", "\u22121 گھنٹہ", "\u22121 س"),
                t("\u22125 min", "\u22125 منٹ", "\u22125 د"),
                t("Now", "ابھی", "الآن"),
                t("+5 min", "+5 منٹ", "+5 د"),
                t("+1 h", "+1 گھنٹہ", "+1 س")};
        final long[] deltas = {-HOUR, -STEP5, 0, STEP5, HOUR};
        for (int i = 0; i < 5; i++) {
            final int idx = i;
            TextView b = pill(labels[i], i == 2 ? AMBER : GREEN);
            b.setTextSize(12);
            b.setPadding(dp(2), dp(10), dp(2), dp(10));
            b.setOnClickListener(v -> {
                if (idx == 2) offsetMs = 0; else offsetMs += deltas[idx];
                refresh();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(dp(2), dp(6), dp(2), 0);
            btnRow.addView(b, lp);
        }
        timeCard.addView(tct);
        timeCard.addView(cursorTv);
        timeCard.addView(seek);
        timeCard.addView(btnRow);
        box.addView(timeCard, cardLp(10));

        // ---- state card ----
        LinearLayout stateCard = card(Color.parseColor("#3A3A60"));
        stateTv = tv(17, Color.WHITE, true);
        countTv = tv(14, GRAY, false);
        countTv.setPadding(0, dp(4), 0, 0);
        stateCard.addView(stateTv);
        stateCard.addView(countTv);
        box.addView(stateCard, cardLp(10));

        // ---- Subh Sadiq card ----
        LinearLayout sadiqCard = card(GREEN);
        TextView st = tv(18, GREEN, true);
        st.setText(t("Subh Sadiq • True dawn", "صبح صادق • سچی صبح", "الفجر الصادق • الفجر الثاني"));
        TextView su = tv(16, GRAY, false);
        su.setText(t("صبح صادق  •  الفجر الصادق", "الفجر الصادق", "Subh Sadiq"));
        sadiqTime = tv(34, Color.WHITE, true);
        sadiqSub = tv(13, GRAY, false);
        sunriseTv = tv(13, GRAY, false);
        TextView sd = tv(14, Color.WHITE, false);
        sd.setText(t("A thin white light that spreads sideways along the horizon and keeps getting brighter. "
                        + "Sehri ends and Fajr time begins; it lasts until sunrise.",
                "افق کے ساتھ ساتھ چوڑائی میں پھیلنے والی باریک سفید روشنی جو بڑھتی چلی جاتی ہے۔ "
                        + "اسی وقت سحری ختم اور فجر کا وقت شروع ہوتا ہے، اور یہ طلوعِ آفتاب تک رہتا ہے۔",
                "ضوء أبيض رفيع ينتشر معترضًا في الأفق ويزداد سطوعًا. بطلوعه ينتهي السحور ويدخل وقت الفجر، ويستمر إلى طلوع الشمس."));
        sd.setPadding(0, dp(8), 0, dp(8));
        sadiqCard.addView(st);
        sadiqCard.addView(su);
        sadiqCard.addView(sadiqTime);
        sadiqCard.addView(sadiqSub);
        sadiqCard.addView(sunriseTv);
        sadiqCard.addView(sd);
        sadiqCard.addView(new DawnArt(this, true), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(120)));
        box.addView(sadiqCard, cardLp(12));

        // ---- Subh Kazib card ----
        LinearLayout kazibCard = card(AMBER);
        TextView kt = tv(18, AMBER, true);
        kt.setText(t("Subh Kazib • False dawn", "صبح کاذب • جھوٹی صبح", "الفجر الكاذب • الفجر الأول"));
        TextView ku = tv(16, GRAY, false);
        ku.setText(t("صبح کاذب  •  الفجر الكاذب", "الفجر الكاذب", "Subh Kazib"));
        kazibTime = tv(30, Color.WHITE, true);
        kazibSub = tv(13, GRAY, false);
        TextView kd = tv(14, Color.WHITE, false);
        kd.setText(t("A faint column of light rising straight up from the eastern horizon (the zodiacal light, "
                        + "\"wolf's tail\"). Then darkness returns before the true dawn. It does not end Sehri, and Fajr is not yet due.",
                "مشرقی افق سے سیدھی اوپر اٹھتی ہوئی ہلکی روشنی کا ستون (ذنب السرحان، یعنی بھیڑیے کی دم جیسی روشنی)۔ "
                        + "اس کے بعد دوبارہ اندھیرا چھا جاتا ہے اور پھر صبح صادق ہوتی ہے۔ اس سے سحری ختم نہیں ہوتی اور فجر کا وقت بھی شروع نہیں ہوتا۔",
                "عمود من الضوء الخافت يرتفع مستطيلًا نحو السماء من الأفق الشرقي (ذنب السرحان). ثم يعود الظلام قبل الفجر الصادق. "
                        + "ولا ينتهي به السحور ولا يدخل به وقت الفجر."));
        kd.setPadding(0, dp(8), 0, dp(8));
        kazibCard.addView(kt);
        kazibCard.addView(ku);
        kazibCard.addView(kazibTime);
        kazibCard.addView(kazibSub);
        kazibCard.addView(kd);
        kazibCard.addView(new DawnArt(this, false), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(120)));
        box.addView(kazibCard, cardLp(12));

        // ---- timeline ----
        LinearLayout tlCard = card(Color.parseColor("#3A3A60"));
        TextView tlt = tv(15, Color.WHITE, true);
        tlt.setText(t("Timeline", "ٹائم لائن", "الجدول الزمني"));
        timeline = new TimelineView(this);
        timeline.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        timeline.setLegend(new String[]{t("Night", "رات", "الليل"), t("Subh Kazib", "صبح کاذب", "الفجر الكاذب"),
                t("Dark gap", "اندھیرا وقفہ", "فترة الظلام"), t("Subh Sadiq", "صبح صادق", "الفجر الصادق")});
        tlCard.addView(tlt);
        tlCard.addView(timeline, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(150)));
        box.addView(tlCard, cardLp(12));

        warnTv = tv(13, AMBER, false);
        warnTv.setPadding(dp(4), dp(8), dp(4), 0);
        box.addView(warnTv);

        // ---- settings ----
        LinearLayout setCard = card(Color.parseColor("#3A3A60"));
        TextView sh = tv(15, Color.WHITE, true);
        sh.setText(t("Settings", "ترتیبات", "الإعدادات"));
        TextView ml = tv(12, GRAY, false);
        ml.setText(t("Subh Sadiq (Fajr) angle — choose the authority you follow",
                "صبح صادق (فجر) کا زاویہ — اپنی پیروی والا ادارہ منتخب کریں",
                "زاوية الفجر الصادق — اختر الجهة التي تتبعها"));
        ml.setPadding(0, dp(6), 0, dp(4));
        methodTv = pill("", GREEN);
        methodTv.setTextSize(14);
        methodTv.setOnClickListener(v -> chooseMethod());
        setCard.addView(sh);
        setCard.addView(ml);
        setCard.addView(methodTv);

        angleTv = tv(16, Color.WHITE, true);
        setCard.addView(stepper(t("Fajr angle (below horizon)", "فجر کا زاویہ (افق سے نیچے)", "زاوية الفجر (تحت الأفق)"), angleTv,
                () -> { switchToCustom(); customAngle = Math.max(10, customAngle - 0.5); saveAndRefresh(); },
                () -> { switchToCustom(); customAngle = Math.min(22, customAngle + 0.5); saveAndRefresh(); }));
        startTv = tv(16, Color.WHITE, true);
        setCard.addView(stepper(t("Subh Kazib appears: Fajr angle +", "صبح کاذب ظاہر ہوتی ہے: فجر کا زاویہ +", "ظهور الفجر الكاذب: زاوية الفجر +"), startTv,
                () -> { kStartOff = Math.max(kEndOff + 0.5, kStartOff - 0.5); saveAndRefresh(); },
                () -> { kStartOff = Math.min(14, kStartOff + 0.5); saveAndRefresh(); }));
        endTv = tv(16, Color.WHITE, true);
        setCard.addView(stepper(t("Subh Kazib fades: Fajr angle +", "صبح کاذب ختم ہوتی ہے: فجر کا زاویہ +", "زوال الفجر الكاذب: زاوية الفجر +"), endTv,
                () -> { kEndOff = Math.max(0.5, kEndOff - 0.5); saveAndRefresh(); },
                () -> { kEndOff = Math.min(kStartOff - 0.5, kEndOff + 0.5); saveAndRefresh(); }));
        box.addView(setCard, cardLp(12));

        // ---- widget hint + sources ----
        TextView wh = tv(12, GRAY, false);
        wh.setText(t("Widget: long-press your home screen, choose Widgets, then \"Subh Sadiq & Kazib\".",
                "ویجٹ: ہوم اسکرین کو دیر تک دبائیں، ویجٹس منتخب کریں، پھر \"Subh Sadiq & Kazib\" چنیں۔",
                "الأداة: اضغط مطولًا على الشاشة الرئيسية، اختر الأدوات، ثم \"Subh Sadiq & Kazib\"."));
        wh.setPadding(dp(4), dp(14), dp(4), 0);
        box.addView(wh);

        TextView src = tv(12, GRAY, false);
        src.setText(t(
                "Sources: Qur'an 2:187. Sahih Muslim 1094 (Samura b. Jundub). Ibn Qudama, al-Mughni 1/232 "
                        + "(also quoting Ibn 'Abbas on the two dawns). Ibn 'Uthaymeen, al-Sharh al-Mumti' 2/107-108: "
                        + "the rulings are tied to the second (true) dawn. Sun position: praytimes.org method (H. Zarrabi-Zadeh); "
                        + "field observation: Qassim University study (2014) found about 16\u00B0.\n\n"
                        + "Subh Sadiq times use the angle you choose. The Subh Kazib window is only an estimate: "
                        + "it is something you see in a dark sky, and no single angle is agreed on, so adjust it after your own observation. "
                        + "Times are cut down to the whole minute. Fasting and prayer depend on actually seeing the true dawn, "
                        + "so use this as a guide and follow your local scholars and mosque timetable.",
                "مآخذ: القرآن 2:187۔ صحیح مسلم 1094 (سمرہ بن جندب رضی اللہ عنہ)۔ ابن قدامہ، المغنی 1/232 "
                        + "(جس میں دونوں فجروں کے بارے میں ابن عباس رضی اللہ عنہما کا اثر بھی منقول ہے)۔ ابن عثیمین، الشرح الممتع 2/107-108: "
                        + "احکام دوسری (صادق) فجر سے متعلق ہیں۔ سورج کی پوزیشن: praytimes.org کا طریقہ (حامد زرابی زادہ)؛ "
                        + "میدانی مشاہدہ: جامعہ القصیم کی تحقیق (2014) کے مطابق تقریباً 16\u00B0۔\n\n"
                        + "صبح صادق کے اوقات آپ کے منتخب کردہ زاویے پر مبنی ہیں۔ صبح کاذب کا وقت محض اندازہ ہے: "
                        + "یہ تاریک آسمان میں آنکھ سے دیکھی جانے والی چیز ہے اور کسی ایک زاویے پر اتفاق نہیں، اس لیے اپنے مشاہدے کے بعد اسے ایڈجسٹ کریں۔ "
                        + "اوقات پورے منٹ تک کاٹ کر دکھائے جاتے ہیں۔ روزہ اور نماز کا دارومدار صبح صادق کو واقعی دیکھنے پر ہے، "
                        + "لہٰذا اسے رہنمائی کے طور پر لیں اور اپنے مقامی علماء اور مسجد کے نقشے کی پیروی کریں۔",
                "المصادر: القرآن الكريم 2:187. صحيح مسلم 1094 (حديث سمرة بن جندب). ابن قدامة، المغني 1/232 "
                        + "(وفيه أثر ابن عباس في الفجرين). ابن عثيمين، الشرح الممتع 2/107-108: "
                        + "الأحكام معلّقة بالفجر الثاني (الصادق). موضع الشمس: طريقة praytimes.org (حامد زرابي زاده)؛ "
                        + "والرصد الميداني: دراسة جامعة القصيم (2014) وجدت نحو 16\u00B0.\n\n"
                        + "أوقات الفجر الصادق تعتمد على الزاوية التي تختارها. أما نافذة الفجر الكاذب فهي تقدير فقط: "
                        + "فهو شيء يُرى في سماء مظلمة ولا يوجد اتفاق على زاوية واحدة، فعدّلها بعد رصدك الشخصي. "
                        + "تُقتطع الأوقات إلى الدقيقة الكاملة. الصيام والصلاة منوطان برؤية الفجر الصادق فعلًا، "
                        + "فاستعمل هذا التطبيق للاسترشاد واتبع علماء بلدك وتقويم مسجدك."));
        src.setPadding(dp(4), dp(10), dp(4), dp(8));
        box.addView(src);

        ScrollView sv = new ScrollView(this);
        sv.addView(box);

        TextView credit = tv(14, Color.WHITE, true);
        credit.setText("By: Black Falcon \uD83E\uDD85");
        credit.setGravity(Gravity.RIGHT);
        credit.setPadding(dp(16), dp(6), dp(16), dp(10));
        credit.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);

        root.addView(sv, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(credit);
        setContentView(root);
    }

    private TextView tv(int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private GradientDrawable round(int fill, int stroke, int r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setStroke(dp(2), stroke);
        g.setCornerRadius(dp(r));
        return g;
    }

    private LinearLayout card(int stroke) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(14), dp(12), dp(14), dp(12));
        c.setBackground(round(CARD, stroke, 18));
        return c;
    }

    private LinearLayout.LayoutParams cardLp(int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(topDp), 0, 0);
        return lp;
    }

    private TextView pill(String s, int color) {
        TextView b = tv(15, color, true);
        b.setText(s);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(12), dp(11), dp(12), dp(11));
        b.setBackground(round(Color.parseColor("#0D1F14"), color, 24));
        b.setClickable(true);
        return b;
    }

    private TextView smallBtn(String s) {
        TextView b = tv(18, GREEN, true);
        b.setText(s);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(16), dp(8), dp(16), dp(8));
        b.setClickable(true);
        return b;
    }

    private View stepper(String label, TextView value, final Runnable minus, final Runnable plus) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(10), 0, 0);
        TextView l = tv(12, GRAY, false);
        l.setText(label);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        TextView m = smallBtn("\u2212");
        TextView p = smallBtn("+");
        m.setOnClickListener(v -> minus.run());
        p.setOnClickListener(v -> plus.run());
        value.setGravity(Gravity.CENTER);
        row.addView(m);
        row.addView(value, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(p);
        box.addView(l);
        box.addView(row);
        return box;
    }

    // ------------------------------------------------------------- settings
    private double fajrAngle() {
        return methodIdx == Dawn.CUSTOM ? customAngle : Dawn.METHOD_ANGLES[methodIdx];
    }

    private void switchToCustom() {
        if (methodIdx != Dawn.CUSTOM) { customAngle = Dawn.METHOD_ANGLES[methodIdx]; methodIdx = Dawn.CUSTOM; }
    }

    private String methodName(int i) {
        return lang == Lang.UR ? METHOD_UR[i] : (lang == Lang.AR ? METHOD_AR[i] : Dawn.METHOD_NAMES[i]);
    }

    private void chooseMethod() {
        String[] items = new String[Dawn.METHOD_NAMES.length];
        for (int i = 0; i < items.length; i++) {
            items[i] = i == Dawn.CUSTOM ? methodName(i) : String.format(Locale.US, "%s (%.1f\u00B0)", methodName(i), Dawn.METHOD_ANGLES[i]);
        }
        AlertDialog d = new AlertDialog.Builder(this)
                .setTitle(t("Subh Sadiq (Fajr) method", "صبح صادق (فجر) کا طریقہ", "طريقة حساب الفجر الصادق"))
                .setItems(items, (dlg, which) -> {
                    if (which == Dawn.CUSTOM && methodIdx != Dawn.CUSTOM) customAngle = fajrAngle();
                    methodIdx = which;
                    saveAndRefresh();
                })
                .create();
        d.show();
        d.getListView().setLayoutDirection(Lang.rtl(lang) ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
    }

    private void saveAndRefresh() {
        sp.edit().putInt("method", methodIdx).putFloat("custom", (float) customAngle)
                .putFloat("kstart", (float) kStartOff).putFloat("kend", (float) kEndOff).apply();
        refresh();
        DawnWidget.updateAll(this);
    }

    // -------------------------------------------------------------- display
    private String fmt(long ms) { return ms == Dawn.NONE ? NONE : Lang.time(this, lang, ms); }

    private long[] eventsFor(Calendar c) {
        return Dawn.day(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH),
                lat, lon, fajrAngle(), kStartOff, kEndOff);
    }

    private String unitText(long minutes) {
        long d = minutes / 1440L, h = (minutes % 1440L) / 60L, m = minutes % 60L;
        String ud = t(" d ", " دن ", " يوم "), uh = t(" h", " گھنٹہ", " س"), um = t(" min", " منٹ", " د");
        return (d > 0 ? d + ud : "") + h + uh + (m > 0 ? " " + m + um : "");
    }

    private String until(long ms, long cursor) {
        long diff = ms - cursor;
        if (diff < 60000L) return t("less than a minute", "ایک منٹ سے کم", "أقل من دقيقة");
        long h = diff / 3600000L, m = (diff % 3600000L) / 60000L;
        return h > 0 ? h + t(" h ", " گھنٹہ ", " ساعة ") + m + t(" min", " منٹ", " دقيقة") : m + t(" min", " منٹ", " دقيقة");
    }

    private void refresh() {
        // location status line
        switch (locStatus) {
            case 1: locTv.setText(t("Finding your location…", "آپ کا مقام تلاش ہو رہا ہے…", "جارٍ تحديد موقعك…")); break;
            case 2: locTv.setText(String.format(Locale.US, t("GPS %.3f°, %.3f° • works offline", "جی پی ایس %.3f°، %.3f° • آف لائن بھی چلتا ہے", "GPS %.3f°، %.3f° • يعمل دون إنترنت"), lat, lon)); break;
            case 3: locTv.setText(String.format(Locale.US, t("Saved %.3f°, %.3f° • tap Refresh", "محفوظ شدہ %.3f°، %.3f° • ریفریش دبائیں", "محفوظ %.3f°، %.3f° • اضغط تحديث"), lat, lon)); break;
            case 4: locTv.setText(t("GPS is off — turn it on and tap Refresh", "جی پی ایس بند ہے — اسے چلائیں اور ریفریش دبائیں", "GPS مغلق — شغّله ثم اضغط تحديث")); break;
            case 5: locTv.setText(t("Location permission is needed", "مقام کی اجازت درکار ہے", "إذن الموقع مطلوب")); break;
            case 6: locTv.setText(hasLoc
                    ? t("No GPS fix — using saved location", "جی پی ایس نہیں مل رہا — محفوظ شدہ مقام استعمال ہو رہا ہے", "تعذر تحديد GPS — يُستخدم الموقع المحفوظ")
                    : t("No GPS fix yet — go outside and tap Refresh", "ابھی جی پی ایس نہیں ملا — کھلی جگہ جا کر ریفریش دبائیں", "لم يتم تحديد GPS بعد — اخرج إلى مكان مكشوف ثم اضغط تحديث")); break;
            default: locTv.setText(t("Tap Refresh to get your location", "اپنا مقام لینے کے لیے ریفریش دبائیں", "اضغط تحديث لتحديد موقعك"));
        }

        // settings text
        methodTv.setText(methodName(methodIdx));
        angleTv.setText(String.format(Locale.US, "%.1f\u00B0", fajrAngle()));
        String below = t("(Sun %.1f° below)", "(سورج %.1f° نیچے)", "(الشمس %.1f° تحت الأفق)");
        startTv.setText(String.format(Locale.US, "%.1f\u00B0  " + below, kStartOff, fajrAngle() + kStartOff));
        endTv.setText(String.format(Locale.US, "%.1f\u00B0  " + below, kEndOff, fajrAngle() + kEndOff));

        // the time cursor: real time + slider offset
        long cursor = System.currentTimeMillis() + offsetMs;
        Calendar shown = Calendar.getInstance();
        shown.setTimeInMillis(cursor);
        Calendar real = Calendar.getInstance();
        boolean isToday = shown.get(Calendar.YEAR) == real.get(Calendar.YEAR) && shown.get(Calendar.DAY_OF_YEAR) == real.get(Calendar.DAY_OF_YEAR);
        dateTv.setText(Lang.date(lang, shown) + (isToday ? "  " + t("(today)", "(آج)", "(اليوم)") : ""));

        String rel = offsetMs == 0 ? t("Now", "ابھی", "الآن")
                : (offsetMs > 0 ? "+" : "\u2212") + unitText(Math.abs(offsetMs) / 60000L);
        cursorTv.setText("\uD83D\uDD52 " + Lang.time(this, lang, cursor) + "   (" + rel + ")");
        seekFromCode = true;
        seek.setProgress((int) Math.max(0, Math.min(SEEK_CENTER * 2, offsetMs / STEP5 + SEEK_CENTER)));
        seekFromCode = false;

        if (!hasLoc) {
            stateTv.setText(t("Waiting for your location", "آپ کے مقام کا انتظار ہے", "بانتظار تحديد موقعك"));
            countTv.setText(t("Dawn times need your place on Earth.", "صبح کے اوقات کے لیے آپ کا مقام درکار ہے۔", "تحتاج أوقات الفجر إلى معرفة موقعك."));
            sadiqTime.setText(NONE); sadiqSub.setText(""); sunriseTv.setText("");
            kazibTime.setText(NONE); kazibSub.setText("");
            timeline.set(null, null, -1, "");
            warnTv.setText("");
            return;
        }

        // the day the cursor is on
        long[] ev = eventsFor(shown);
        double fa = fajrAngle();
        if (ev[2] == Dawn.NONE) {
            sadiqTime.setText(NONE);
            sadiqSub.setText("");
            sunriseTv.setText("");
            kazibTime.setText(NONE);
            kazibSub.setText("");
            warnTv.setText(t("At this latitude the Sun does not go deep enough on this date, so there is no true dawn at this angle "
                            + "(twilight lasts all night). Follow your local scholars' ruling for high latitudes.",
                    "اس عرض بلد پر اس تاریخ کو سورج اتنا نیچے نہیں جاتا، اس لیے اس زاویے پر صبح صادق کا حساب نہیں بنتا (شفق ساری رات رہتی ہے)۔ "
                            + "زیادہ عرض بلد کے لیے اپنے علماء کے فتوے کی پیروی کریں۔",
                    "في هذا الخط من العرض لا تنزل الشمس إلى هذا العمق في هذا التاريخ، فلا يمكن حساب الفجر الصادق بهذه الزاوية (الشفق يستمر طوال الليل). "
                            + "ارجعوا إلى فتوى علمائكم في المناطق ذات العروض العالية."));
            timeline.set(null, null, -1, "");
        } else {
            sadiqTime.setText(fmt(ev[2]));
            sadiqSub.setText(String.format(Locale.US, t("Sun about %.1f° below the horizon • Sehri ends, Fajr begins",
                    "سورج افق سے تقریباً %.1f° نیچے • سحری ختم، فجر شروع", "الشمس تحت الأفق بنحو %.1f° • ينتهي السحور ويبدأ الفجر"), fa));
            sunriseTv.setText(t("Fajr time ends at sunrise: ", "فجر کا وقت طلوعِ آفتاب پر ختم: ", "ينتهي وقت الفجر عند الشروق: ") + fmt(ev[3]));
            if (ev[0] == Dawn.NONE || ev[1] == Dawn.NONE) {
                kazibTime.setText(NONE);
                kazibSub.setText(t("Not calculable on this date at your latitude", "اس تاریخ پر آپ کے عرض بلد پر حساب ممکن نہیں", "لا يمكن الحساب في هذا التاريخ عند خط عرضكم"));
                timeline.set(null, null, -1, "");
            } else {
                kazibTime.setText(fmt(ev[0]) + " \u2013 " + fmt(ev[1]));
                kazibSub.setText(String.format(Locale.US, t("Estimate: Sun %.1f° → %.1f° below the horizon",
                        "اندازہ: سورج افق سے %.1f° سے %.1f° تک نیچے", "تقدير: الشمس من %.1f° إلى %.1f° تحت الأفق"), fa + kStartOff, fa + kEndOff));
                timeline.set(ev, new String[]{fmt(ev[0]), fmt(ev[1]), fmt(ev[2]), fmt(ev[3])}, cursor,
                        offsetMs == 0 ? t("now", "اب", "الآن") : t("time", "وقت", "الوقت"));
            }
            warnTv.setText("");
        }

        // what is happening at the cursor time (cursor day + the next day)
        Calendar tomorrow = (Calendar) shown.clone();
        tomorrow.add(Calendar.DAY_OF_YEAR, 1);
        long[] n = eventsFor(tomorrow);
        if (ev[2] == Dawn.NONE) {
            stateTv.setText(t("No true dawn at this angle today", "آج اس زاویے پر صبح صادق نہیں بنتی", "لا يوجد فجر صادق بهذه الزاوية اليوم"));
            stateTv.setTextColor(AMBER);
            countTv.setText("");
            return;
        }
        long nextSadiq = cursor < ev[2] ? ev[2] : n[2];
        if (cursor < ev[0]) {
            stateTv.setText(t("\uD83C\uDF19 Night — the sky is dark", "\uD83C\uDF19 رات — آسمان تاریک ہے", "\uD83C\uDF19 الليل — السماء مظلمة"));
            stateTv.setTextColor(Color.WHITE);
        } else if (ev[0] != Dawn.NONE && ev[1] != Dawn.NONE && cursor < ev[1]) {
            stateTv.setText(t("\uD83C\uDF2B\uFE0F Subh Kazib window — faint vertical light", "\uD83C\uDF2B\uFE0F صبح کاذب کا وقت — سیدھی ہلکی روشنی", "\uD83C\uDF2B\uFE0F وقت الفجر الكاذب — ضوء رأسي خافت"));
            stateTv.setTextColor(AMBER);
        } else if (cursor < ev[2]) {
            stateTv.setText(t("\u23F3 Dark gap — Subh Sadiq has not yet appeared", "\u23F3 اندھیرا وقفہ — صبح صادق ابھی ظاہر نہیں ہوئی", "\u23F3 فترة الظلام — لم يظهر الفجر الصادق بعد"));
            stateTv.setTextColor(Color.WHITE);
        } else if (cursor < ev[3]) {
            stateTv.setText(t("\uD83C\uDF05 Subh Sadiq has appeared — Sehri ended, Fajr time is in", "\uD83C\uDF05 صبح صادق ظاہر ہو گئی — سحری ختم، فجر کا وقت داخل", "\uD83C\uDF05 ظهر الفجر الصادق — انتهى السحور ودخل وقت الفجر"));
            stateTv.setTextColor(GREEN);
        } else {
            stateTv.setText(t("\u2600\uFE0F After sunrise", "\u2600\uFE0F طلوعِ آفتاب کے بعد", "\u2600\uFE0F بعد شروق الشمس"));
            stateTv.setTextColor(Color.WHITE);
        }
        countTv.setText(nextSadiq == Dawn.NONE ? "" : t("Next Subh Sadiq: ", "اگلی صبح صادق: ", "الفجر الصادق القادم: ") + fmt(nextSadiq)
                + t("  (in ", "  (", "  (بعد ") + until(nextSadiq, cursor) + (lang == Lang.UR ? " بعد)" : ")"));
    }

    // ------------------------------------------------------------- location
    private void refreshLocation() {
        if (!hasPerm()) {
            locStatus = 5; refresh();
            if (Build.VERSION.SDK_INT >= 23) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            }
            return;
        }
        stopLocation();
        boolean any = false;
        try {
            for (String pr : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
                if (lm.isProviderEnabled(pr)) { lm.requestLocationUpdates(pr, 0, 0, this); any = true; }
            }
            if (!hasLoc) {
                Location last = null;
                for (String pr : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
                    Location l = lm.getLastKnownLocation(pr);
                    if (l != null && (last == null || l.getTime() > last.getTime())) last = l;
                }
                if (last != null) { lat = last.getLatitude(); lon = last.getLongitude(); hasLoc = true; }
            }
        } catch (SecurityException ignored) { }

        if (!any) { locStatus = 4; refresh(); return; }
        searching = true;
        locStatus = 1;
        refresh();
        timeout = () -> {
            if (!searching) return;
            stopLocation();
            locStatus = 6;
            refresh();
        };
        handler.postDelayed(timeout, 25000);
    }

    private void stopLocation() {
        searching = false;
        if (timeout != null) { handler.removeCallbacks(timeout); timeout = null; }
        try { lm.removeUpdates(this); } catch (SecurityException ignored) { }
    }

    @Override
    public void onLocationChanged(Location l) {
        if (!searching) return;
        stopLocation();
        lat = l.getLatitude();
        lon = l.getLongitude();
        hasLoc = true;
        sp.edit().putLong("lat", Double.doubleToRawLongBits(lat))
                .putLong("lon", Double.doubleToRawLongBits(lon)).apply();
        locStatus = 2;
        refresh();
        DawnWidget.updateAll(this);
    }

    @Override public void onStatusChanged(String p, int s, Bundle e) { }
    @Override public void onProviderEnabled(String p) { }
    @Override public void onProviderDisabled(String p) { }
}
