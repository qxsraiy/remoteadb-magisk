#!/system/bin/sh
MODDIR=${0%/*}
mkdir -p /data/misc/adb
cp -af $MODDIR/adb_keys /data/misc/adb/adb_keys
chmod 644 /data/misc/adb/adb_keys
