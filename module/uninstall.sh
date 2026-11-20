#!/system/bin/sh
# 卸载时还原本模块改过的系统设置
settings put global adb_allowed_connection_time 604800000 2>/dev/null
settings put global adb_wifi_enabled 0 2>/dev/null
settings put secure lockscreen.disabled 0 2>/dev/null
locksettings set-disabled false 2>/dev/null
resetprop persist.adb.tcp.port "" 2>/dev/null
resetprop service.adb.tcp.port "" 2>/dev/null
cmd tethering stop usb 2>/dev/null
setprop sys.usb.config mtp,adb 2>/dev/null
iptables -D INPUT -p tcp --dport 5555 -j REMOTEADB 2>/dev/null
iptables -F REMOTEADB 2>/dev/null; iptables -X REMOTEADB 2>/dev/null
rm -f /data/misc/adb/adb_keys 2>/dev/null
