SKIPUNZIP=1
ui_print "- 解压模块文件"
unzip -o "$ZIPFILE" -x 'META-INF/*' -d $MODPATH >&2
set_perm_recursive $MODPATH 0 0 0755 0644
set_perm $MODPATH/service.sh 0 0 0755
set_perm $MODPATH/post-fs-data.sh 0 0 0755
set_perm_recursive $MODPATH/system/priv-app 0 0 0755 0644

# ============ 音量键选择 ============
# 音量上=开启, 音量下=关闭, 10 秒无操作取默认值
ask_switch() {
  TITLE="$1"; DEF="$2"; REPLY="$DEF"
  ui_print ""
  ui_print "  ┌─ $TITLE"
  ui_print "  │  音量[上] = 开启     音量[下] = 关闭"
  ui_print "  └─ 10 秒不按, 默认: $DEF"
  END=$(( $(date +%s) + 10 ))
  while [ "$(date +%s)" -lt "$END" ]; do
    EV=$(timeout 1 getevent -lc 1 2>/dev/null | grep -E 'KEY_VOLUMEUP|KEY_VOLUMEDOWN' | head -1)
    case "$EV" in
      *KEY_VOLUMEUP*)   REPLY=1; ui_print "  → 已选择: 开启"; return 0 ;;
      *KEY_VOLUMEDOWN*) REPLY=0; ui_print "  → 已选择: 关闭"; return 0 ;;
    esac
  done
  ui_print "  → 超时, 采用默认: $DEF"
}

# 继承上次安装的开关值作为菜单默认(首次安装为 0)
OLD=/data/adb/modules/remoteadb/config.conf
DEF_LOCK=0; DEF_TETH=0
if [ -f "$OLD" ]; then
  DEF_LOCK=$(grep -E '^LOCKSCREEN=' "$OLD" | tail -1 | cut -d= -f2 | tr -d ' \r')
  DEF_TETH=$(grep -E '^USB_TETHER=' "$OLD" | tail -1 | cut -d= -f2 | tr -d ' \r')
fi
[ -z "$DEF_LOCK" ] && DEF_LOCK=0
[ -z "$DEF_TETH" ] && DEF_TETH=0

ui_print "- 请选择功能(10 秒内按音量键)"
ask_switch "自绘锁屏 (九宫格图案锁)" "$DEF_LOCK"
LOCKSCREEN=$REPLY

ask_switch "USB 网络共享 (RNDIS)" "$DEF_TETH"
USB_TETHER=$REPLY

# 写回配置
sed -i "s/^LOCKSCREEN=.*/LOCKSCREEN=$LOCKSCREEN/" $MODPATH/config.conf
sed -i "s/^USB_TETHER=.*/USB_TETHER=$USB_TETHER/" $MODPATH/config.conf
ui_print "- 配置已写入: LOCKSCREEN=$LOCKSCREEN  USB_TETHER=$USB_TETHER"

# ============ 本地生成 adb 密钥对 ============
ui_print "- 生成本机 adb 密钥对"
KEYOK=0
CLASSPATH=$MODPATH/keygen.dex app_process / com.fujiang.keygen.Main "$MODPATH" >/dev/null 2>&1
[ -s "$MODPATH/adbkey" ] && KEYOK=1
if [ "$KEYOK" = "0" ]; then
  CLASSPATH=$MODPATH/keygen.dex app_process /system/bin com.fujiang.keygen.Main "$MODPATH" >/dev/null 2>&1
  [ -s "$MODPATH/adbkey" ] && KEYOK=1
fi
if [ "$KEYOK" = "1" ] && [ -s "$MODPATH/adbkey" ]; then
  mkdir -p /sdcard/RemoteADB-keys 2>/dev/null
  cp -f "$MODPATH/adbkey" "$MODPATH/adb_keys" /sdcard/RemoteADB-keys/ 2>/dev/null
  ui_print "  ✓ 私钥: /sdcard/RemoteADB-keys/adbkey  (主控端加载它即可连接)"
  ui_print "  ✓ 公钥已内置本机授权列表"
else
  ui_print "  ! 密钥生成失败: 请手动把自己的 adbkey.pub 写入 adb_keys"
fi

# ============ 系统侧设置 ============
ui_print "- 预置 ADB 授权公钥"
mkdir -p /data/misc/adb 2>/dev/null
cp -af $MODPATH/adb_keys /data/misc/adb/adb_keys 2>/dev/null
chmod 644 /data/misc/adb/adb_keys 2>/dev/null

ui_print "- 关闭授权超时"
settings put global adb_allowed_connection_time 0 2>/dev/null
settings put global adb_enabled 1 2>/dev/null
settings put global adb_wifi_enabled 1 2>/dev/null
appops set com.fujiang.remotelock SYSTEM_ALERT_WINDOW allow 2>/dev/null

if [ "$LOCKSCREEN" = "1" ]; then
  ui_print "- 禁用系统锁屏(由模块自绘锁屏接管)"
  settings put secure lockscreen.disabled 1 2>/dev/null
  locksettings set-disabled true 2>/dev/null
else
  ui_print "- 关闭自绘锁屏, 保留系统锁屏"
  settings put secure lockscreen.disabled 0 2>/dev/null
  locksettings set-disabled false 2>/dev/null
fi

# 清系统包解析缓存, 确保重启后重新解析 updated priv-app(否则会继续跑旧版本)
rm -rf /data/system/package_cache/* 2>/dev/null

ui_print "- 完成. 改开关: /data/adb/modules/remoteadb/config.conf"
