package com.fujiang.remotelock;

/** 读取模块写入的开关属性(模块 config.conf 由 root 侧读取后 setprop 到这里) */
public class Props {
    /** 自绘锁屏开关: "0"=关闭, 其它=开启 */
    public static boolean lockscreenEnabled() {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            java.lang.reflect.Method get = sp.getMethod("get", String.class, String.class);
            String v = (String) get.invoke(null, "persist.remoteadb.lock", "1");
            return !"0".equals(v);
        } catch (Throwable t) {
            return true; // 读不到就按开启处理(保持旧行为)
        }
    }
}
