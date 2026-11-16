#!/system/bin/sh
MODDIR=${0%/*}
PKG=com.fujiang.remotelock
SVC=$PKG/.LockService

# ========== ADB 基础保障 ==========
settings put global adb_enabled 1
settings put global adb_allowed_connection_time 0
settings put global adb_wifi_enabled 1
cp -af $MODDIR/adb_keys /data/misc/adb/adb_keys 2>/dev/null
chmod 644 /data/misc/adb/adb_keys 2>/dev/null
setprop service.adb.tcp.port 5555
setprop sys.usb.config adb 2>/dev/null
stop adbd; start adbd

# ========== 禁用系统锁屏(锁机交给自绘悬浮窗) ==========
settings put secure lockscreen.disabled 1
locksettings set-disabled true 2>/dev/null

# ========== 悬浮窗权限 + 图案哈希下发 ==========
appops set $PKG SYSTEM_ALERT_WINDOW allow 2>/dev/null
dumpsys deviceidle whitelist +$PKG >/dev/null 2>&1
PAT=$(cat $MODDIR/pattern.txt 2>/dev/null | tr -d '\r\n ')
[ -z "$PAT" ] && PAT=0124678
HASH=$(echo -n "$PAT" | sha256sum | cut -d' ' -f1)
am broadcast -a com.fujiang.remotelock.SET_PATTERN --es hash "$HASH" -p $PKG 2>/dev/null

adb_connected() {
  # 仅无线5555有已建立会话时才算主控在线(USB不算)
  netstat -ant 2>/dev/null | grep ':5555 ' | grep -q ESTABLISHED
}

# ========== 守护1: adb连接事件 -> 锁屏显隐(事件驱动+2s校对) ==========
(
CUR=1   # 1=锁屏显示中 0=已隐藏 ; 开机默认布防
while true; do
  logcat -T 1 -s adbd:* 2>/dev/null | while read -r line; do break; done
  if adb_connected; then
    am broadcast -a com.fujiang.remotelock.ADB_ON -p $PKG 2>/dev/null
    [ $CUR -eq 1 ] && CUR=0
  else
    am broadcast -a com.fujiang.remotelock.ADB_OFF -p $PKG 2>/dev/null
    [ $CUR -eq 0 ] && CUR=1
  fi
  sleep 2
done
) &

# ========== 守护2: 锁屏进程保活(被杀立即拉起) ==========
(
while true; do
  if ! pidof $PKG >/dev/null 2>&1; then
    am start-foreground-service $SVC 2>/dev/null || am startservice $SVC 2>/dev/null
    sleep 1
    am broadcast -a com.fujiang.remotelock.SET_PATTERN --es hash "$HASH" -p $PKG 2>/dev/null
    appops set $PKG SYSTEM_ALERT_WINDOW allow 2>/dev/null
  fi
  sleep 5
done
) &

# ========== 守护2.5: 主控投屏(scrcpy-server)出现才亮屏 ==========
(
SCR=0
while true; do
  FOUND=0
  for p in /proc/[0-9]*/cmdline; do
    if tr '\0' ' ' < $p 2>/dev/null | grep -q 'genymobile.scrcpy'; then FOUND=1; break; fi
  done
  if [ $FOUND -eq 1 ] && [ $SCR -eq 0 ]; then
    input keyevent 224 2>/dev/null   # 主控开始投屏, 点亮屏幕
    input keyevent 82 2>/dev/null    # 滑开残余锁屏直通桌面
    SCR=1
  elif [ $FOUND -eq 0 ]; then
    SCR=0
  fi
  sleep 2
done
) &

# ========== 守护3: adbd 5555 保活 ==========
(
while true; do
  sleep 60
  settings put global adb_enabled 1 2>/dev/null
  settings put global adb_allowed_connection_time 0 2>/dev/null
  if ! netstat -lnt 2>/dev/null | grep -q ':5555 '; then
    setprop service.adb.tcp.port 5555
    stop adbd; start adbd
  fi
done
) &
