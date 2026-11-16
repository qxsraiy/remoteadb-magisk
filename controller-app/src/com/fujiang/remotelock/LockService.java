package com.fujiang.remotelock;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.view.WindowManager;

public class LockService extends Service {
    private WindowManager wm;
    private LockView lockView;
    private BroadcastReceiver screenRx;
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private static final long RELOCK_DELAY = 30000;
    private final Runnable relockTask = new Runnable() {
        @Override public void run() {
            android.os.PowerManager pm = (android.os.PowerManager) getSystemService(POWER_SERVICE);
            if (!Prefs.getAdbOn(LockService.this) && pm.isInteractive()) showLock();
        }
    };

    private boolean inGrace() {
        return System.currentTimeMillis() - Prefs.getLastUnlock(this) < RELOCK_DELAY;
    }

    private void scheduleRelock() {
        handler.removeCallbacks(relockTask);
        long left = RELOCK_DELAY - (System.currentTimeMillis() - Prefs.getLastUnlock(this));
        handler.postDelayed(relockTask, Math.max(left, 1000));
    }

    @Override public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        NotificationChannel ch = null;
        if (Build.VERSION.SDK_INT >= 26) {
            ch = new NotificationChannel("rl", "RemoteLock", NotificationManager.IMPORTANCE_MIN);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
            Notification n = new Notification.Builder(this, "rl")
                .setContentTitle("RemoteLock").setContentText("running")
                .setSmallIcon(R.drawable.ic_lock).build();
            try { startForeground(42, n); } catch (Throwable t) { android.util.Log.e("RL", "startForeground", t); }
        }
        // 监听物理亮屏事件: 亮屏即布防
        screenRx = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                if (Intent.ACTION_SCREEN_ON.equals(i.getAction()) && !Prefs.getAdbOn(c) && !Prefs.getManual(c)) showLock();
                else if (Intent.ACTION_SCREEN_OFF.equals(i.getAction())) {
                    Prefs.setManual(c, false);  // 灭屏即结束手动会话, 下次亮屏必锁
                    handler.removeCallbacks(relockTask);
                }
            }
        };
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_SCREEN_OFF);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenRx, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(screenRx, f);
        // 直接接收root发来的锁屏显隐命令(不再经过FGS启动)
        BroadcastReceiver cmdRx = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                String a = i.getAction();
                if ("com.fujiang.remotelock.ADB_ON".equals(a)) {
                    Prefs.setAdbOn(c, true);
                    handler.removeCallbacks(relockTask);
                    hideLock();   // 只撤锁不亮屏, 亮屏等主控投屏事件
                }
                else if ("com.fujiang.remotelock.ADB_OFF".equals(a)) {
                    Prefs.setAdbOn(c, false);
                    android.os.PowerManager pm2 = (android.os.PowerManager) getSystemService(POWER_SERVICE);
                    // 仅当当前是adb解开的会话才即时锁定; 手动解锁的会话不碰
                    if (pm2.isInteractive() && !Prefs.getManual(c)) showLock();
                }
                else if ("com.fujiang.remotelock.LOCK_DISMISS".equals(a)) hideLock();
                else if ("com.fujiang.remotelock.LOCK_SHOW".equals(a)) showLock();
            }
        };
        IntentFilter cf = new IntentFilter();
        cf.addAction("com.fujiang.remotelock.LOCK_SHOW");
        cf.addAction("com.fujiang.remotelock.LOCK_DISMISS");
        cf.addAction("com.fujiang.remotelock.ADB_ON");
        cf.addAction("com.fujiang.remotelock.ADB_OFF");
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(cmdRx, cf, Context.RECEIVER_EXPORTED);
        else registerReceiver(cmdRx, cf);
        // 服务被拉起时若当前屏亮且无adb会话, 直接盖锁
        android.os.PowerManager pm = (android.os.PowerManager) getSystemService(POWER_SERVICE);
        if (pm.isInteractive() && !Prefs.getAdbOn(this) && !Prefs.getManual(this)) showLock();
    }

    @Override public int onStartCommand(Intent i, int flags, int startId) {
        if (i != null && i.getAction() != null) {
            if ("com.fujiang.remotelock.LOCK_DISMISS".equals(i.getAction())) hideLock();
            else if ("com.fujiang.remotelock.LOCK_SHOW".equals(i.getAction())) showLock();
        }
        return START_STICKY;
    }

    synchronized void showLock() {
        if (lockView != null) return;
        lockView = new LockView(this);
        // 显式取屏幕真实尺寸, 防止部分ROM把MATCH_PARENT overlay压成小窗
        // 统一用 getRealMetrics 拿真实物理像素(部分ROM的WindowMetrics返回dp值)
        android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(dm);
        int sw = dm.widthPixels, sh = dm.heightPixels;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
            sw, sh,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
              | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT);
        lp.gravity = android.view.Gravity.TOP | android.view.Gravity.START;
        if (Build.VERSION.SDK_INT >= 28)
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        lp.setTitle("RemoteLock");
        // 禁用状态栏下拉(priv-app STATUS_BAR 权限)
        try {
            Object sbm = getSystemService("statusbar");
            java.lang.reflect.Method m = sbm.getClass().getMethod("disable", int.class);
            m.invoke(sbm, 0x00010000); // DISABLE_EXPAND
        } catch (Throwable ignored) {}
        try {
            wm.addView(lockView, lp);
            wm.updateViewLayout(lockView, lp); // 二次强制全屏
            lockView.post(() -> { lockView.requestLayout(); lockView.invalidate(); });
            android.util.Log.i("RL", "overlay shown " + sw + "x" + sh);
        }
        catch (Throwable t) {
            android.util.Log.e("RL", "addView failed", t);
            lockView = null; // 权限没给到位时下次守护拉起再试
        }
    }

    public synchronized void manualUnlock() {
        Prefs.setLastUnlock(this, System.currentTimeMillis());
        Prefs.setManual(this, true);
        handler.removeCallbacks(relockTask);
        hideLock();  // 手动会话持续到灭屏, 系统息屏超时即"无动作锁定"
    }

    public synchronized void hideLock() {
        if (lockView == null) return;
        try { wm.removeView(lockView); } catch (Throwable ignored) {}
        lockView = null;
        try {
            Object sbm = getSystemService("statusbar");
            java.lang.reflect.Method m = sbm.getClass().getMethod("disable", int.class);
            m.invoke(sbm, 0);
        } catch (Throwable ignored) {}
    }

    private void wakeScreen() {
        try {
            android.os.PowerManager pm = (android.os.PowerManager) getSystemService(POWER_SERVICE);
            android.os.PowerManager.WakeLock wl = pm.newWakeLock(
                android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK | android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "rl:wake");
            wl.acquire(3000);
            wl.release();
        } catch (Throwable ignored) {}
    }

    @Override public IBinder onBind(Intent i) { return null; }
}
