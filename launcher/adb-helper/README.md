# TV ADB 助手

独立的 Android 电视 ADB 诊断 APK，适配 Android 4.4 / API 19 起，支持方向键、OK 和返回键。不替换 TV Desktop，不注册 HOME，不修改应用启停状态。

## 能做什么

- 显示设备型号、Android 版本、IPv4 地址、ADB 开关、可见的 adbd 进程和相关系统属性。
- 分别探测 `127.0.0.1:5555` 和电视自身的非回环 IPv4 地址，区分回环与本机网卡上的可达监听。
- 发送不含密钥的 ADB CNXN 探测，检查完整的 AUTH/CNXN 报文、长度、校验和和超时；不会发送 AUTH 签名或公钥，不会代替用户允许电脑授权。
- 提供“尝试开启网络 ADB”：只在当前值不同于目标时，请求开启系统 ADB 开关、设置端口 5555，并对已经存在的厂商 `netadb.config` 属性请求启用。逐项显示错误和读回结果，随后重新检查监听。
- 打开标准开发者设置；固件没有该入口时打开系统设置并明确提示。

## 权限限制：不是无条件开启 ADB 的工具

**普通安装通常不能直接开启 ADB。** Android 4.4 的 `WRITE_SECURE_SETTINGS` 为系统/签名/development 权限，普通 APK 无法自行获取；该版本 SettingsProvider 的写入路径还要求 `WRITE_SETTINGS`。应用声明权限不代表已获系统授权。

系统属性服务也单独检查调用者 UID/GID。反射 `SystemProperties.set` 不会将普通应用变成 shell 或 root。小米固件的 `sys.usb.netadb.config` / `persist.sys.usb.netadb.config` 是厂商行为，不是通用 Android API；工具只在属性已存在且当前值不是 1 时尝试写入，不声称已验证每种固件的触发语义。

如果属性原本就是 5555/1，本工具会显示“未证实变化”，而不是重复写入并假报成功。即使写入后值确实变化，也不等于服务已启动。**最终恢复必须由外部电脑实际连接并执行 ADB shell 验证**；本机端口开放、AUTH 响应或设置开关勾选都不是完整成功证明。

本工具不执行 `su`，不调用 `ctl.start/stop/restart`，不关闭 `ro.adb.secure`，不清除 ADB 授权，不重启电视，不修改 USB functions、原桌面或系统分区，不提供提权或刷机能力。普通应用权限不足时只能诊断和引导进入原生设置，无法强行修复系统服务。

## HTTP 读取诊断

选择“开启 HTTP 日志”，电视会显示 `http://TV_IP:8765/<随机令牌>/`。同一可信局域网内的电脑打开完整地址，即可读取报告；在地址后加 `report.txt` 可获取 UTF-8 纯文本。HTTP 服务与 ADB 独立，ADB 服务停止时仍可用。

- 默认关闭；仅在助手前台手动开启，离开助手或手动关闭即停止，不开机自启。
- 页面只读，显示最近一次诊断与本次开启尝试的逐项结果，以及更新时间；在电视上选择“刷新诊断”或执行开启尝试后，电脑刷新页面获取新结果。HTTP 刷新本身不会执行系统操作或重新诊断。
- 每次开启生成新访问令牌，不开放任意文件、系统命令、安装接口或远程开启 ADB 的接口。
- 地址包含私有设备报告的访问凭据，不要公开。HTTP 不加密，只在可信局域网使用，不做公网端口映射。

## 在线升级

选择“检查更新”，通过固定的 GitHub HTTPS 元数据检查助手新版本。发现新版后先确认下载；下载完成后检查 SHA-256、包名、严格递增的版本号和与本机安装相同的签名证书，全部通过才允许请求系统安装。安装仍需电视端确认，不依赖 ADB，不静默安装或卸载旧版。

Android 8 及以上如果未允许该助手安装应用，会引导进入系统“安装未知应用”授权页，返回后再次选择“安装已下载更新”。Android 4.4–6 的安装器仅支持文件地址，因此会将已验证的 APK 复制到助手的外部存储专属更新目录，重新校验后交给系统；该路径需要可用的外部存储（可包含电视内置共享存储）。较旧固件可能需要在原生设置中允许侧载。外部存储在旧系统上并非私有区，安装器仍会核对已有应用签名；维护后可删除该更新 APK。网络、HTTPS 证书、安装权限或签名失败会明确显示错误，不会关闭 TLS 校验或降级版本。

元数据位于 [`update.json`](update.json)，仅更新本助手，不会替换 TV Desktop。API 19 默认禁用的 TLS 1.2 会在连接中启用。API 19–23 对固定 GitHub 主机连接先使用系统证书库；旧证书库校验失败时，再使用内置的 ISRG Root X1 和 USERTrust ECC 两份公开根证书进行标准链验证，保留域名校验，不信任任意证书。电视网络、系统时间与固件的 TLS 兼容性仍须实机验证。无需自建服务；GitHub Release 提供 APK，仓库提供版本元数据。1.1.1 修正了 Android 5–6 安装器不接受 content URI 的兼容问题。1.0 没有升级入口，首次到 1.1.1 仍需 U 盘覆盖安装一次，之后可在线升级。

## 下载与 U 盘安装

