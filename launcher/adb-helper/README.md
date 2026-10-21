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

## 下载与 U 盘安装

下载入口：[TV ADB 助手 1.0](https://github.com/zzusec/tv-desktop/releases/tag/adb-helper-v1.0)。选择 `tv-adb-helper-1.0.apk` 并核对同页 `SHA256SUMS`，不要误装或卸载桌面 APK。

1. 将 APK 放入 U 盘，插入电视，在电视文件管理器中打开 APK。
2. 如系统阻止侧载，在原生设置中明确允许本次安装；不要关闭其他安全校验。
3. 安装后从桌面应用列表打开“TV ADB 助手”。包名为 `com.zzusec.tvadbhelper`，不是桌面的包名。
4. 先查看“刷新诊断”的结果，可用方向键进入报告区域向下滚动。需要协助时可拍摄报告；报告包含本机 IP，请勿公开敏感设备信息。
5. 点击“尝试开启网络 ADB”并阅读确认说明。若显示权限拒绝或仍无法连接，保留错误报告，不要把它当作已开启，不需要因此卸载桌面、恢复出厂或刷机。
6. 如果报告出现 AUTH/CNXN，在可信局域网的电脑上执行下列验证；需要时在电视上允许该电脑的调试授权。维护完成后在系统设置关闭网络调试。

```bash
adb connect TV_IP:5555
adb -s TV_IP:5555 shell id
adb -s TV_IP:5555 shell getprop ro.product.device
```

工具只做到本机地址的有界 TCP 探测，不上传报告，不提供 HTTP 服务，不后台常驻、不注册开机接收器。Android 4.4 上普通应用可能看不到全部进程，因此“未在可见列表找到 adbd”不等于服务一定不存在。

## 构建与验证

使用项目现有 JDK 17、Android SDK 34 与 Gradle Wrapper；没有新增运行时依赖。

```bash
cd launcher
./gradlew :adb-helper:assembleRelease :adb-helper:lintRelease :adb-helper:testReleaseUnitTest --console=plain
```

产物：`adb-helper/build/outputs/apk/release/adb-helper-release.apk`。发布包使用本地调试证书签名，与桌面分属不同包名；自行构建的证书可能不同，升级前核对签名。不要提交 keystore 或设备报告。

本模块包含权限拒绝、静默写入失败、已有值伪成功、只在网卡监听、非 ADB 服务、报文截断/校验错误和超时的 JVM 测试。构建、lint、单元测试与 v1 签名校验不等于 API 19 电视实机验收；首版需 U 盘安装后核对实际 UI、权限结果和电脑端连接。

首版已通过 21 项 JVM 测试、release 构建和 lint（0 个错误、4 个警告）；桌面原有 64 项 JVM 和仓库 18 项 Python 回归也通过。APK 使用兼容 Android 4.4 的 v1 签名并同时通过 v2 校验。尚未在电视上安装，不宣称网络 ADB 已恢复。

## Android 4.4 权限依据

- [AOSP AndroidManifest.xml：WRITE_SECURE_SETTINGS](https://android.googlesource.com/platform/frameworks/base/+/android-4.4.4_r2/core/res/AndroidManifest.xml)
- [AOSP SettingsProvider：Global/Secure 写入检查](https://android.googlesource.com/platform/frameworks/base/+/android-4.4.4_r2/packages/SettingsProvider/src/com/android/providers/settings/SettingsProvider.java)
- [AOSP property_service.c：调用者身份和属性写权限](https://android.googlesource.com/platform/system/core/+/android-4.4.4_r2/init/property_service.c)
- [AOSP adb.c：adbd 的 TCP 端口选择](https://android.googlesource.com/platform/system/core/+/android-4.4.4_r2/adb/adb.c)
