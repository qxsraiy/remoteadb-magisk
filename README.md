# RemoteADB Magisk 模块

让一台 **已 root 的 Android 手机**变成可随时远程接管的"云手机":开机自动开 ADB、无线调试固定 5555 端口、免授权弹窗、停用授权超时,外加一套**模块自绘的九宫格锁屏**——物理拿起手机的人被挡在锁屏外,而持有私钥的你通过 adb 一连,锁屏自动解除。

## 功能

- 开机自动开启 USB 调试与无线调试,**固定 TCP 5555 端口**
- 预置 adb 公钥 → 永不弹授权框;`adb_allowed_connection_time=0` 授权永不超时
- adbd 守护:5555 掉了自动拉起,Wi-Fi 切换自动恢复
- 自绘九宫格锁屏(悬浮窗实现,**不依赖系统锁屏**):
  - 物理亮屏即锁;手动解锁后用到灭屏为止(系统息屏超时即"无动作锁定")
  - adb 无线连接建立 → **静默撤锁不亮屏**;断开 → 立即回锁
  - 检测到 scrcpy-server 启动(主控投屏)→ 自动亮屏直通桌面
- 系统锁屏被禁用 → 无 BFU 死锁问题,断电重启直通
- 卸载模块自动还原所有系统设置(adb 超时/锁屏/USB 模式等)
- 锁屏进程被 ROM 杀死后由 root 守护 5 秒拉起,已加电池优化白名单

## 适用场景

- 自有闲置手机当云手机/挂机机,人在外面随时接管
- 配合 frp / Tailscale / DDNS 端口转发从外网访问(本模块只负责固定端口,隧道请自行选择成熟方案)
- 配合 scrcpy / Scrcpy for Android 等主控端投屏控制

## 限制与注意

- **需要 Magisk root**,刷入方式为 Magisk → 模块 → 本地安装
- 锁屏是**悬浮窗方案**:安全模式下不生效;懂 root 的人物理拿到手机也能绕过。它防的是"别人顺手拿起来翻你手机",不防专业取证
- **SIM 卡若设了 PIN**:系统锁屏被禁用后 SIM PIN 弹窗不会出现,重启后 SIM 无服务。建议直接取消 SIM PIN(设置→安全→SIM 卡锁定)
- 5555 端口暴露后,**adb 私钥就是唯一防线**:不要把端口裸奔在不可信网络,外网访问务必走 frp/Tailscale 等带鉴权的隧道
- 无线 adb 连接建立会撤锁——挂机投屏建议配合 `scrcpy --turn-screen-off`

## 设备适配

| 环境 | 状态 |
|---|---|
| ColorOS 15 (OPPO, Android 15, 1080x2400) | ✅ 实测通过 |
| Android 8+ 其他 ROM | 理论可用(悬浮窗+root方案不挑系统) |
| MIUI / HyperOS | 未实测;如遇窗口尺寸异常检查 LockService 的 getRealMetrics 逻辑 |
| Pixel / 三星 | 未实测;Pixel 的 FBE/生物解锁绑定更严,行为可能不同 |
| Android 11 以下 | 无线调试开关项不同,adb_wifi_enabled 可能无效,5555 仍可用 |

## 准备 adb 密钥(替换占位 adb_keys)

主控电脑上:

```bash
# 方式一: 已有 adb 密钥(用过 adb 的电脑都有)
#   Windows: %USERPROFILE%\.android\adbkey.pub
#   Linux/macOS: ~/.android/adbkey.pub
# 把 adbkey.pub 内容粘贴进 module/adb_keys(一行一把,可放多把)

# 方式二: 生成新密钥对
adb keygen mykey        # 生成 mykey(私钥,主控端用)和 mykey.pub(公钥)
cat mykey.pub > module/adb_keys
```

主控端连接时使用对应私钥(放进 ~/.android/ 覆盖默认,或在主控 app 里指定密钥文件)。

## 修改锁屏图案(可选)

编辑 `module/pattern.txt`:一行数字,九宫格点位编号 0-8(从左到右、从上到下)。默认 `0124678`(Z 形)。

```
0 1 2
3 4 5
6 7 8
```

## 构建

需要 JDK + Android SDK:

```powershell
# aapt2 compile+link → javac → d8 → zipalign → apksigner
# 产物放入 module/system/priv-app/RemoteLock/RemoteLock.apk
# 之后把 module/ 打成 zip(根目录即 module.prop),Magisk 刷入重启
```

## 目录结构

```
module/            Magisk 模块源码(脚本、属性、特权权限白名单)
controller-app/    自绘锁屏 APK 的 Java 源码(无桌面图标的特权系统应用)
```

## 卸载

Magisk 里移除模块并重启,uninstall.sh 自动还原:adb 授权超时恢复默认、系统锁屏恢复启用、无线调试关闭、预置 adb_keys 删除、USB 模式恢复 mtp,adb。

## License

MIT
