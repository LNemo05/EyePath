# EyePath

[简体中文](README.md) | **English**

Open-source Android app that intervenes when you use your phone while walking.

## Features

- **Walking detection** via step sensors (last-step timeout decides when walking ends)
- **Three intervention modes**
  - **MILD** — notification reminder
  - **NORMAL** — overlay + vibration (needs display-over-other-apps)
  - **RAGE** — force lock screen (needs device admin)
- **Per-app policies** (inherit global / mild / normal / rage / whitelist)
- **Foreground package tracking** via Accessibility (+ optional Usage Access fallback)
- **Keepalive / recovery** after boot, package update, accessibility reconnect, and Quick Settings tile
- **Local-only data** — settings (DataStore) and stats (Room); no account, no cloud upload

## Privacy

EyePath keeps data on-device:

- Does **not** collect accounts, contacts, SMS, or location
- Does **not** upload walking state, foreground package names, or stats
- Accessibility is used only to detect the current foreground app for intervention decisions
- Device admin is used **only** for RAGE mode force-lock (not wipe / password change)

## Download

For normal use, install the latest APK from **[GitHub Releases](https://github.com/LNemo05/eyepath/releases/latest)**.

## Build from source (developers)

Requires Android SDK. Create `local.properties` in the repo root with `sdk.dir=...`.

```powershell
# Windows
.\gradlew.bat :app:assembleDebug
```

```bash
# macOS / Linux
./gradlew :app:assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

## Permissions

On Android 10+ device or emulator:

1. Install the APK and open the app.
2. Open the **Permissions** tab and grant what you need:
   - **Accessibility** (EyePath service) — foreground app detection (required)
   - **Notifications** (+ runtime permission on Android 13+)
   - **Activity recognition** — walking / steps
   - **Display over other apps** — NORMAL mode overlay
   - **Device admin** — RAGE mode lock only
   - **Usage access** (optional) — secondary foreground package source
   - **Battery optimization exemption** (recommended) — reduce OEM background kills
   - **`WRITE_SECURE_SETTINGS`** (optional, ADB) — accessibility repair helper
3. **Stop walking** before changing permissions (permission setup is not exempt from guard logic).

## Architecture (MVP)

- Foreground guard service + multi-entry recovery when guard is enabled
- Accessibility Service for foreground package (UsageStats fallback)
- Step-based walking detection
- MILD / NORMAL / RAGE interventions with permission gating
- DataStore settings + Room policies and aggregate stats
- Jetpack Compose UI (Home, Apps, Settings, Stats, Permissions)

## Acknowledgements / third-party reference

EyePath's keepalive architecture was implemented with reference to:

- **[GKD](https://github.com/gkd-kit/gkd)** ([gkd-kit/gkd](https://github.com/gkd-kit/gkd)) — licensed under **[GPL-3.0](https://github.com/gkd-kit/gkd/blob/main/LICENSE)**

Thanks to the GKD authors and contributors for publishing a high-quality, auditable Android accessibility codebase.

Other dependencies (AndroidX, Jetpack Compose, Room, DataStore, Kotlin coroutines, etc.) are used under their respective licenses via Gradle.

## Friend links

- [Linux.do](https://linux.do/) — 新的理想型社区

## License

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

Full text: [LICENSE](LICENSE) (GNU GPL v3).

Portions of the keepalive design are derived from or inspired by GKD, which is also GPL-3.0. When you redistribute EyePath (source or binary), you must comply with GPL-3.0, including offering corresponding source.

## Disclaimer

- Interventions can interrupt normal phone use while walking is detected.
- RAGE mode can lock the screen; revoke **Device admin** in system settings before uninstall if needed.
- OEM battery / process killers may still stop background work; keepalive improves recovery but is not a guarantee.
- This project is provided as-is for personal safety-oriented use; you are responsible for how you configure and use it.