下载入口：[TV ADB 助手 1.1.1](https://github.com/zzusec/tv-desktop/releases/tag/adb-helper-v1.1.1)。选择 `tv-adb-helper-1.1.1.apk` 并核对同页 `SHA256SUMS`，不要误装或卸载桌面 APK。

1. 将 APK 放入 U 盘，插入电视，在电视文件管理器中打开 APK。
2. 如系统阻止侧载，在原生设置中明确允许本次安装；不要关闭其他安全校验。
3. 安装后从桌面应用列表打开“TV ADB 助手”。包名为 `com.zzusec.tvadbhelper`，不是桌面的包名。
4. 先查看“刷新诊断”的结果，可用方向键进入报告区域向下滚动。需要协助时可开启 HTTP 读取报告，或拍摄报告；报告包含本机 IP，请勿公开敏感设备信息。
5. 点击“尝试开启网络 ADB”并阅读确认说明。若显示权限拒绝或仍无法连接，保留错误报告，不要把它当作已开启，不需要因此卸载桌面、恢复出厂或刷机。
6. 如果报告出现 AUTH/CNXN，在可信局域网的电脑上执行下列验证；需要时在电视上允许该电脑的调试授权。维护完成后在系统设置关闭网络调试。

```bash
adb connect TV_IP:5555
adb -s TV_IP:5555 shell id
adb -s TV_IP:5555 shell getprop ro.product.device
```

工具不主动上传诊断；只在手动开启后通过带令牌的 HTTP 地址提供报告，不后台常驻、不注册开机接收器。检查更新会访问 GitHub，但不会上传诊断报告。Android 4.4 上普通应用可能看不到全部进程，因此“未在可见列表找到 adbd”不等于服务一定不存在。

## 构建与验证

使用项目现有 JDK 17、Android SDK 34 与 Gradle Wrapper；没有新增运行时依赖。

```bash
cd launcher
./gradlew :adb-helper:assembleRelease :adb-helper:lintRelease :adb-helper:testReleaseUnitTest --console=plain
```

产物：`adb-helper/build/outputs/apk/release/adb-helper-release.apk`。发布包使用本地调试证书签名，与桌面分属不同包名；自行构建的证书可能不同，升级前核对签名。不要提交 keystore 或设备报告。

本模块包含权限拒绝、静默写入失败、已有值伪成功、只在网卡监听、非 ADB 服务、报文截断/校验错误和超时，以及 HTTP 令牌、转义、请求边界、关闭和升级安全策略的 JVM 测试。构建、lint、单元测试与 v1 签名校验不等于 API 19 电视实机验收；首版需 U 盘安装后核对实际 UI、权限结果和电脑端连接。

1.0 已在 Android 4.4 小米电视上运行并采集到真实诊断；这不代表已恢复 ADB。1.1.1 已通过 43 项助手 JVM 测试、release 构建和 lint（0 错误，4 项既有警告）；桌面 64 项 JVM 与仓库 18 项 Python 回归通过。API 21 模拟器已验证 HTTP 页面/文本报告、退出后关闭及 GitHub HTTPS 握手，构建和模拟器测试不能替代 Android 4.4 目标电视实测。发布 APK 保留兼容 Android 4.4 的 v1 签名和原证书，以便覆盖安装。

## Android 4.4 权限与安装依据

- [AOSP AndroidManifest.xml：WRITE_SECURE_SETTINGS](https://android.googlesource.com/platform/frameworks/base/+/android-4.4.4_r2/core/res/AndroidManifest.xml)
- [AOSP SettingsProvider：Global/Secure 写入检查](https://android.googlesource.com/platform/frameworks/base/+/android-4.4.4_r2/packages/SettingsProvider/src/com/android/providers/settings/SettingsProvider.java)
- [AOSP property_service.c：调用者身份和属性写权限](https://android.googlesource.com/platform/system/core/+/android-4.4.4_r2/init/property_service.c)
- [AOSP PackageInstallerActivity：KitKat 支持的安装 URI](https://android.googlesource.com/platform/packages/apps/PackageInstaller/+/android-4.4.4_r2/src/com/android/packageinstaller/PackageInstallerActivity.java)
- [AOSP adb.c：adbd 的 TCP 端口选择](https://android.googlesource.com/platform/system/core/+/android-4.4.4_r2/adb/adb.c)

## 更新连接使用的公开根证书

根证书只用于上述旧系统的助手更新连接，不修改电视系统证书库或其他应用。证书过期/轮换时需维护新版，不能通过忽略 TLS 错误解决。

- ISRG Root X1：来自 [Let's Encrypt 官方 PEM](https://letsencrypt.org/certs/isrgrootx1.pem)，DER SHA-256：`96bcec06264976f37460779acf28c5a7cfe8a3c0aae11a8ffcee05c0bddf08c6`。
- USERTrust ECC Certification Authority：与本机受信 CA 库及 [Sectigo 官方证书](http://crt.sectigo.com/USERTrustECCCertificationAuthority.crt) 双份比对一致，DER SHA-256：`4ff460d54b9c86dabfbcfc5712e0400d2bed3fbc4d4fbdaa86e06adcd2a9ad7a`。官方历史下载地址是 HTTP；证书身份不是仅凭这个下载通道认定，测试固定校验上述指纹、CA 属性与自签名。
