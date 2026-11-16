package com.fujiang.remotelock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class CmdReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        String a = i.getAction();
        if (a == null) return;
        if ("com.fujiang.remotelock.SET_PATTERN".equals(a)) {
            String h = i.getStringExtra("hash");
            if (h != null) Prefs.setHash(c, h);
        }
        // LOCK_SHOW/DISMISS 由 LockService 内动态接收器直接处理
    }
}
