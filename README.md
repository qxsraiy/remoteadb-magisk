# RemoteADB Magisk 模块

让一台 **已 root 的 Android 手机**变成可随时远程接管的"云手机":开机自动开 ADB、无线调试固定端口、免授权弹窗、停用授权超时;可选**自绘九宫格锁屏**(物理拿手机的人被挡在外面,持私钥的你 adb 一连就撤锁),可选**USB 网络共享**。

刷入时用**音量键选择**要开哪些功能(10 秒不按走默认),之后随时改 `config.conf`。

## 功能

- 开机自动开启 USB 调试与无线调试,**固定 TCP 端口**(默认 5555,可配)
- **刷入时在手机本地生成 adb 密钥对**,私钥落到 `/sdcard/RemoteADB-keys/adbkey`,拿走就能用其他设备连接,无需事先准备密钥
- 预置公钥 → 永不弹授权框;`adb_allowed_connection_time=0` 授权永不超时
- adbd 守护:端口掉了自动拉起,Wi-Fi 切换自动恢复
- **自绘九宫格锁屏**(可开关):物理亮屏即锁,adb 无线连接自动撤锁且不亮屏,断开立即回锁;手动解锁后用到灭屏为止
- **USB 网络共享**(可开关):开机自动开 RNDIS,插线即可给电脑/路由器供网,60 秒巡检保活
- 卸载模块自动还原所有系统设置

## 刷入时的音量键菜单

刷入模块后会出现两个选择,每个都有 10 秒窗口:

| 提示 | 音量上 | 音量下 | 10 秒默认 |
|---|---|---|---|
| 自绘锁屏 (九宫格图案锁) | 开启 | 关闭 | **关闭** |
| USB 网络共享 (RNDIS) | 开启 | 关闭 | **关闭** |

> 为什么默认关闭:锁屏功能会禁用系统锁屏,属于强侵入项,默认不动系统行为更安全。

## 功能开关配置文件

模块目录 `/data/adb/modules/remoteadb/config.conf`,改完**重启生效**。每项都有注释,摘要:

| 开关 | 默认 | 说明 |
|---|---|---|
| `LOCKSCREEN` | 1 | 1=自绘锁屏接管;0=不碰系统锁屏,恢复系统原本行为 |
| `USB_TETHER` | 0 | 1=开机自动 USB 网络共享(RNDIS) |
| `ADB_PORT` | 5555 | 无线调试端口,改了要同步改转发规则 |
| `ADB_FIREWALL` | 1 | 1=用 iptables 白名单限制来源(强烈建议开) |
| `ADB_ALLOWED_CIDRS` | `192.168.2.0/24 127.0.0.1/32` | 允许访问 ADB 端口的网段,空格分隔 |
| `KEEPALIVE` | 60 | adbd 保活巡检间隔(秒) |

## 取出密钥连接

刷入完成后:

```
/sdcard/RemoteADB-keys/adbkey        # 私钥 -> 主控端使用
/sdcard/RemoteADB-keys/adb_keys      # 公钥(本机已自动授权)
```

主控端把 `adbkey` 放到 `~/.android/adbkey`(或主控 app 里指定),然后:

```bash
adb connect <手机IP>:5555
```

密钥是**刷入时在手机上现生成的**,从不上网。也可以换成自己的密钥:把自己的 `adbkey.pub` 内容覆盖 `module/adb_keys`(或手机上的 `/data/adb/modules/remoteadb/adb_keys`)后重启。

## 修改锁屏图案

编辑 `pattern.txt`:一行数字,九宫格编号 0-8(从左到右、从上到下),默认 `0124678`(Z 形)。

```
0 1 2
3 4 5
6 7 8
```

> `LOCKSCREEN=0` 时图案不生效。

## 安全模型(必读)

**密钥在这台 ROM 上不一定是防线。** 实测 ColorOS 15 的 adbd 由 init 以 `--root_seclabel=u:r:su:s0`(root 模式)启动,日志明确打印 `authentication not required` —— 此模式下 AOSP 直接跳过 RSA 鉴权,**任何能连上 5555 的机器都能进 shell**(非 root,但能截图/注入输入/读写 sdcard)。

因此本模块加了第二道防线:**iptables 来源白名单**(`ADB_FIREWALL=1`):

- 只放行 `ADB_ALLOWED_CIDRS` 里列出的网段,其余全部 DROP
- 默认只允许本机网段 `192.168.2.0/24` + loopback,**请按自己的网络改**
- 用 Tailscale 组网就加 `100.64.0.0/10`;用 frp 隧道则把隧道源地址放进来
- 白名单一条都没解析成功时会**自动放弃限制**而不是自锁(并有日志)

如果手机的 adbd 走正常模式(大多数 ROM),密钥鉴权仍生效,白名单则是额外一层。

## 适用场景

- 自有闲置手机当云手机/挂机机,人在外面随时接管
- 配合 frp / Tailscale / DDNS 端口转发从外网访问(本模块只负责固定端口,隧道请选成熟方案)
- 配合 scrcpy(`--turn-screen-off`)或 Scrcpy for Android 等主控端投屏控制

## 限制与注意

- **需要 Magisk root**
- 锁屏是**悬浮窗方案**:安全模式下不生效;懂 root 的人物理拿到手机也能绕过。防的是顺手翻手机,不防专业取证
- **SIM 卡若设了 PIN**:自绘锁屏会禁用系统锁屏,SIM PIN 弹窗不再出现,重启后 SIM 无服务。建议取消 SIM PIN
- 端口暴露后,**adb 私钥就是唯一防线**:外网务必走 frp/Tailscale 等带鉴权的隧道
- 投屏时被控机物理屏由主控 `--turn-screen-off` 控制;不带该参数时若屏幕是灭的,主控会看到黑屏(正常现象)

## 设备适配

| 环境 | 状态 |
|---|---|
| ColorOS 15 (OPPO, Android 15, 1080x2400) | ✅ 实测通过(注意: 该 ROM adbd 为 root 模式, 跳过密钥鉴权, 必须靠 ADB_FIREWALL) |
| Android 8+ 其他 ROM | 理论可用(悬浮窗+root 方案不挑系统) |
| MIUI / HyperOS | 未实测;窗口尺寸异常时查 LockService 的 getRealMetrics 逻辑 |
| Pixel / 三星 | 未实测;FBE/生物解锁绑定更严,行为可能不同 |
| Android 11 以下 | `adb_wifi_enabled` 可能无效,5555 经典模式仍可用 |

## 目录结构

```
module/            模块源码(脚本/属性/特权权限/keygen.dex)
module/config.conf 功能开关(带注释)
module/keygen.dex  密钥生成器(刷入时本机生成密钥对)
controller-app/    自绘锁屏 APK 的 Java 源码
keygen/            密钥生成器 Java 源码
```

## 构建

需要 JDK + Android SDK:

```powershell
# 1. 编译锁屏 APK -> module/system/priv-app/RemoteLock/RemoteLock.apk
# 2. 编译密钥生成器 -> module/keygen.dex
# 3. 把 module/ 打成 zip(根目录即 module.prop),Magisk 刷入
```

## 卸载

Magisk 移除模块并重启,`uninstall.sh` 自动还原:adb 授权超时默认值、系统锁屏恢复、无线调试关闭、USB 共享停止、adb_keys 删除、USB 模式回 `mtp,adb`。

## License

MIT
