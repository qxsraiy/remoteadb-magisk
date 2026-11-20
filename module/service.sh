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
ADB_FIREWALL=$(getconf ADB_FIREWALL); [ -z "$ADB_FIREWALL" ] && ADB_FIREWALL=1
ADB_ALLOWED_CIDRS=$(getconf ADB_ALLOWED_CIDRS); [ -z "$ADB_ALLOWED_CIDRS" ] && ADB_ALLOWED_CIDRS="192.168.2.0/24 127.0.0.1/32"

# ---------- ADB 基础 ----------
settings put global adb_enabled 1
settings put global adb_allowed_connection_time 0
settings put global adb_wifi_enabled 1
mkdir -p /data/misc/adb
cp -af $MODDIR/adb_keys /data/misc/adb/adb_keys 2>/dev/null
chmod 644 /data/misc/adb/adb_keys 2>/dev/null
setprop service.adb.tcp.port $ADB_PORT
setprop sys.usb.config adb 2>/dev/null
stop adbd; start adbd

# ---------- 锁屏开关 ----------
if [ "$LOCKSCREEN" = "1" ]; then
  settings put secure lockscreen.disabled 1 2>/dev/null
  locksettings set-disabled true 2>/dev/null
  appops set $PKG SYSTEM_ALERT_WINDOW allow 2>/dev/null
  dumpsys deviceidle whitelist +$PKG >/dev/null 2>&1
  PAT=$(cat $MODDIR/pattern.txt 2>/dev/null | tr -d '\r\n ')
  [ -z "$PAT" ] && PAT=0124678
  HASH=$(echo -n "$PAT" | sha256sum | cut -d' ' -f1)
  am broadcast -a com.fujiang.remotelock.SET_PATTERN --es hash "$HASH" -p $PKG 2>/dev/null
else
  # 关闭自绘锁屏: 还原系统锁屏, 停掉锁屏应用
  settings put secure lockscreen.disabled 0 2>/dev/null
  locksettings set-disabled false 2>/dev/null
  am force-stop $PKG 2>/dev/null
fi

# ---------- 端口访问控制(iptables 白名单) ----------
apply_firewall() {
  iptables -D INPUT -p tcp --dport $ADB_PORT -j REMOTEADB 2>/dev/null
  iptables -F REMOTEADB 2>/dev/null
  iptables -X REMOTEADB 2>/dev/null
  iptables -N REMOTEADB 2>/dev/null || return 0
  iptables -A REMOTEADB -i lo -j ACCEPT
  iptables -A REMOTEADB -m conntrack --ctstate ESTABLISHED,RELATED -j ACCEPT
  OK=0
  for c in $ADB_ALLOWED_CIDRS; do
    iptables -A REMOTEADB -s "$c" -j ACCEPT 2>/dev/null && OK=$((OK+1))
  done
  if [ $OK -eq 0 ]; then
    # 白名单解析失败, 宁可不限制也不要自锁
    iptables -F REMOTEADB 2>/dev/null; iptables -X REMOTEADB 2>/dev/null
    return 0
  fi
  iptables -A REMOTEADB -j DROP
  iptables -I INPUT -p tcp --dport $ADB_PORT -j REMOTEADB
}

# ---------- USB 网络共享 ----------
if [ "$USB_TETHER" = "1" ]; then
  sleep 8   # 等系统 USB 服务就绪
  setprop sys.usb.config rndis,adb 2>/dev/null
  sleep 3
  cmd tethering start usb 2>/dev/null || cmd tethering start --usb 2>/dev/null || true
  settings put global tether_enable 1 2>/dev/null
  # 每 60 秒确保共享仍开着
  (
  while true; do
    sleep 60
    if ! dumpsys tethering 2>/dev/null | grep -qi 'usb.*true\|Tethering.*USB'; then
      setprop sys.usb.config rndis,adb 2>/dev/null
      cmd tethering start usb 2>/dev/null || true
    fi
  done
  ) &
fi

[ "$ADB_FIREWALL" = "1" ] && { sleep 10; apply_firewall; }

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
  [ "$ADB_FIREWALL" = "1" ] && apply_firewall
  if ! netstat -lnt 2>/dev/null | grep -q ":$ADB_PORT "; then
    setprop service.adb.tcp.port $ADB_PORT
    stop adbd; start adbd
  fi
done
) &
