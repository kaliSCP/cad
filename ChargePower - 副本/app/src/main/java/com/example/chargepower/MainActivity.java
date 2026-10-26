package com.example.chargepower;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final int NOTIFICATION_PERMISSION_REQUEST = 10;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView powerText;
    private TextView currentText;
    private TextView voltageText;
    private TextView stateText;
    private View stateDot;
    private FlowingBackground flowingBackground;
    private FluidOrb fluidOrb;
    private boolean waitingForPermission;

    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            refreshValues();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(244, 247, 252));
        getWindow().setNavigationBarColor(Color.rgb(244, 247, 252));
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getWindow().setNavigationBarContrastEnforced(false);
        }
        buildScreen();
    }

    @Override
    protected void onResume() {
        super.onResume();
        flowingBackground.setAnimating(true);
        fluidOrb.setAnimating(true);
        handler.post(refresh);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(refresh);
        flowingBackground.setAnimating(false);
        fluidOrb.setAnimating(false);
        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST && waitingForPermission) {
            waitingForPermission = false;
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startMonitor();
            } else {
                stateText.setText("需要通知权限，才能在后台持续显示功率");
                stateDot.setBackground(circle(Color.rgb(255, 183, 77)));
            }
        }
    }

    private void buildScreen() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(244, 247, 252));
        flowingBackground = new FlowingBackground(this);
        root.addView(flowingBackground, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setClipToPadding(false);
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.setPadding(dp(22), dp(20), dp(22), dp(28));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        scrollView.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView eyebrow = makeText("CHARGE  /  POWER", 11, Color.rgb(104, 123, 151));
        eyebrow.setLetterSpacing(0.16f);
        eyebrow.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        content.addView(eyebrow, matchWrap());

        TextView title = makeText("实时充电功率", 30, Color.rgb(24, 34, 53));
        title.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        title.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        title.setPadding(0, dp(7), 0, dp(21));
        content.addView(title, matchWrap());

        FrameLayout powerCard = new FrameLayout(this);
        powerCard.setBackground(glassDrawable(dp(30), 0.76f, 0.72f));
        powerCard.setElevation(dp(5));
        LinearLayout.LayoutParams powerCardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        content.addView(powerCard, powerCardParams);

        fluidOrb = new FluidOrb(this);
        FrameLayout.LayoutParams orbParams = new FrameLayout.LayoutParams(
                dp(142), dp(142), Gravity.RIGHT | Gravity.TOP);
        orbParams.rightMargin = dp(3);
        orbParams.topMargin = dp(12);
        powerCard.addView(fluidOrb, orbParams);

        LinearLayout powerContent = new LinearLayout(this);
        powerContent.setOrientation(LinearLayout.VERTICAL);
        powerContent.setPadding(dp(22), dp(22), dp(22), dp(20));
        powerCard.addView(powerContent, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView powerLabel = makeText("BATTERY POWER", 11, Color.rgb(166, 199, 216));
        powerLabel.setTextColor(Color.rgb(111, 127, 151));
        powerLabel.setLetterSpacing(0.14f);
        powerLabel.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        powerContent.addView(powerLabel, matchWrap());

        powerText = makeText("-- W", 54, Color.rgb(27, 43, 69));
        powerText.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        powerText.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        powerText.setPadding(0, dp(8), 0, dp(4));
        powerContent.addView(powerText, matchWrap());

        TextView estimateLabel = makeText("电池端实时估算", 14, Color.rgb(113, 130, 154));
        estimateLabel.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        powerContent.addView(estimateLabel, matchWrap());

        LinearLayout statusPill = new LinearLayout(this);
        statusPill.setOrientation(LinearLayout.HORIZONTAL);
        statusPill.setGravity(Gravity.CENTER_VERTICAL);
        statusPill.setPadding(dp(12), dp(9), dp(14), dp(9));
        statusPill.setBackground(glassDrawable(dp(18), 0.52f, 0.65f));
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = dp(22);
        powerContent.addView(statusPill, statusParams);

        stateDot = new View(this);
        stateDot.setBackground(circle(Color.rgb(151, 160, 176)));
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(8), dp(8));
        dotParams.rightMargin = dp(9);
        statusPill.addView(stateDot, dotParams);

        stateText = makeText("监测未启动", 13, Color.rgb(74, 91, 117));
        statusPill.addView(stateText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout metrics = new LinearLayout(this);
        metrics.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams metricsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        metricsParams.topMargin = dp(13);
        content.addView(metrics, metricsParams);
        LinearLayout.LayoutParams currentCardParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        currentCardParams.rightMargin = dp(7);
        metrics.addView(createMetricCard("CURRENT", "电流", true), currentCardParams);
        LinearLayout.LayoutParams voltageCardParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        voltageCardParams.leftMargin = dp(7);
        metrics.addView(createMetricCard("VOLTAGE", "电压", false), voltageCardParams);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        actionsParams.topMargin = dp(18);
        content.addView(actions, actionsParams);

        TextView startButton = makeText("开始后台监测", 15, Color.WHITE);
        startButton.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        startButton.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.rgb(93, 139, 247), Color.rgb(111, 112, 241)}));
        startButton.setElevation(dp(3));
        startButton.setOnClickListener(view -> requestNotificationPermissionAndStart());
        LinearLayout.LayoutParams startParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1.35f);
        startParams.rightMargin = dp(10);
        actions.addView(startButton, startParams);

        TextView stopButton = makeText("停止监测", 14, Color.rgb(65, 81, 109));
        stopButton.setBackground(glassDrawable(dp(18), 0.69f, 0.72f));
        stopButton.setOnClickListener(view -> {
            stopService(new Intent(this, PowerMonitorService.class));
            getSharedPreferences(PowerMonitorService.PREFERENCES, MODE_PRIVATE)
                    .edit().putBoolean(PowerMonitorService.KEY_RUNNING, false).apply();
            refreshValues();
        });
        actions.addView(stopButton, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1));

        LinearLayout noteCard = glassCard(dp(20));
        noteCard.setOrientation(LinearLayout.VERTICAL);
        noteCard.setPadding(dp(17), dp(16), dp(17), dp(16));
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        noteParams.topMargin = dp(18);
        content.addView(noteCard, noteParams);

        TextView noteTitle = makeText("流体云显示", 14, Color.rgb(35, 49, 73));
        noteTitle.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        noteTitle.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        noteCard.addView(noteTitle, matchWrap());

        TextView note = makeText(
                "后台通知每秒更新，可尝试显示在 OPPO 流体云。是否展示取决于机型与 ColorOS 设置。功率为电池端估算值，不等同于充电器输出功率。",
                12, Color.rgb(105, 121, 146));
        note.setGravity(Gravity.START);
        note.setLineSpacing(dp(4), 1.0f);
        note.setPadding(0, dp(8), 0, 0);
        noteCard.addView(note, matchWrap());

        root.addView(scrollView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
    }

    private LinearLayout createMetricCard(String heading, String label, boolean isCurrent) {
        LinearLayout card = glassCard(dp(20));
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(15), dp(16), dp(12), dp(16));

        TextView title = makeText(heading, 10, Color.rgb(111, 129, 155));
        title.setLetterSpacing(0.12f);
        title.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        card.addView(title, matchWrap());

        TextView value = makeText(label + "：--", 17, Color.rgb(35, 49, 73));
        value.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        value.setSingleLine(true);
        value.setPadding(0, dp(10), 0, 0);
        card.addView(value, matchWrap());
        if (isCurrent) {
            currentText = value;
        } else {
            voltageText = value;
        }
        return card;
    }

    private void requestNotificationPermissionAndStart() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            waitingForPermission = true;
            requestPermissions(
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    NOTIFICATION_PERMISSION_REQUEST);
            return;
        }
        startMonitor();
    }

    private void startMonitor() {
        Intent intent = new Intent(this, PowerMonitorService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void refreshValues() {
        android.content.SharedPreferences preferences =
                getSharedPreferences(PowerMonitorService.PREFERENCES, MODE_PRIVATE);
        powerText.setText(preferences.getString(PowerMonitorService.KEY_POWER, "-- W"));
        currentText.setText("电流：" + preferences.getString(PowerMonitorService.KEY_CURRENT, "--"));
        voltageText.setText("电压：" + preferences.getString(PowerMonitorService.KEY_VOLTAGE, "--"));
        boolean running = preferences.getBoolean(PowerMonitorService.KEY_RUNNING, false);
        stateText.setText(running
                ? preferences.getString(PowerMonitorService.KEY_STATE, "正在监测")
                : "监测未启动");
        stateDot.setBackground(circle(running
            ? Color.rgb(47, 190, 132)
            : Color.rgb(151, 160, 176)));
    }

    private LinearLayout glassCard(int radius) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(glassDrawable(radius, 0.72f, 0.72f));
        card.setElevation(dp(3));
        return card;
    }

    private GradientDrawable glassDrawable(int radius, float fillAlpha, float strokeAlpha) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.argb(Math.round(fillAlpha * 255), 255, 255, 255));
        drawable.setCornerRadius(radius);
        drawable.setStroke(dp(1), Color.argb(Math.round(strokeAlpha * 255), 255, 255, 255));
        return drawable;
    }

    private GradientDrawable circle(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        return drawable;
    }

    private TextView makeText(String text, int sizeSp, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER);
        return view;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private abstract class AnimatedCanvasView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean animating;
        protected float phase;

        AnimatedCanvasView(Activity activity) {
            super(activity);
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        void setAnimating(boolean animating) {
            this.animating = animating;
            if (animating) {
                invalidate();
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            phase = (SystemClock.uptimeMillis() % 12000L) / 12000f;
            drawFrame(canvas, phase, paint);
            if (animating) {
                postInvalidateDelayed(32);
            }
        }

        abstract void drawFrame(Canvas canvas, float phase, Paint paint);

        protected void drawGlow(
                Canvas canvas, Paint paint, float x, float y, float radius, int color, int alpha) {
            paint.setShader(new RadialGradient(
                    x, y, radius,
                    new int[]{Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color)),
                            Color.argb(0, Color.red(color), Color.green(color), Color.blue(color))},
                    null, Shader.TileMode.CLAMP));
            canvas.drawCircle(x, y, radius, paint);
            paint.setShader(null);
        }
    }

    private final class FlowingBackground extends AnimatedCanvasView {
        FlowingBackground(Activity activity) {
            super(activity);
        }

        @Override
        void drawFrame(Canvas canvas, float phase, Paint paint) {
            float width = getWidth();
            float height = getHeight();
            float drift = (float) Math.sin(phase * Math.PI * 2) * dp(22);
            drawGlow(canvas, paint, width * 0.82f + drift, height * 0.08f,
                    dp(250), Color.rgb(109, 180, 255), 34);
            drawGlow(canvas, paint, width * 0.03f - drift, height * 0.46f,
                    dp(220), Color.rgb(120, 211, 222), 28);
            drawGlow(canvas, paint, width * 0.92f + drift, height * 0.84f,
                    dp(255), Color.rgb(166, 146, 255), 30);
        }
    }

    private final class FluidOrb extends AnimatedCanvasView {
        private final Path wavePath = new Path();
        private final Path clipPath = new Path();

        FluidOrb(Activity activity) {
            super(activity);
        }

        @Override
        void drawFrame(Canvas canvas, float phase, Paint paint) {
            float size = Math.min(getWidth(), getHeight());
            float centerX = getWidth() / 2f;
            float centerY = getHeight() / 2f;
            float radius = size * 0.40f;
            if (radius <= 0) {
                return;
            }

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            paint.setColor(Color.argb(170, 255, 255, 255));
            canvas.drawCircle(centerX, centerY, radius + dp(7), paint);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(85, 183, 220, 255));
            canvas.drawCircle(centerX, centerY, radius, paint);

            int save = canvas.save();
            clipPath.reset();
            clipPath.addCircle(centerX, centerY, radius, Path.Direction.CW);
            canvas.clipPath(clipPath);
            float level = centerY + radius * 0.05f;
            float wave = radius * 0.12f;
            float offset = phase * size * 2;

            wavePath.reset();
            wavePath.moveTo(0, level);
            for (int x = 0; x <= getWidth(); x += dp(4)) {
                float y = level + (float) Math.sin((x + offset) / size * Math.PI * 2) * wave;
                wavePath.lineTo(x, y);
            }
            wavePath.lineTo(getWidth(), getHeight());
            wavePath.lineTo(0, getHeight());
            wavePath.close();

            paint.setShader(new android.graphics.LinearGradient(
                    0, level - wave, getWidth(), getHeight(),
                    new int[]{Color.argb(205, 127, 193, 255), Color.argb(178, 125, 145, 246)},
                    null, Shader.TileMode.CLAMP));
            canvas.drawPath(wavePath, paint);
            paint.setShader(null);
            canvas.restoreToCount(save);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            paint.setColor(Color.argb(185, 255, 255, 255));
            canvas.drawCircle(centerX, centerY, radius, paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }
}
