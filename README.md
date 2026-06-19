<div align="center">

<img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white" />
<img src="https://img.shields.io/badge/Language-Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" />
<img src="https://img.shields.io/badge/Min%20SDK-23%20(Android%206.0)-blue?style=for-the-badge" />
<img src="https://img.shields.io/github/v/release/Dhruv-1309/CodeCountdown?style=for-the-badge&color=success&label=Latest%20Release" />

<br/><br/>

# 📅 CodeCountdown

**Never miss a competitive programming contest again.**

CodeCountdown is a native Android app that aggregates upcoming and live contests from all major competitive programming platforms into one clean, minimal interface — with smart notifications so you're always prepared.

[**⬇️ Download Latest APK**](https://github.com/Dhruv-1309/CodeCountdown/releases/latest/download/CodeCountdown.apk) &nbsp;·&nbsp; [**📋 View All Releases**](https://github.com/Dhruv-1309/CodeCountdown/releases)

</div>

---

## ✨ Features

### 🏆 Multi-Platform Contest Tracker
Track contests across **4 major platforms** from a single screen:

| Platform | Live | Upcoming | Reminders |
|----------|------|----------|-----------|
| ![Codeforces](https://img.shields.io/badge/Codeforces-1F8ACB?style=flat&logo=codeforces&logoColor=white) | ✅ | ✅ | ✅ |
| ![LeetCode](https://img.shields.io/badge/LeetCode-FFA116?style=flat&logo=leetcode&logoColor=white) | ✅ | ✅ | ✅ |
| ![CodeChef](https://img.shields.io/badge/CodeChef-5B4638?style=flat&logo=codechef&logoColor=white) | ✅ | ✅ | ✅ |
| ![AtCoder](https://img.shields.io/badge/AtCoder-222222?style=flat&logoColor=white) | ✅ | ✅ | ✅ |

### 📱 Home Screen
- **Live Now** — See contests currently in progress with a live indicator
- **Upcoming Today** — View contests starting later today
- **Day Selector** — Swipe through a 7-day chip bar to browse future contests
- **Platform Filter** — Toggle platforms to focus on what you care about
- **Smart Empty State** — Context-aware messages when no contests are scheduled or all contests have ended for the day

### 🗓️ Schedule View
- Full contest schedule grouped by date
- Tap any contest to open it in your browser

### 🔔 Reminders
- Set per-contest reminders at your preferred time before start
- Notifications fire even when the app is closed
- Reminders persist across device reboots

### ⚙️ Settings
- **Light / Dark / System** theme
- Toggle individual platforms on or off
- **OTA Update System** — Check for updates directly in-app

### 🔄 OTA Updates
CodeCountdown includes a built-in over-the-air update system:
- Automatically checks for new versions on launch (at most once per 24 hours)
- Shows a Material 3 dialog with version info and release notes
- Downloads the update in the background via Android's Download Manager
- Verifies the APK with **SHA-256** before installation
- **Stable** and **Beta** update channels available in Settings → App Updates

---

## 📲 Installation

CodeCountdown is distributed as a standalone APK — **no Play Store required**.

### One-time setup (Android 12+ only)
Before installing, enable sideloading from your browser:

1. Open your phone's **Settings**
2. Go to **Apps → Special app access → Install unknown apps**
3. Select your browser and toggle **Allow from this source**

### Download & Install
1. Open this link **on your phone's browser**:
   ```
   https://github.com/Dhruv-1309/CodeCountdown/releases/latest/download/CodeCountdown.apk
   ```
2. Tap the downloaded file in your notifications or **Files → Downloads**
3. Tap **Install**

> After the first install, all future updates are handled automatically by the in-app OTA system — no manual steps needed.

---

## 🏗️ Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin |
| UI | Native Android Views · Material Design 3 |
| Architecture | MVVM (ViewModel + LiveData) |
| Networking | Retrofit 2 · Gson |
| Async | Kotlin Coroutines |
| Updates | Android DownloadManager · FileProvider |
| Notifications | NotificationManager · AlarmManager |
| Min SDK | API 23 (Android 6.0 Marshmallow) |
| Target SDK | API 35 (Android 15) |

---

## 🔄 Update Channels

| Channel | Description |
|---------|-------------|
| **Stable** *(default)* | Production-ready releases with all features tested |
| **Beta** | Pre-release builds with new features — may have rough edges |

Switch channels anytime in **Settings → APP UPDATES → Update Channel**.

---

## 📁 Project Structure

```
app/src/main/java/com/example/contesttracker/
├── MainActivity.kt              # Main navigation host
├── ContestViewModel.kt          # Data layer (MVVM)
├── ContestAdapter.kt            # Home screen RecyclerView
├── ScheduleAdapter.kt           # Schedule / Reminders RecyclerView
├── ContestModel.kt              # Data models + Platform enum
├── ContestRepository.kt         # API repository
├── ContestTimeUtils.kt          # Time parsing & formatting
├── NotificationScheduler.kt     # Alarm scheduling
├── NotificationReceiver.kt      # Alarm broadcast receiver
├── BootReceiver.kt              # Re-schedule on device reboot
└── update/
    ├── UpdateConfig.kt          # OTA configuration
    ├── GitHubUpdateService.kt   # GitHub Releases API
    ├── VersionComparator.kt     # Semantic version comparison
    ├── UpdatePreferences.kt     # Persistent update state
    ├── SecurityVerifier.kt      # SHA-256 APK verification
    ├── ApkDownloader.kt         # DownloadManager wrapper
    ├── ApkInstaller.kt          # FileProvider APK installer
    ├── DownloadCompleteReceiver.kt  # Download broadcast handler
    ├── UpdateDialog.kt          # Material 3 update dialog
    └── UpdateManager.kt         # OTA orchestrator
```

---

## 🔒 Security

- APK downloads are verified using **SHA-256** checksums published alongside each release
- All network communication uses **HTTPS**
- The APK installer uses Android's **FileProvider** — no `file://` URIs exposed
- No user data is sent to any external server

---

## 📝 Changelog

### [v1.2.1](https://github.com/Dhruv-1309/CodeCountdown/releases/tag/v1.2.1)
- Fixed installation on Android 12+ (package ID updated)

### [v1.2.0](https://github.com/Dhruv-1309/CodeCountdown/releases/tag/v1.2.0)
- Smart empty state for home screen
- Context-aware "No contests scheduled" / "You're all caught up!" messages

### [v1.1.0](https://github.com/Dhruv-1309/CodeCountdown/releases/tag/v1.1.0)
- Full OTA update system with SHA-256 verification
- Stable / Beta update channels
- App Updates card in Settings

---

## 👨‍💻 Author

**Dhruv B** — Designed & developed with ❤️

---

<div align="center">

© 2026 Dhruv B. All rights reserved.

</div>
