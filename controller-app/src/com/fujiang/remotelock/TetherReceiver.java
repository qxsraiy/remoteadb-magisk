package com.fujiang.remotelock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.TetheringManager;
import android.os.Build;

/**
 * 由模块广播触发: 开启 USB 网络共享。
 * 用公开 API TetheringManager(部分 ROM 的 cmd tethering 没有 shell 实现),
 * 需要 TETHER_PRIVILEGED 特权权限(模块已用 privapp 白名单授权)。
 */
public class TetherReceiver extends BroadcastReceiver {
    /** ConnectivityManager.TETHERING_USB 的常量值(该常量在 SDK stub 中为隐藏) */
    private static final int TETHERING_USB = 1;

    @Override public void onReceive(Context c, Intent i) {
        try {
            if (Build.VERSION.SDK_INT < 30) {
                android.util.Log.w("RL", "usb tether via app requires Android 11+");
                return;
            }
            TetheringManager tm = c.getSystemService(TetheringManager.class);
            if (tm == null) { android.util.Log.e("RL", "no TetheringManager"); return; }
            TetheringManager.TetheringRequest req =
                new TetheringManager.TetheringRequest.Builder(TETHERING_USB).build();
            tm.startTethering(req, c.getMainExecutor(), new TetheringManager.StartTetheringCallback() {
                @Override public void onTetheringStarted() {
                    android.util.Log.i("RL", "usb tethering started");
                }
                @Override public void onTetheringFailed(int error) {
                    android.util.Log.e("RL", "usb tethering failed: " + error);
                }
            });
        } catch (Throwable t) {
            android.util.Log.e("RL", "tether start exception", t);
        }
    }

}
