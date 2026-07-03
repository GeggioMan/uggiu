# uggiu - Heart Rate & Crisis Monitor

**uggiu** is a modern Android application specialized in real-time heart rate monitoring and automatic detection of critical situations using Bluetooth Low Energy (BLE) smartbands.

## 🚀 Key Features

*   **Automatic Discovery (Zero-Click)**: The app starts searching for devices as soon as it's opened or brought to the foreground. Simply select your band from the list to start.
*   **Advanced Crisis Monitoring**:
    *   **Intelligent Detection**: A crisis is recorded only if the heart rate exceeds the threshold (X) for a minimum duration (Y).
    *   **Real-time Counter**: A live stopwatch shows the duration of the active crisis, retroactively including the initial observation time.
    *   **Notifications and Alarms**: Sound feedback or persistent vibration during critical events, even in the background.
*   **Dynamic Chart (Sliding Window)**: Visualization of the heart rate trend with selectable time scales (1h, 4h, 8h, 12h) that scroll maintaining the correct time proportion.
*   **Event History**: Dedicated local database for recording individual crises (start, end, duration, and peak BPM).
*   **Device Diagnostics**: 
    *   Display of the Name and MAC Address of the connected sensor.
    *   Battery Badge and Proximity Indicator (RSSI) to monitor signal quality.
    *   Smart Automatic Reconnection in case of signal loss.
*   **Contact Detection**: Advanced management of signal absence (band not worn) using official BLE flags to avoid "phantom heartbeats".
*   **Multi-language Support**: Full support for Italian and English.

## 🛠 Tech Stack

*   **Language**: Kotlin 2.x
*   **UI**: Jetpack Compose (Material 3) with reactive architecture.
*   **Database**: Room with KSP support (`CrisisRecord` table for history).
*   **Networking**: Android Bluetooth LE API (Standard GATT Heart Rate Service).
*   **Background**: Foreground Service with persistent notification for 24/7 monitoring.

## 📦 Installation and Distribution

### APK Generation
You can generate the updated executable directly via Gradle using the custom task:
```bash
./gradlew generateUggiuApk
```
The APK will be available in: `apk/uggiu.apk`

### Requirements
*   Android 8.0 (API 26) or higher.
*   Permissions: Bluetooth (Scan/Connect), Body Sensors, Notifications.

## 📂 Project Structure

*   `MainActivity.kt`: Interactive dashboard, permission management, and history navigation.
*   `service/WorkoutService.kt`: Core engine for data recording and alarm logic.
*   `ble/BLEHeartRateClient.kt`: Low-level GATT manager with packet parsing and RSSI management.
*   `data/`: Data persistence with Room (`CrisisRecord` and `WorkoutSession`).

## 📄 License

This project is distributed for personal and demonstrative use.
