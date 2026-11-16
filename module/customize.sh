SKIPUNZIP=1

ui_print "- 解压模块文件"
unzip -o "$ZIPFILE" -x 'META-INF/*' -d $MODPATH >&2
set_perm_recursive $MODPATH 0 0 0755 0644
set_perm $MODPATH/service.sh 0 0 0755
set_perm $MODPATH/post-fs-data.sh 0 0 0755
set_perm_recursive $MODPATH/system/priv-app 0 0 0755 0644

ui_print "- 预置 ADB 授权公钥"
mkdir -p /data/misc/adb 2>/dev/null
cp -af $MODPATH/adb_keys /data/misc/adb/adb_keys 2>/dev/null
chmod 644 /data/misc/adb/adb_keys 2>/dev/null

ui_print "- 关闭授权超时/系统锁屏"
settings put global adb_allowed_connection_time 0 2>/dev/null
settings put global adb_enabled 1 2>/dev/null
settings put global adb_wifi_enabled 1 2>/dev/null
settings put secure lockscreen.disabled 1 2>/dev/null
locksettings set-disabled true 2>/dev/null

ui_print "- 悬浮窗权限"
appops set com.fujiang.remotelock SYSTEM_ALERT_WINDOW allow 2>/dev/null

ui_print "- 完成: 图案密码见模块目录 pattern.txt (默认Z形0124678)"
