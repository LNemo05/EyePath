# EyePath

**简体中文** | [English](README.en.md)

开源 Android 应用：在你**边走边玩手机**时进行干预提醒。

## 功能

- **行走检测**：基于计步传感器（最后一步后超时则判定停止行走）
- **三种干预模式**
  - **温和（MILD）** — 通知提醒
  - **普通（NORMAL）** — 全屏遮罩 + 震动（需「显示在其他应用上层」）
  - **狂暴（RAGE）** — 立即锁屏（需设备管理员）
- **按应用策略**：继承全局 / 温和 / 普通 / 狂暴 / 白名单
- **前台应用识别**：无障碍服务（可选「使用情况访问」作备用）
- **保活与恢复**：开机、应用更新、无障碍重连、快捷设置磁贴等多入口恢复
- **数据仅本地**：设置（DataStore）与统计（Room）；无账号、不上云

## 隐私

EyePath 数据保存在本机：

- **不**收集账号、通讯录、短信、位置
- **不**上传行走状态、前台包名或统计数据
- 无障碍仅用于识别当前前台应用，以便判断是否干预
- 设备管理员**仅**用于狂暴模式强制锁屏（不会擦除数据或修改密码）

## 下载安装

日常使用请从 **[GitHub Releases](https://github.com/LNemo05/eyepath/releases/latest)** 下载最新 APK 安装。

## 从源码构建（开发者）

需要 Android SDK。在仓库根目录创建 `local.properties`，写入 `sdk.dir=...`。

```powershell
# Windows
.\gradlew.bat :app:assembleDebug
```

```bash
# macOS / Linux
./gradlew :app:assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`

## 权限

在 Android 10+ 真机或模拟器上：

1. 安装 APK 后打开应用。
2. 打开 **权限** 页，按需授予：
   - **无障碍**（EyePath 服务）— 前台应用检测（必需）
   - **通知**（Android 13+ 另需运行时权限）
   - **活动识别** — 行走 / 计步
   - **显示在其他应用上层** — 普通模式遮罩
   - **设备管理员** — 仅狂暴模式锁屏
   - **使用情况访问**（可选）— 次要前台包名来源
   - **忽略电池优化**（推荐）— 降低厂商杀后台概率
   - **`WRITE_SECURE_SETTINGS`**（可选，需 ADB）— 无障碍修复辅助
3. **更改权限前请先停止行走**（配置权限不会豁免守护逻辑）。

## 架构（MVP）

- 前台守护服务 + 守护开启时的多入口恢复
- 无障碍服务识别前台包名（UsageStats 回退）
- 基于步数的行走检测
- 温和 / 普通 / 狂暴干预 + 权限门控
- DataStore 设置 + Room 策略与聚合统计
- Jetpack Compose 界面（首页、应用、设置、统计、权限）

## 致谢 / 第三方参考

EyePath 的保活架构在实现时参考自：

- **[GKD](https://github.com/gkd-kit/gkd)**（[gkd-kit/gkd](https://github.com/gkd-kit/gkd)）— 许可证为 **[GPL-3.0](https://github.com/gkd-kit/gkd/blob/main/LICENSE)**

感谢 GKD 作者与贡献者开源高质量、可审计的 Android 无障碍相关代码。

其他依赖（AndroidX、Jetpack Compose、Room、DataStore、Kotlin 协程等）通过 Gradle 引入，遵循各自许可证。

## 友情链接

- [Linux.do](https://linux.do/) — 新的理想型社区

## 许可证

```
EyePath
Copyright (C) 2026 EyePath contributors

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <https://www.gnu.org/licenses/>.
```

全文见 [LICENSE](LICENSE)（GNU GPL v3）。

保活设计有部分源自或受 GKD 启发，GKD 同为 GPL-3.0。再分发 EyePath（源码或二进制）时须遵守 GPL-3.0，包括提供对应源码。

## 免责声明

- 检测到行走时，干预可能打断正常使用手机。
- 狂暴模式可锁屏；卸载前如需请先在系统设置中**撤销设备管理员**。
- 厂商省电 / 杀进程策略仍可能导致后台停止；保活提高恢复能力，**不保证**进程永不被杀。
- 本项目按「现状」提供，面向个人安全向使用；如何配置与使用由你自行负责。
