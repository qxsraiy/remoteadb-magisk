#!/system/bin/sh
MODDIR=${0%/*}
PKG=com.fujiang.remotelock
SVC=$PKG/.LockService

# ---------- 读配置 ----------
CONF=$MODDIR/config.conf
getconf() { grep -E "^$1=" "$CONF" 2>/dev/null | tail -1 | cut -d= -f2- | tr -d '\r' | sed 's/^ *//; s/ *$//'; }
LOCKSCREEN=$(getconf LOCKSCREEN); [ -z "$LOCKSCREEN" ] && LOCKSCREEN=1
USB_TETHER=$(getconf USB_TETHER); [ -z "$USB_TETHER" ] && USB_TETHER=0
ADB_PORT=$(getconf ADB_PORT);     [ -z "$ADB_PORT" ] && ADB_PORT=5555
KEEPALIVE=$(getconf KEEPALIVE);   [ -z "$KEEPALIVE" ] && KEEPALIVE=60

# ---------- ADB 基础 ----------
settings put global adb_enabled 1
settings put global adb_allowed_connection_time 0
settings put global adb_wifi_enabled 1
mkdir -p /data/misc/adb
cp -af $MODDIR/adb_keys /data/misc/adb/adb_keys 2>/dev/null
chmod 644 /data/misc/adb/adb_keys 2>/dev/null
setprop service.adb.tcp.port $ADB_PORT
if [ "$USB_TETHER" = "1" ]; then
  setprop sys.usb.config rndis,adb 2>/dev/null
else
  setprop sys.usb.config adb 2>/dev/null
fi
stop adbd; start adbd

# ---------- 锁屏开关 ----------
# 开机早期 SettingsProvider 可能未就绪, 带重试确保写入生效
set_system_lock_disabled() {
  want=$1
  i=0
  while [ $i -lt 10 ]; do
    cur=$(settings get secure lockscreen.disabled 2>/dev/null)
    [ "$cur" = "$want" ] && return 0
    if [ "$want" = "1" ]; then
      settings put secure lockscreen.disabled 1 2>/dev/null
      locksettings set-disabled true 2>/dev/null
    else
      settings put secure lockscreen.disabled 0 2>/dev/null
      locksettings set-disabled false 2>/dev/null
    fi
    sleep 3
    i=$((i+1))
  done
}
# 把开关写给 app(persist 便于跨重启)
setprop persist.remoteadb.lock "$LOCKSCREEN" 2>/dev/null
if [ "$LOCKSCREEN" = "1" ]; then
  set_system_lock_disabled 1
  appops set $PKG SYSTEM_ALERT_WINDOW allow 2>/dev/null
  dumpsys deviceidle whitelist +$PKG >/dev/null 2>&1
  PAT=$(cat $MODDIR/pattern.txt 2>/dev/null | tr -d '\r\n ')
  [ -z "$PAT" ] && PAT=0124678
  HASH=$(echo -n "$PAT" | sha256sum | cut -d' ' -f1)
  am broadcast -a com.fujiang.remotelock.SET_PATTERN --es hash "$HASH" -p $PKG 2>/dev/null
else
  # 关闭自绘锁屏: 还原系统锁屏, 停掉锁屏应用
  set_system_lock_disabled 0
  am force-stop $PKG 2>/dev/null
  # 再补一刀: 关掉可能已被系统拉起的服务
  am stopservice $PKG/.LockService 2>/dev/null
fi

# ---------- USB 网络共享 ----------
usb_tether_on() {
  # ROM 可能把 USB 功能改回别的组合, 重试直到 rndis 生效
  i=0
  while [ $i -lt 8 ]; do
    getprop sys.usb.state 2>/dev/null | grep -q rndis && break
    svc usb setFunctions rndis,adb 2>/dev/null || setprop sys.usb.config rndis,adb 2>/dev/null
    sleep 3
    i=$((i+1))
  done
  # 由特权 app 调 TetheringManager 开启(部分 ROM 无 cmd tethering 实现)
  am broadcast -a com.fujiang.remotelock.USB_TETHER -p $PKG 2>/dev/null
  cmd tethering start usb 2>/dev/null || true
  # 3 秒后复查, 未开则再触发一次
  sleep 3
  if ! dumpsys tethering 2>/dev/null | grep -q 'TetheredState'; then
    svc usb setFunctions rndis,adb 2>/dev/null || setprop sys.usb.config rndis,adb 2>/dev/null
    sleep 2
    am broadcast -a com.fujiang.remotelock.USB_TETHER -p $PKG 2>/dev/null
  fi
}

if [ "$USB_TETHER" = "1" ]; then
  sleep 8   # 等系统 USB 服务就绪
  usb_tether_on
  # ---------- 看门狗: 30 秒巡检, rndis 状态或共享会话掉任一即重开 ----------
  (
  FAIL=0
  while true; do
    sleep 30
    NEED=0
    getprop sys.usb.state 2>/dev/null | grep -q rndis || NEED=1
    dumpsys tethering 2>/dev/null | grep -q 'TetheredState' || NEED=1
    if [ $NEED -eq 1 ]; then
      usb_tether_on
      # 连续 3 次修不好就放慢到 5 分钟, 避免狂切 USB 影响 adb
      FAIL=$((FAIL+1))
      [ $FAIL -ge 3 ] && { sleep 270; FAIL=0; }
    else
      FAIL=0
    fi
  done
  ) &
fi

adb_connected() {
  netstat -ant 2>/dev/null | grep ":$ADB_PORT " | grep -q ESTABLISHED
}

# ---------- 守护1: adb 连接事件 -> 锁屏显隐 ----------
if [ "$LOCKSCREEN" = "1" ]; then
(
CUR=1
while true; do
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

# ---------- 守护2: 锁屏进程保活 ----------
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
fi

# ---------- 守护3: adbd 保活 ----------
(
while true; do
  sleep $KEEPALIVE
  settings put global adb_enabled 1 2>/dev/null
  settings put global adb_allowed_connection_time 0 2>/dev/null
  if [ "$LOCKSCREEN" = "1" ]; then
    [ "$(settings get secure lockscreen.disabled 2>/dev/null)" = "1" ] || set_system_lock_disabled 1
  fi
  if ! netstat -lnt 2>/dev/null | grep -q ":$ADB_PORT "; then
    setprop service.adb.tcp.port $ADB_PORT
    stop adbd; start adbd
  fi
done
) &
