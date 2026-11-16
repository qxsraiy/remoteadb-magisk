package com.fujiang.remotelock;

import android.content.Context;
import android.content.SharedPreferences;

public class Prefs {
    private static final String NAME = "rl";
    public static String getHash(Context c) {
        return c.getSharedPreferences(NAME, 0).getString("pattern_hash", "");
    }
    public static void setHash(Context c, String h) {
        c.getSharedPreferences(NAME, 0).edit().putString("pattern_hash", h).apply();
    }
    public static boolean getAdbOn(Context c) {
        return c.getSharedPreferences(NAME, 0).getBoolean("adb_on", false);
    }
    public static void setAdbOn(Context c, boolean v) {
        c.getSharedPreferences(NAME, 0).edit().putBoolean("adb_on", v).apply();
    }
    public static long getLastUnlock(Context c) {
        return c.getSharedPreferences(NAME, 0).getLong("last_unlock", 0);
    }
    public static void setLastUnlock(Context c, long t) {
        c.getSharedPreferences(NAME, 0).edit().putLong("last_unlock", t).apply();
    }
    public static boolean getManual(Context c) {
        return c.getSharedPreferences(NAME, 0).getBoolean("manual_session", false);
    }
    public static void setManual(Context c, boolean v) {
        c.getSharedPreferences(NAME, 0).edit().putBoolean("manual_session", v).apply();
    }
    public static int getFail(Context c) {
        return c.getSharedPreferences(NAME, 0).getInt("fail", 0);
    }
    public static void setFail(Context c, int n) {
        c.getSharedPreferences(NAME, 0).edit().putInt("fail", n).apply();
    }
}
