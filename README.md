# Watch VoIP Notifier

> **Watch VoIP Notifier** is an open-source Android & Wear OS companion system designed to bridge third-party VoIP incoming calls (specifically **Zalo** and **Facebook Messenger**) directly to Wear OS smartwatches (tested with Samsung Galaxy Watch6 40mm paired with OnePlus Ace 5 / ColorOS).

---

## 📌 Problem & Motivation

On non-Samsung Android devices (e.g., OnePlus, OPPO, Xiaomi), Wear OS smartwatches typically only sync native cellular GSM calls via Bluetooth Hands-Free Profile (HFP). Third-party VoIP calls such as Zalo and Facebook Messenger:
* Often fail to notify the watch or get delayed/missed.
* Do not trigger native incoming call screens or continuous ringing/vibration on Wear OS.
* Are often suppressed by aggressive OEM battery managers (ColorOS / OxygenOS / HyperOS).

**Watch VoIP Notifier** bridges this gap by capturing incoming VoIP notifications on the handheld device, transmitting real-time call signaling to the watch via Google Play Services Wearable Data Layer, and enabling call management (ringing, caller identification, call acceptance/rejection) right on the wrist.

---

## 🚀 Key Features

* **Real-time Call Detection**: Intercepts incoming VoIP call events from Zalo (`com.zing.zalo`) and Facebook Messenger (`com.facebook.orca`) using `NotificationListenerService`.
* **Wear OS Full-Screen Incoming Call UI**: Displays an incoming call screen built with Jetpack Wear Compose.
* **Haptic Ringing**: Continuous vibration pattern on the smartwatch during ringing.
* **Caller Identification**: Extracts and displays the caller's name and platform logo.
* **Call Actions**:
  * **Answer Call**: Triggers the notification answer action on the phone and directs audio to the smartwatch via Bluetooth HFP.
  * **Decline Call**: Dismisses the call notification immediately from the wrist.
* **Bluetooth HFP Audio Integration**: Leverages existing Bluetooth hands-free routing between the phone and Galaxy Watch6 for seamless two-way microphone and speaker communication.

---

## 🛠️ Architecture & Tech Stack

```
┌─────────────────────────────────┐                 ┌─────────────────────────────────┐
│           Phone App             │                 │          Wear OS App            │
│         (:app module)           │                 │         (:wear module)          │
├─────────────────────────────────┤                 ├─────────────────────────────────┤
│ • NotificationListenerService   │  Wearable Data  │ • WearableListenerService       │
│ • Incoming Call Parser          ├────────────────►│ • Full-screen Call Screen       │
│ • PendingIntent Call Controller │   MessageClient │ • Continuous Vibrator Alert     │
│ • Jetpack Compose UI            │◄────────────────┤ • Wear Compose UI               │
└─────────────────────────────────┘                 └─────────────────────────────────┘
                 ▲                                                   ▲
                 └─────────────── Bluetooth HFP ─────────────────────┘
                             (Two-way Call Audio & Mic)
```

* **Language**: Kotlin
* **Build System**: Gradle Kotlin DSL (`build.gradle.kts`) with Version Catalogs (`libs.versions.toml`)
* **UI**: Jetpack Compose (Phone) & AndroidX Wear Compose (Smartwatch)
* **Connectivity**: Google Play Services Wearable Data Layer (`MessageClient`, `CapabilityClient`)
* **Target Platforms**:
  * Phone: Android 13+ (API 33 - 37)
  * Watch: Wear OS 4+ (API 33 - 37)
* **CI/CD**: GitHub Actions automated APK compilation (`.github/workflows/build.yml`)

---

## 📂 Project Structure

```
watch_voip_notifier/
├── .github/
│   └── workflows/
│       └── build.yml              # CI workflow for building debug APKs
├── app/                           # Handheld Companion App module
│   ├── src/main/java/com/luca/watchvoipnotifier/
│   └── build.gradle.kts
├── wear/                          # Wear OS Smartwatch App module
│   ├── src/main/java/com/luca/wear/
│   └── build.gradle.kts
├── gradle/
│   ├── libs.versions.toml         # Version catalog
│   └── wrapper/
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

---

## ⚙️ Device Setup & Optimization (ColorOS / OnePlus)

To ensure notifications and background services are never killed:

1. **Battery Optimization**:
   * Settings → Battery → More settings → App battery management.
   * Locate **Zalo**, **Messenger**, **Galaxy Wearable**, and **Watch VoIP Notifier**.
   * Set all to **"Don't optimize"** / **"Allow background activity"**.
2. **Auto-launch**:
   * Settings → Apps → Auto-launch → Enable for all 4 apps.
3. **Notification Access**:
   * Settings → Apps → Special app access → Device & app notifications (Notification Access).
   * Grant access to **Watch VoIP Notifier**.

---

## 💻 Build & Development

### Prerequisites
* JDK 21+ (or Android Studio JBR)
* Android SDK (API 33+)
* Antigravity CLI or Android Studio

### Building from Command Line
```bash
# Build both Phone and Wear OS debug APKs
./gradlew assembleDebug

# Output APK locations:
# - app/build/outputs/apk/debug/app-debug.apk
# - wear/build/outputs/apk/debug/wear-debug.apk
```

### Installing via ADB
```bash
# Install Phone APK to OnePlus
adb -s <PHONE_SERIAL> install -r app/build/outputs/apk/debug/app-debug.apk

# Install Wear OS APK to Galaxy Watch6 (via Wi-Fi ADB)
adb -s <WATCH_IP:PORT> install -r wear/build/outputs/apk/debug/wear-debug.apk
```

---

## 📄 License
This project is licensed under the Apache 2.0 License.
