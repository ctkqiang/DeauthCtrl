# DeauthCtrl

**Android BLE Spam & Wi-Fi Disruption Tool for Security Research**

DeauthCtrl is a pure Android application (no root, no external hardware) that performs two types of denial-of-service attacks for authorized security testing in a controlled lab environment.

---

## LEGAL WARNING

**This application is for AUTHORIZED SECURITY RESEARCH and EDUCATIONAL PURPOSES only.**

- Disrupting networks or devices you **do not own** is **illegal** in most jurisdictions.
- BLE spam can crash Bluetooth stacks on nearby devices. Only test against devices you own.
- Wi-Fi beacon flooding may violate telecommunications regulations. Use only in an isolated lab.
- **The developer assumes NO LIABILITY for misuse or damage caused by this tool.**

**By building or using this app, you accept full responsibility for ensuring your use complies with all applicable laws.**

---

## Features

### 1. BLE Spam Module
- Uses `BluetoothLeAdvertiser` to broadcast spoofed BLE advertisement packets
- Pre-configured payload profiles mimicking real device types:
  - **Apple Continuity** (AirDrop, AirPods, Handoff)
  - **Microsoft Swift Pair** (Windows peripheral pairing)
  - **Google Fast Pair** (Pixel Buds, etc.)
  - **Samsung SmartThings** (Galaxy Buds, SmartTag, TV)
  - **Flipper-Style Spam** (randomized multi-vendor cycling)
  - **All Profiles** (cycles through all of the above)
- Configurable advertising interval (20ms – 100ms)
- Real-time transmission log with timestamps and payload summaries
- Start/stop control with foreground service

### 2. Wi-Fi Disruption Module (Experimental)
- Scan for nearby Wi-Fi networks (SSID, BSSID, signal strength)
- Select a target network to attack
- Creates an **evil-twin hotspot** with the same SSID as the target via `WifiManager.startLocalOnlyHotspot()`
- **Beacon flooding**: rapidly toggles the hotspot on/off to overwhelm clients scanning for that SSID, potentially causing temporary disconnections from the real AP
- Configurable flood duration (1–60 seconds)
- Graceful cleanup after flood completes
- Status display showing progress

---

## Limitations

### Wi-Fi Module (Critical)
- **This is NOT a true deauth attack.** Raw 802.11 frame injection is impossible on stock Android without root privileges and a monitor-mode-capable Wi-Fi chipset.
- The beacon flood technique is **experimental** and results vary significantly:
  - Some devices/Android versions throttle hotspot creation
  - The real AP must be on a different channel for any confusion effect
  - Many modern Wi-Fi stacks are resilient to beacon floods
  - Not all devices support `startLocalOnlyHotspot()`
- **This will NOT reliably disconnect clients from a well-configured modern AP.**

### BLE Module
- BLE advertising range is limited (~10–50m depending on hardware)
- Android limits the number of simultaneous advertisers
- Some Android versions throttle BLE advertising
- The spoofed advertisements are simplified; they won't perfectly mimic all aspects of real device advertisements

---

## Build Instructions

### Prerequisites
- **Android Studio** (Hedgehog or newer recommended)
- **JDK 17**
- **Android SDK 34** with build tools
- A physical Android device running **Android 6.0+** (API 23+)
  - Emulators do not support BLE advertising or Wi-Fi hotspot features

### Build Steps
1. Clone the repository:
   ```bash
   git clone <repo-url>
   cd DeauthCtrl
   ```

2. Open the project in Android Studio.

3. Sync Gradle and let dependencies download.

4. Connect your Android device with USB debugging enabled.

5. Build and run:
   ```
   ./gradlew installDebug
   ```

   Or use the **Run** button in Android Studio.

### Release Build
```bash
./gradlew assembleRelease
```
The signed APK will be at `app/build/outputs/apk/release/app-release.apk`.

---

## Project Structure

```
app/src/main/java/com/ctkqiang/deauthctrl/
├── MainActivity.kt              # Entry point, disclaimer + theme
├── DeauthApp.kt                 # Application class
├── manager/
│   ├── BleSpamManager.kt        # BLE advertisement spam logic
│   ├── BleSpamService.kt        # Foreground service for BLE spam
│   └── WifiDisruptManager.kt    # Wi-Fi scan + hotspot beacon flood
├── model/
│   ├── BlePayload.kt            # BLE profile enum + log entry
│   └── WifiNetwork.kt           # Wi-Fi network data class + states
├── viewmodel/
│   ├── BleSpamViewModel.kt      # BLE spam UI state
│   └── WifiDisruptViewModel.kt  # Wi-Fi disrupt UI state
└── ui/
    ├── theme/
    │   ├── Color.kt, Type.kt, Theme.kt
    ├── components/
    │   ├── DisclaimerDialog.kt  # First-launch legal dialog
    │   └── WarningBanner.kt     # Persistent warning banner
    └── screens/
        ├── MainScreen.kt        # Tab container
        ├── BleSpamScreen.kt     # BLE spam UI
        └── WifiDisruptScreen.kt # Wi-Fi disrupt UI
```

---

## Permissions Required

| Permission | Purpose |
|---|---|
| `BLUETOOTH` / `BLUETOOTH_ADMIN` | BLE advertising (API < 31) |
| `BLUETOOTH_ADVERTISE` | BLE advertising (API 31+) |
| `BLUETOOTH_CONNECT` | BLE device interaction |
| `BLUETOOTH_SCAN` | BLE scanning |
| `ACCESS_FINE_LOCATION` | BLE/Wi-Fi scanning (required by Android) |
| `ACCESS_WIFI_STATE` | Wi-Fi scanning |
| `CHANGE_WIFI_STATE` | Hotspot creation |
| `INTERNET` | Network access |
| `FOREGROUND_SERVICE` | Long-running BLE spam |

The app requests runtime permissions on first use. Location permission must be granted for both BLE and Wi-Fi scanning to work.

---

## Lab Testing Guide

### Testing BLE Spam
1. **Equipment needed**: A spare Android phone or BLE device (smartwatch, headphones) as the **target**.
2. Install DeauthCtrl on your **attack device**.
3. Put the target device in Bluetooth discovery/pairing mode.
4. In DeauthCtrl, select a payload profile (e.g., "Apple Continuity") and press **Start BLE Spam**.
5. Observe the target device: it may show phantom pairing requests, crash the Bluetooth stack, or become unresponsive.
6. Stop the spam and verify the target device recovers normally.

### Testing Wi-Fi Flood
1. **Equipment needed**: A spare Wi-Fi router as the **target AP**, and a client device (laptop/phone) connected to it.
2. Install DeauthCtrl on your **attack device**.
3. Press **Scan Networks** and select the target router's SSID.
4. Set duration to 10 seconds and press **Start Beacon Flood**.
5. Observe the client device: it may show a second network with the same SSID, or temporarily disconnect.
6. **Note**: This test may not produce visible results on all hardware. It is for demonstrating the concept, not reliable disruption.

---

## Tech Stack
- **Language**: Kotlin 2.0
- **UI**: Jetpack Compose (Material 3)
- **Architecture**: MVVM (ViewModel + StateFlow)
- **Async**: Kotlin Coroutines
- **Min SDK**: 23 (Android 6.0)
- **Target SDK**: 34 (Android 14)

## License

This project is provided for educational and research purposes. No license is granted for illegal use.
