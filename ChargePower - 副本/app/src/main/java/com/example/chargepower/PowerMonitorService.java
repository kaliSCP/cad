package com.example.chargepower;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;

import java.util.Locale;

public class PowerMonitorService extends Service {
    static final String PREFERENCES = "charge_power";
    static final String KEY_RUNNING = "running";
    static final String KEY_POWER = "power";
    static final String KEY_CURRENT = "current";
    static final String KEY_VOLTAGE = "voltage";
    static final String KEY_STATE = "state";

    private static final String CHANNEL_ID = "charging_power";
    private static final int NOTIFICATION_ID = 1001;
    private static final String ACTION_STOP = "com.example.chargepower.STOP";

    private HandlerThread samplingThread;
    private Handler samplingHandler;
    private BatteryManager batteryManager;

    private final Runnable sample = new Runnable() {
        @Override
        public void run() {
            updateReading();
            samplingHandler.postDelayed(this, 1000);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        batteryManager = (BatteryManager) getSystemService(BATTERY_SERVICE);
        createNotificationChannel();
        samplingThread = new HandlerThread("PowerMonitor");
        samplingThread.start();
        samplingHandler = new Handler(samplingThread.getLooper());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }

        startForeground(NOTIFICATION_ID, buildNotification("正在读取电池数据…", "请稍候"));
        getSharedPreferences(PREFERENCES, MODE_PRIVATE)
                .edit().putBoolean(KEY_RUNNING, true).apply();
        samplingHandler.removeCallbacks(sample);
        samplingHandler.post(sample);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        samplingHandler.removeCallbacks(sample);
        samplingThread.quitSafely();
        stopForeground(true);
        getSharedPreferences(PREFERENCES, MODE_PRIVATE)
                .edit().putBoolean(KEY_RUNNING, false).apply();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void updateReading() {
        Intent batteryStatus = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int voltageMillivolts = batteryStatus == null
                ? 0 : batteryStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);
        int plugged = batteryStatus == null
                ? 0 : batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
        int batteryStatusCode = batteryStatus == null
                ? BatteryManager.BATTERY_STATUS_UNKNOWN
                : batteryStatus.getIntExtra(
                        BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN);
        long currentMicroAmps = batteryManager.getLongProperty(
                BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);

        boolean charging = plugged != 0
                && (batteryStatusCode == BatteryManager.BATTERY_STATUS_CHARGING
                || batteryStatusCode == BatteryManager.BATTERY_STATUS_UNKNOWN);
        String state;
        if (plugged == 0) {
            state = "请连接充电器";
        } else if (batteryStatusCode == BatteryManager.BATTERY_STATUS_FULL) {
            state = "电池已充满";
        } else if (charging) {
            state = "正在充电";
        } else {
            state = "电源已连接 · 暂无充电";
        }
        String power;
        String current;
        String voltage;

        if (!charging) {
            power = "0.0 W";
            current = "—";
            voltage = voltageMillivolts > 0
                    ? String.format(Locale.getDefault(), "%.2f V", voltageMillivolts / 1000.0)
                    : "不可用";
        } else if (currentMicroAmps == Long.MIN_VALUE || voltageMillivolts <= 0) {
            power = "-- W";
            current = currentMicroAmps == Long.MIN_VALUE
                    ? "设备不支持读取" : formatCurrent(currentMicroAmps);
            voltage = voltageMillivolts > 0
                    ? String.format(Locale.getDefault(), "%.2f V", voltageMillivolts / 1000.0)
                    : "设备不支持读取";
            state = "正在充电 · 电池数据不完整";
        } else {
            double amps = Math.abs(currentMicroAmps) / 1_000_000.0;
            double volts = voltageMillivolts / 1000.0;
            power = String.format(Locale.getDefault(), "%.1f W", amps * volts);
            current = String.format(Locale.getDefault(), "%.0f mA", Math.abs(currentMicroAmps) / 1000.0);
            voltage = String.format(Locale.getDefault(), "%.2f V", volts);
        }

        String notificationDetail = charging
                ? state + " · " + current + " · " + voltage
                : state + (voltageMillivolts > 0 ? " · " + voltage : "");
        getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit()
                .putString(KEY_POWER, power)
                .putString(KEY_CURRENT, current)
                .putString(KEY_VOLTAGE, voltage)
                .putString(KEY_STATE, state)
                .apply();
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        manager.notify(NOTIFICATION_ID, buildNotification("充电功率 · " + power, notificationDetail));
    }

    private String formatCurrent(long microAmps) {
        return String.format(Locale.getDefault(), "%.0f mA", Math.abs(microAmps) / 1000.0);
    }

    private Notification buildNotification(String title, String detail) {
        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(
                this, 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent stopIntent = new Intent(this, PowerMonitorService.class).setAction(ACTION_STOP);
        PendingIntent stopPendingIntent = PendingIntent.getService(
                this, 1, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        builder.setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setContentTitle(title)
                .setContentText(detail)
                .setSubText("电池端功率估算")
                .setCategory(Notification.CATEGORY_STATUS)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(contentIntent)
                .addAction(android.R.drawable.ic_media_pause, "停止", stopPendingIntent);
        return builder.build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "充电功率监测", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("持续显示电池端估算充电功率");
            NotificationManager manager =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            manager.createNotificationChannel(channel);
        }
    }
}
