# TV Desktop

面向 Android TV、Google TV 和兼容安卓电视的轻量桌面：没有广告推荐流，使用遥控器即可打开应用、切换输入源、进入网络和系统设置。不仅限于 Sony，也保留上游对其他品牌电视的适配与工具。

本项目基于 [Gathin23/CleanYourTV](https://github.com/Gathin23/cleanyourtv)，保留原作者署名和 Git 历史，以 [MIT](LICENSE) 协议开源；不是 Sony 或其他电视厂商的官方产品。字体采用独立的 [SIL Open Font License](launcher/FONT-LICENSE-OFL.txt)。

- 源码与反馈：[zzusec/tv-desktop](https://github.com/zzusec/tv-desktop)
- APK 下载与校验文件：[Releases](https://github.com/zzusec/tv-desktop/releases/latest)
- 旧系统适配与实测记录：[docs/OLD-TVS.md](docs/OLD-TVS.md)

## 适用范围与功能

- Android 4.4 / API 19 起可安装；不适用于 Tizen、webOS 或其他非安卓系统。Fire TV 等限制第三方 HOME 的系统不能据此保证可接管桌面。
- 本分支已在 Sony BRAVIA 4K 2015 / Android 7 实机验证。Android 4.4 与其他品牌/固件仍需分别验证，不能把上游机型资料视为本分支逐台实测结果。
- 应用 dock、网格、输入源、网络状态、主题和遥控器焦点导航；可调节低性能设备的动效。
- 长按应用图标的 OK 可隐藏图标；长按顶部设置的 OK → 简洁桌面设置 → 隐藏的应用，可恢复图标。只隐藏桌面入口，不停用或卸载应用。
- 压缩顶部过大的动态留白，兼容旧系统字体与图标显示。
- 保留应用内正常返回；应用退出后回到默认桌面，不把返回键全局映射为 Home。应用自己的退出确认仍保留，个别应用的自定义返回行为需要单独验证。

## 下载与安全预览

从 Releases 下载 `tv-desktop-2.5.apk` 和 `SHA256SUMS`，先核对 SHA-256。当前 APK 的应用内版本仍为 2.5 / versionCode 6，包名保留为 `com.gathin.tvhome`，以保持现有配置与升级兼容；仓库名称不等于 Android 包名。

在电视开启 USB / 网络调试，并允许你自己电脑的调试授权。先保留原厂桌面，只安装并打开预览：

```bash
TV=192.168.1.50:5555  # 替换为你自己的电视地址
adb connect "$TV"
adb -s "$TV" install -r tv-desktop-2.5.apk
adb -s "$TV" shell am start -n com.gathin.tvhome/.MainActivity
```

这几条命令不会停用原厂桌面，也不会自动设置默认 HOME。确认显示、遥控器、设置入口正常后，再使用该固件支持的默认桌面设置；并非所有电视都会提供相同入口。

**不要直接套用停用清单，不要卸载唯一可用的桌面。** 如确需停用原厂桌面，必须先核对实际包名、备份、演练恢复，并设置独立的定时回退；原厂桌面已经停用时，升级 APK 前也必须有回退保护。断电重新上电和无需 ADB 的遥控器独立救援尚未在上述 Sony 设备上验证。

发布 APK 当前使用调试证书签名，供 ADB 侧载验证。自行构建会使用你本机的调试签名，不一定能覆盖安装已有版本；不要为解决签名冲突而贸然卸载正在使用的唯一桌面。ADB 私钥、签名私钥和设备备份不随源码发布。

## 从源码构建

需要 JDK 17、Android SDK platform 34 和 build-tools 34.0.0；Gradle Wrapper 已包含在仓库中。

```bash
git clone https://github.com/zzusec/tv-desktop.git
cd tv-desktop/launcher
./gradlew assembleRelease lintRelease testReleaseUnitTest --console=plain
cd ..
CLEANYOURTV_STRICT=1 python3 -m unittest discover -s tests -v
```

APK 位于 `launcher/app/build/outputs/apk/release/app-release.apk`。`JAVA_HOME` 和 `ANDROID_HOME` 应指向你本机的 JDK 与 SDK，不要提交 `launcher/local.properties`、ADB key、keystore 或电视状态备份。

当前适配版本通过 64 项 JVM、18 项 Python、release 构建与 lint；lint 仍有既有警告。CI 同时检查构建、测试和 450 KiB 的 APK 体积预算。发现问题时请附机型、Android/固件版本和复现步骤，不要公开令牌、序列号或 ADB 密钥。

## 上游项目资料

下方保留 CleanYourTV 的原始介绍、机型索引与工具说明，来源链接、统计徽标和支持按钮均指向上游作者。不同品牌的示例并不代表可直接用于你的电视；尤其是 debloat 与切换 HOME 操作，应先逐项核验，而不是整份执行。

---

<p align="center">
  <a href="https://cleanyour.tv"><img src="https://raw.githubusercontent.com/Gathin23/cleanyourtv/main/docs/banner.png" alt="CleanYourTV. Your TV, minus the ads." width="800"></a>
</p>

<p align="center">
  <a href="https://cleanyour.tv">cleanyour.tv</a> ·
  <a href="PROMPT.md">The prompt</a> ·
  <a href="examples/">68 TV lists</a> ·
  <a href="SHOWCASE.md">15 built on it</a> ·
  <a href="https://x.com/gathint/status/2111379569030939102">The tweet</a>
</p>

<p align="center">
  <a href="https://github.com/Gathin23/cleanyourtv/stargazers"><img src="https://img.shields.io/github/stars/Gathin23/cleanyourtv?style=flat&cacheSeconds=1800&color=cfffcf&labelColor=15181e" alt="Stars"></a>
  <a href="https://github.com/Gathin23/cleanyourtv/pulls?q=is%3Amerged"><img src="https://img.shields.io/github/issues-pr-closed/Gathin23/cleanyourtv?style=flat&cacheSeconds=1800&label=merged%20PRs&color=cfffcf&labelColor=15181e" alt="Merged PRs"></a>
  <a href="https://github.com/Gathin23/cleanyourtv/network/members"><img src="https://img.shields.io/github/forks/Gathin23/cleanyourtv?style=flat&cacheSeconds=1800&color=cfffcf&labelColor=15181e" alt="Forks"></a>
  <a href="https://github.com/Gathin23/cleanyourtv/graphs/contributors"><img src="https://img.shields.io/github/contributors/Gathin23/cleanyourtv?style=flat&cacheSeconds=1800&color=cfffcf&labelColor=15181e" alt="Contributors"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/licence-MIT-cfffcf?style=flat&labelColor=15181e" alt="MIT"></a>
  <a href="https://buymeacoffee.com/gathint"><img src="https://img.shields.io/badge/support-%E2%9D%A4-ff5e8a?style=flat&labelColor=15181e" alt="Support, buy me a coffee"></a>
</p>

Strip the bloatware from any Android TV or Google TV over Wi-Fi, and replace the ad-filled home
screen with a clean launcher that looks like an Apple TV. **No root, no cable, nothing uninstalled,
and a one-line restore if you change your mind.**

<h3 align="center">68 TVs tested or profiled &nbsp;·&nbsp; 25 brands &nbsp;·&nbsp; 14 languages &nbsp;·&nbsp; 424 KB launcher</h3>

![Before: stock Google TV home with a sponsored ad. After: the Clean Your TV launcher](docs/stock-vs-new-side.jpg)

> Vibe coded my parents' TV this afternoon! It was showing a sponsored ad before you could even get
> to Netflix. Gave Claude Code the model number, it found the TV on wifi, removed 25 Xiaomi packages
> and wrote a new launcher. Looks like an Apple TV now lol.
>
> [@gathint, 17 October 2026](https://x.com/gathint/status/2111379569030939102). The replies are where
> most of the TV lists here came from. The repo was called tv-vibehack until the 19th; old links work.

## How it works

Android TVs have the same debug bridge as Android phones. Switch on USB debugging in the TV's
developer options, allow your computer once, and the rest is standard Android tooling:

1. **Back up** the list of installed packages and settings.
2. **Disable** the manufacturer's telemetry, ad services, content rows and preloaded junk with
   `pm disable-user`. Nothing is uninstalled, so a restore script can undo every step.
3. **Install** a small native launcher and make it the default home screen.

The bloat differs by brand, so the prompt has your coding assistant discover it on your TV and ask
before touching anything you might use.

## Quick start

You need a computer on the same Wi-Fi as the TV and a coding assistant with shell access
(Claude Code or similar).

| | |
|---|---|
| **1. On the TV** | Settings → Device Preferences → About → press OK on **Build** seven times → back → Developer options → **USB debugging** on. If you see a Network or ADB debugging toggle, turn that on too. Keep the TV on. |
| **2. On your computer** | Paste [`PROMPT.md`](PROMPT.md) into your assistant and fill in your TV model. |
| **3. Follow along** | Accept "Allow USB debugging?" on the TV, then answer the checklist: which apps you use, which four go in the dock, whether to keep the voice assistant, Chromecast and updates. |

The assistant backs up, debloats, builds and installs the launcher, and checks each step with
screenshots pulled from the TV. With this repo as context it takes about fifteen minutes.

## What you get

- **A pitch-black home** with a dock of your four favourite apps in a rounded band, where Apple TV puts it.
- **A top shelf** naming the focused app in large type, with an ambient glow in a colour taken from its artwork.
- **The rest of your apps in a grid** that slides up when you press Down.
- **One floating glass highlight** that springs from tile to tile. The focused tile lifts and shows its name.
- **Three frosted buttons**: Inputs (HDMI ports, AV, Live TV), Wi-Fi or Ethernet with the network name, Settings.
- **A greeting, a thin clock and the date** in [Outfit](https://fonts.google.com/specimen/Outfit), and a matching Clock screensaver.
- **Themes** that change colours, wallpaper, dock and columns without a rebuild, and **module cards** that let other apps plug a tile in, only with your say-so.

And a TV that stops working against you: no sponsored rows, no "Discover" nags, no analytics
phoning home, no apps you never asked for.

| | |
|---|---|
| ![The top shelf names the focused app over an ambient glow in its colour](docs/focus-2-up-netflix.png) | ![The Inputs popup: HDMI ports with what is connected, AV and Live TV](docs/inputs-menu.jpg) |
| *Top shelf and ambient glow* | *Inputs: HDMI with connection state, AV, Live TV* |
| ![The grid with the glass highlight on a tile and its name beneath](docs/focus-1-fancode.png) | ![The Clock screensaver: a thin clock drifting over the glow](docs/clock-screensaver.png) |
| *The grid, one glass highlight, the tile's name* | *Clock screensaver, drifts so nothing burns in* |
| ![The aurora theme: a wallpaper and tinted pills, pushed without a rebuild](docs/theme-aurora.jpg) | ![Six columns and a six-app dock from a theme's config.json](docs/theme-six-columns.jpg) |
| *Aurora theme, pushed with `tools/theme.sh`* | *Six columns and a six-app dock, same launcher* |

## Supported TVs

Anything running **Android TV** or **Google TV**. Samsung (Tizen) and LG (webOS) run different
systems; see [SHOWCASE.md](SHOWCASE.md) for a webOS port. **Fire TV** takes the debloat half only,
since Fire OS will not hand Home to a third-party launcher. A few China-market TCL sets refuse to
install any launcher at all; their lists say so in the header.

Contributor lists and provisional profiles (marked unverified), one folder per model in [`examples/`](examples/):

**TCL** 14 · **Xiaomi** 15 · **Sony** 10 · **Philips** 4 · **Hisense** 3 · **CHiQ** 2 · **Skyworth** 2 ·
AiPro2K · Amazon Fire TV Stick · BenQ · Chromecast with Google TV · Hitachi · Insignia Fire TV ·
NVIDIA Shield · OnePlus · Panasonic · PRISM+ · RCA · Redmi · SANYO · Thomson · Toshiba Fire TV · Vu ·
Stellamore and XGIMI projectors

<details>
<summary><strong>The full list</strong></summary>

| TV | OS |
|---|---|
| AiPro2K (Expressluck, dongnae; Changhong AI PONT family) | Android TV 14 |
| Amazon Fire TV Stick 4K, 1st gen 2018 (AFTMM, mantis) | Fire OS 6.7.1.1 / Android 7.1 |
| BenQ E55-735 (Changhong AI PONT, DVB96BQ) | Google TV 14 |
| CHiQ AI PONT (Changhong GTVU65, dangni, MediaTek mt5867) | Google TV 14 |
| CHiQ AI PONT (DVB93CQ) | Android TV 11 |
| Chromecast with Google TV 4K (sabrina) | Google TV 14 |
| Hisense HiSmartTV A4 (xiaoyushan, MStar m7322), India | Android TV 9 |
| Hisense SmartTV 4K FFM (huangshan, MediaTek) | Android TV 10 |
| Hisense U75 (hengshan, MediaTek), Latin America | Google TV 12 |
| Hitachi 65QLEDSM20 (MTC, bangbae, MediaTek m7322), Australia | Android TV 11 |
| Insignia 55" F30 4K Fire TV (NS-55F301NA25, AFTDCT31, duckie) | Fire OS 7 / Android 9 |
| NVIDIA SHIELD TV Pro 2019 (P2897, mdarcy) | Android TV 11 |
| OnePlus TV (D00, Dubai) | Google TV 14 |
| Panasonic 4KTV-JUP (Jupiter_PA, MediaTek) | Android TV 11 |
| Philips 50PUS8517/12 (TP Vision, PH1M_EA_9970A, MediaTek MT5895), Europe | Android TV 11 |
| Philips 55PUS7503/12 (TP Vision TPM171E, PH7M_EU_5596, MediaTek MT5891), Europe | Android TV 8.0 |
| Philips R8G Google TV (MOKA, whitefield, Realtek RTD2851F), US | Google TV 14 |
| Philips Smart 4K TV (Skyworth, eleonas, MediaTek), India | Google TV 14 |
| PRISM+ Q65 Ultra 2026 QLED 4K AI Google TV (R10G, PRISMPLUS_FFM, MOKA, Realtek RTD2875 SoC, RTD6748 platform) | Google TV 14 |
| RCA AI PONT SA (Changhong, redwood, ISDB92RC; also sold as realme 40-inch) | Android 14 |
| Redmi TV (MiTV-ANTL0, starwars) | Android 5.1.1 (not Android TV) |
| SANYO ATV R2 (SCBC, Realtek RTD285O) | Android TV 9 |
| Skyworth SWTV-21AE (sunnyvale, MediaTek) | Android TV 14 |
| Skyworth SWTV-24NA-4K NF (eleonas, MediaTek mt5896), US | Google TV 14 |
| Sony BRAVIA 4K AE2 (BRAVIA_AE2, MediaTek MT5897), North America | Google TV 14 |
| Sony BRAVIA 4K 2015 (SVP-DTV15, China) | Android TV 7.0 |
| Sony BRAVIA 4K GB (BRAVIA_ATV2_EU, BRAVIA_ATV2) | Android TV 9 |
| Sony BRAVIA 4K UR2 (BRAVIA_UR2_4K, MediaTek) | Android TV 10 |
| Sony BRAVIA 4K VH2 (KD-55X80J, BRAVIA_VH2, MediaTek), EU | Google TV 12 |
| Sony BRAVIA 9 II (K-65XR90M2, BRAVIA_AE3, MediaTek MT5838) | Google TV 14 |
| Sony BRAVIA BF1 (BRAVIA 2 II class, BRAVIA_BF1_4K_A, MediaTek) | Google TV 12 |
| Sony BRAVIA KD-55X85J (2021, BRAVIA_VH2, MediaTek MT5895), South America | Google TV 12 |
| Sony BRAVIA KD-65X80J (2021, BRAVIA_VH2, MediaTek), India | Google TV 10 |
| Sony BRAVIA XBR-65X900E (2017, BRAVIA_ATV2) | Android TV 9 |
| Stellamore T950S projector (Droidlogic be30a3, Amlogic txhd2) | Android 13 |
| TCL 40S5400A (C06, C06_2K_GB, MediaTek MT9221, 1 GB) | Android TV 11 |
| TCL 43C645 (Smart TV Pro, Realtek RTD2851 / R51MT05, V8-R51MT05-LF1V652) | Google TV launcher on Android TV 11 |
| TCL 50EP680 (Europe, BeyondTV, Realtek rtd285o) | Android TV 11 |
| TCL 55" 4K Google TV, 2023 (G10, G10_4K_US_NF); also TCL 55P6K, Spain (G10_4K_GB_NF_32BIT) | Google TV 12 |
| TCL 55Q10G Pro (China, tcl_mt5879_cn, MediaTek MT9952) | Android TV 11 |
| TCL 65C725 (Smart TV Pro, BeyondTV4) | Android TV 11 |
| TCL 65C735 (Smart TV Pro, G05, MediaTek), France | Google TV 11 |
| TCL ak30a5 (China, Amlogic) | Android TV 9 |
| TCL BeyondTV (India) | Android TV 9 |
| TCL BeyondTV, EU/Nordic (Realtek RTD2851, V8-R851T02-LF1V606) | Android TV 9 |
| TCL BeyondTV2 (Latin America / Brazil, Realtek RTD2851) | Android TV 9 |
| TCL QM6K Pro, 2025 (G08, MediaTek tcl9618) | Google TV 14 |
| TCL Smart TV Pro (G05, G05_4K_US, MediaTek MT5889) | Google TV 11 |
| TCL UnionTV, Latin America (TCL-built like Thomson 32HE5800, Realtek RT2841) | Android TV 9 |
| Thomson 32HE5800 (TCL-built UnionTV, Realtek RT2841), Spain | Android TV 9 |
| Toshiba Fire TV (AFTHA004, hazel) | Fire OS 7 / Android 9 |
| Vu 4K (HVCL, lushan, MediaTek), India | Google TV 12 |
| XGIMI Horizon S Pro projector (XK03H, xgimigalileo, MediaTek) | Android TV 11 |
| Xiaomi Mi Box S (MIBOX4, oneday), streaming box | Android TV 9 |
| Xiaomi Mi Box S 2nd gen (MiTV-AFKR0, jaws), streaming box | Google TV 11 |
| Xiaomi Mi TV (MiTV-MOOQ0, croods) | Android TV 10 |
| Xiaomi Mi TV 2 (2014-2015, China; unverified) | Android 4.4.2 expected |
| Xiaomi Mi TV 4A (MiTV-MOOQ1) | Android TV 10 |
| Xiaomi MiTV-AESP0 stick (aquaman, 1 GB) | Android TV 9 |
| Xiaomi MiTV-ASTP1 (maverick), China firmware 2.40.641.0514.cn, 1 GB RAM, 720p | Android TV 9 (CN build) |
| Xiaomi MiTV-AXFR1 (rango, Amlogic, 1 GB), India | Android TV 11 |
| Xiaomi MiTV-AXSO0 (dangal; Mi TV 4C Pro 2019 / 4A Pro) | Android TV 9 |
| Xiaomi MiTV-MOOR2 (venom, MediaTek) | Google TV 11 |
| Xiaomi MiTV-MOSR4 (irobot, MediaTek) | Google TV 11 |
| Xiaomi MiTV-MSSP3 (machuca, MediaTek) | Android TV 11 |
| Xiaomi MiTV-MSSP3 (machuca, MediaTek), Android 9 firmware PI.7524 | Android TV 9 |
| Xiaomi TV A Pro 75" 2026 (MiTV-MZTU0, river, MediaTek) | Google TV 14 |
| Xiaomi TV Box S 3rd gen (MiTV-AFMU0, twilight, Amlogic), streaming box | Google TV 14 |

</details>

Your TV not here? The prompt discovers the bloat itself. Add your list afterwards and the next
person with your model skips that step: [CONTRIBUTING.md](CONTRIBUTING.md).

## Doing it by hand

Everything the assistant does, you can run yourself. The tools need Python 3 and nothing else.

```bash
python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt
export TV_HOST=192.168.1.50                                   # your TV's IP address

tools/venv/bin/python -I tools/tv.py --probe                 # model, Android version, firmware
tools/venv/bin/python -I tools/tv.py 'pm list packages'      # one shell command on the TV
tools/disable.sh examples/xiaomi-mi-tv-4a/debloat.txt        # apply a debloat list
tools/restore.sh examples/xiaomi-mi-tv-4a/debloat.txt        # undo it, stock launcher back
TV_HOST=192.168.1.50 launcher/build-and-install.sh           # build the launcher and make it Home
tools/theme.sh themes/aurora                                 # push a theme; --reset removes it
```

| Tool | What it does |
|---|---|
| [`tools/tv.py`](tools/tv.py) | Pure-Python ADB client: shell, batch, push, pull. Works where the stock `adb` binary is blocked. |
| [`tools/b64push.py`](tools/b64push.py) | Uploads large files such as APKs in base64 chunks and verifies the checksum. |
| [`tools/disable.sh`](tools/disable.sh), [`restore.sh`](tools/restore.sh) | Apply or undo a debloat list. |
| [`tools/theme.sh`](tools/theme.sh) | Push a folder from [`themes/`](themes/) to the launcher and restart Home. |
| [`launcher/build-and-install.sh`](launcher/build-and-install.sh) | Builds a shrunk release (about 300 KB), installs it, switches Home, and rolls back if Home does not land on it. |

Debloat list paths passed to `disable.sh` and `restore.sh` are relative to the directory you run
from, so your own lists can live outside the repository. Absolute paths also work.

Every script has a PowerShell twin next to it. On Windows with Android Studio installed nothing
else is needed:

```powershell
python -m venv tools\venv; tools\venv\Scripts\python.exe -m pip install -r tools\requirements.txt
powershell -ExecutionPolicy Bypass -File launcher\build-and-install.ps1 -TvHost 192.168.1.50
powershell -ExecutionPolicy Bypass -File tools\disable.ps1 -TvHost 192.168.1.50 examples\xiaomi-mi-tv-4a\debloat.txt
```

The install scripts ask the TV which launcher owns Home and only disable one they recognise
(`tvlauncher` or `launcherx`); pass `STOCK_LAUNCHER=<package>` (`-StockLauncher`) to override, or
`none` to leave it alone.

## The launcher

A small native app (`com.gathin.tvhome`, about 400 KB installed) in plain Java with AndroidX
RecyclerView and nothing heavier. Built for sets with 1 GB of RAM: no blur, no video, no network, no
background services. It runs from **Android 4.4 (API 19)** up, which covers older sets that are not
Android TV at all: the Xiaomi Mi TV 2 (Android 4.4, MIUI TV) and the Sony BRAVIA 4K 2015
(Android 7.0). Everything newer than API 19 is used behind a version check, so a modern TV behaves
exactly as before. It needs JDK 17 and Android SDK platform 34 to build; on macOS,
`brew install openjdk@17 android-commandlinetools` and `sdkmanager "platforms;android-34" "build-tools;34.0.0"`. On Linux
the install script finds `~/Android/Sdk` and the `java` on your PATH.

What the old-TV support changes, and why:

- **Fonts** are static instances of Outfit in `assets/fonts`, applied in code from `android:tag="font:light"` (`Fonts.java`). `android:fontFamily="@font/..."` needs API 26, and Android 7 draws the default weight of a variable font whatever the file asks for.
- **Icons** use correctly scaled PNGs per density below API 21, and the original vectors in `drawable-anydpi-v21` on newer devices. The colour selectors moved to `res/color`, since `android:textColor="@drawable/..."` is only a colour on newer versions.
- **Network and TV inputs** go through `Platform`/`PlatformNet`/`PlatformTv`: `NetworkCallback`, `NetworkCapabilities`, `TvInputManager` and `getLeanbackLaunchIntent` are all API 21, and Android 4.4 has no such classes at all. The two API 21 classes are only loaded after the version check.
- **The app grid** merges `CATEGORY_LAUNCHER` with `CATEGORY_LEANBACK_LAUNCHER`. MIUI TV and sideloaded apps often declare only the former, and an "empty means fall back" test never fires because the launcher itself declares a leanback entry.
- **Install script**: Android 5 and older have no `cmd` binary, so `build-and-install.sh` uses `pm disable-user` plus the HOME intent there instead of `cmd package set-home-activity`.

The full table of what changed and why is in [`docs/OLD-TVS.md`](docs/OLD-TVS.md).

- **Configure** in [`config.xml`](launcher/app/src/main/res/values/config.xml): dock order, hidden apps, the Live TV package, columns (tiles shrink from 5 up), rows under the dock, an optional VPN app for the network pill, and `reduced_motion` for TVs whose GPU cannot keep up.
- **On-TV launcher settings:** click Settings for the TV's system settings, or hold OK on Settings and choose Launcher settings to set 4, 5, 6 or 7 apps per row, turn animations on or off, and opt into advanced effects. The advanced option adds tile lift, icon zoom and smooth ambient-colour transitions; it may use more memory. Use Follow theme to clear on-TV layout and motion choices. Pushing or resetting a theme also clears these choices, but keeps your hidden-icon list.
- **Hide unwanted icons:** hold OK on an app tile, then choose Hide icon. App info remains in the same menu. To restore icons, hold OK on Settings, choose Launcher settings, open Hidden apps, select the apps and choose Restore selected. This hides tiles (including dock entries and module cards), not apps: nothing is uninstalled or disabled. The hidden list survives launcher restarts, in-place APK updates and layout/theme resets. Apps hidden by `config.xml` or a theme's `hidden_apps` still follow that configuration and are not listed as user-hidden apps here.
- **Change the default home:** Launcher settings and the optional Default-home tile (`show_home_settings_shortcut`) open Android's home picker. Choose the original launcher to stop using CleanYourTV as Home, or CleanYourTV to return. The original launcher must be enabled. If the install script disabled it, use the restore script below first. Some TVs provide no home picker.
- **Themes** set the same keys plus a background colour and a wallpaper from a `config.json` pushed to the TV, no rebuild. Three ship in [`themes/`](themes/), documented there.
- **Language** follows the TV: English, German, Spanish, Catalan, French, Russian, Serbian (Latin), Polish, Portuguese, Turkish, Arabic, Hebrew, Simplified Chinese, Thai. Android 13+ can set it for the launcher alone: `tools/tv.py 'cmd locale set-app-locales com.gathin.tvhome --locales ca'`.
  Hebrew places the menu buttons on the left and the date and clock on the right, with the greeting above the clock. The Clock screensaver right-aligns its date and time, and Hebrew dates include the month prefix ב (for example, 19 באוקטובר). Hebrew dock and app-grid rows start on the right, with matching left/right remote navigation. The Hebrew input selector places icons and labels on the right, status on the left, and aligns the dropdown with the Inputs button’s right edge. Other languages keep their original layouts.
  Russian includes the module-card approval and management screens and the VPN-off badge.
- **Module cards** let any TV app declare its own tile (label, caption, glow). The launcher asks you once per app, Keep hidden by default, and remembers the answer for that app's signing key and card name. Hold OK on Settings and choose Module cards to change your mind. How to make one: [`docs/modules.md`](docs/modules.md); the contract: [`docs/specs/card.md`](docs/specs/card.md).
- **Clock screensaver**: a drifting clock over the glow, so nothing burns in. Pick "Clock" in the TV's screensaver settings, or over ADB:
  `tools/tv.py 'settings put secure screensaver_components com.gathin.tvhome/.ClockDream; settings put secure screensaver_enabled 1'`
- **Remote keys**: an optional accessibility service keeps the remote's Input button working on Google TV (`INPUT_KEY_SERVICE=1` at install), another fixes sticks whose Home key sends ALL_APPS and opens Settings from the remote's gear button, even one Android does not map (`HOME_KEY_SERVICE=1`; scancodes and keycodes are the constants at the top of `HomeKeyService.java`, because `integer-array` resources need API 21).
- **Navigation** is deliberate: Up and Down move one row, the top buttons never fall through to tiles and Down from them returns to the dock tile you came from, the last focused app is remembered, and installing or removing an app refreshes the tiles while Home is open.
- **Artwork**: app banners load at xhdpi or better, so low-density TV UIs do not downsample them before the focus zoom.

## Restoring your TV

Keep the list you applied. The restore script re-enables exactly those packages, puts the stock
launcher back as Home and resets the animation scales:

```bash
TV_HOST=192.168.1.50 tools/restore.sh examples/<your tv>/debloat.txt                                    # Android TV
TV_HOST=192.168.1.50 tools/restore.sh examples/<your tv>/debloat.txt com.google.android.apps.tv.launcherx  # Google TV
```

It leaves the new launcher installed (remove it from the TV's Settings → Apps if you want it gone)
and does not know about packages you disabled outside the list. To switch back later, run the
install script again; it works out which launcher to disable even when Home has fallen back to
Android's chooser. If the TV is unreachable, its own
Settings → Apps page can re-enable packages too.

## When something goes wrong

The full list of things that bit us is in [docs/TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md). The
three that matter most:

- **macOS blocks `adb connect`** ("No route to host") under Local Network privacy. `tools/tv.py` avoids the binary.
- **Disable the stock launcher only after the new one is installed and running**, or Home lands on a factory test app. The install script orders this for you.
- **On Google TV the setup wizard** (`com.google.android.tungsten.setupwraith`) can take Home and crash-loop the system after the stock launcher is disabled. Disable it too, and list it in your `debloat.txt` so restore brings it back. But check `dumpsys wifi | grep -o 'cname=[^ ]*'` first: if the Wi-Fi network was saved during setup it belongs to setupwraith, and disabling it deletes the network (re-join it in Settings first; `tools/disable.sh` skips setupwraith until you do). Stuck already? `tools/tv.py 'pm enable --user 0 com.google.android.apps.tv.launcherx'`.

## Questions people ask

**Is this safe? Can it brick the TV?** Nothing is flashed, rooted or deleted; packages are disabled
in the user profile. The risk is disabling something the TV leans on, which is why lists are
applied in small batches with a restore script ready. The TV's own Settings → Apps page can
re-enable packages even without the tools.

**What about firmware updates?** Most lists disable the update service, so updates arrive only when
you re-enable it. If one lands, the worst case is a package coming back and you re-run the script.

**Won't the apps break?** Streaming apps still update through the Play Store, which is left alone.
The launcher just lists what is installed and launches it with intents that have been stable for a decade.

**Can I do this for someone in another home?** Yes, over a private [Tailscale](https://tailscale.com)
link instead of the same Wi-Fi: either Tailscale on their TV, shared with you, or a computer or Apple
TV at their home routing to it. Never by opening the ADB port on their router. Step by step in
[docs/REMOTE.md](docs/REMOTE.md).

**Isn't ADB access a security hole?** It is one explicit "allow this computer" tap on the TV, the
same as developing an Android app. Network ADB is plaintext on port 5555, and on firmware with
`ro.adb.secure=0` any device on the LAN gets a shell, so close it when you are done:

1. On the TV: Developer options → turn **USB debugging** and **Network/ADB debugging** off, and
   revoke the USB debugging authorisations.
2. Reboot the TV, then confirm the port is gone from your computer: `nc -vz <tv ip> 5555` must fail.

The launcher keeps working without debugging.

**Can it block ads inside apps too?** Partly, with Android's Private DNS (Android 9 and newer). Point
it at an ad-blocking resolver such as [AdGuard DNS](https://adguard-dns.io) and every app's ad and
tracker domains stop resolving, system-wide, surviving reboots. The trade-off: that resolver then
sees every DNS query the TV makes.

```bash
tools/tv.py 'settings put global private_dns_specifier dns.adguard-dns.com'
tools/tv.py 'settings put global private_dns_mode hostname'
```

It does not remove YouTube ads, which come from the same servers as the videos. If an app stops
playing or the TV reports "No internet" (the resolver is down or unreachable), switch back with
`tools/tv.py 'settings put global private_dns_mode opportunistic'`.

**AirPlay?** Not from this repo. Chromecast built-in stays for Android phones. For iPhones,
[jqssun/android-airplay-server](https://github.com/jqssun/android-airplay-server) works; an install
script for it is in [PR #84](https://github.com/Gathin23/cleanyourtv/pull/84).

**Can I just download the APK?** Build it with the one-line script; it takes a minute and you
control what goes on your TV. No prebuilt binaries.

## Contributing

The most useful contribution is your TV's debloat list, so the next person with that model does
not have to rediscover it. Launcher improvements and sharper prompt wording are welcome too.
[CONTRIBUTING.md](CONTRIBUTING.md) has the format and the testing we ask for. Built something on
top, a launcher, a port, a theme? Add it to [SHOWCASE.md](SHOWCASE.md), where eleven projects already
live: a webOS port for LG, a Stremio shelf, a Hisense restyle, Fire TV launchers, an AirPlay installer.

The people whose work is in this repo:

<a href="https://github.com/Gathin23/cleanyourtv/graphs/contributors"><img src="https://contrib.rocks/image?repo=Gathin23/cleanyourtv&columns=16" alt="Contributors"></a>

<details>
<summary><strong>Star history</strong></summary>

<a href="https://star-history.com/#Gathin23/cleanyourtv&Date"><img src="https://api.star-history.com/svg?repos=Gathin23/cleanyourtv&type=Date" alt="Star history chart" width="640"></a>

</details>

## Licence

MIT, see [`LICENSE`](LICENSE). The Outfit typeface is under the SIL Open Font License, see
`launcher/FONT-LICENSE-OFL.txt`.
