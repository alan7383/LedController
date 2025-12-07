# LED Controller 🎵💡

A modern, native Android application to control BLE LED strips with advanced music synchronization features. Built with **Jetpack Compose** and **Kotlin**.

## ✨ Features

* **Real-time Audio Visualization**: Analyzes microphone input to generate responsive light shows.
* **Music Sync**: Syncs LED pulses with the beat of your music.
* **Media Integration**: Displays currently playing track info and album art (Spotify, SoundCloud, etc.) directly in the app.
* **AMOLED Mode**: A "True Black" theme optimized to save battery on OLED screens.
* **Custom Presets**: Save your favorite static colors or scenes.
* **Background Service**: Keeps the light show running even when the screen is off (using a foreground service with Wakelock).

## 📱 Compatibility

This application is designed to communicate with generic Bluetooth Low Energy (BLE) controllers that use the **ELK / Triones** protocol.

These controllers are widely available and often sold under various brand names, including but not limited to:
* **Lotus Lantern**
* **Happy Lighting**
* **ELK-BLEDOM**
* **Triones**

*Note: While it works with many generic controllers, it is not compatible with proprietary ecosystems like Philips Hue or Govee (Wi-Fi).*

## 🛠 Tech Stack

* **Language**: Kotlin
* **UI Framework**: Jetpack Compose (Material 3)
* **Architecture**: MVVM
* **Asynchronicity**: Coroutines & Flow
* **Bluetooth**: Android BLE API (Scanner, GATT)
* **Audio**: AudioRecord (PCM 16-bit processing)

## 🚀 Getting Started

### Prerequisites
* Android Studio Ladybug (or newer)
* Android Device with Bluetooth LE support (Android 8.0+)

### Permissions
The app requires the following permissions to function:
* `BLUETOOTH_SCAN` & `BLUETOOTH_CONNECT` (Android 12+)
* `ACCESS_FINE_LOCATION` (Required for BLE scanning on older Android versions)
* `RECORD_AUDIO` (For music synchronization)
* `POST_NOTIFICATIONS` (For the foreground service)
* `BIND_NOTIFICATION_LISTENER_SERVICE` (To read media metadata like Album Art)

### Installation
1.  Clone the repository.
2.  Open in Android Studio.
3.  Build and Run on your device.
4.  **Important**: On first launch, grant the Notification Listener permission when prompted to enable the "Music" tab features.

## 📸 Screenshots

| Control Panel | Music Sync | Dark Mode |
|:---:|:---:|:---:|
| <img src="https://github.com/user-attachments/assets/32b35674-715e-4694-ae07-410fd4235515" width="220" /> | <img src="https://github.com/user-attachments/assets/04e5c433-a1cc-4bdc-be76-9e38b0e9aee1" width="220" /> | <img src="https://github.com/user-attachments/assets/e1218a4b-8769-4e37-9e41-9e839c9e44cd" width="220" /> |

## 🤝 Contributing

Contributions are welcome! Please fork the repository and submit a pull request.

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---
*Built with ❤️ using Jetpack Compose*
