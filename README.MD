# SafeOrbit

[![Build Status](https://img.shields.io/badge/build-passing-brightgreen)](https://github.com/your-username/safeorbit/actions)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-Android-green.svg)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/language-Kotlin-orange.svg)](https://kotlinlang.org/)


**SafeOrbit** is a Kotlin-based Android application that allows two smartphones to securely connect and interact in real time using Firebase as the backend. The app is designed for private tracking and remote monitoring between trusted users — one device acts as a **Server** and the other as a **Client**.

---

## ✨ Features

- 🔐 Role selection on first launch: **Server** or **Client**
- 📍 Real-time location tracking of the Server device on a map
- 🎙️ Remote microphone access: Clients can request audio from the Server
- 🔋 Smart power-saving location updates (based on device movement)
- 🔗 Firebase-based pairing with custom **ID** and **Code**
- 📁 Firebase Realtime Database and Storage integration
- 👥 One Client can link to multiple Servers

---

## 📲 Use Cases

- Monitoring a child's or loved one's device location
- Personal security applications
- Safe remote supervision of a trusted person
- Private remote audio access in limited contexts (with consent)

---

## 📸 Screenshots

| ![](images/screenshot1.png) | ![](images/screenshot2.png) |
|-----------------------------|-----------------------------|
| ![](images/screenshot3.png) | ![](images/screenshot4.png) |

---

## 🛠️ Technologies Used

- **Kotlin + MVVM architecture**
- **Firebase Authentication** (anonymous)
- **Firebase Realtime Database** (data exchange)
- **Firebase Storage** (audio uploads)
- **Google Maps SDK**
- **Fused Location Provider**
- **Foreground Services & WorkManager**
- **Hilt (Dependency Injection)**

---

## 🚀 Getting Started

### 1. Clone the repository

```bash
git clone https://github.com/your-username/safeorbit.git
```

### 2. Open in Android Studio
- Open the project in Android Studio
- Make sure your google-services.json is placed in the app/ folder
- Enter your api key in local.properties (YANDEX_MAPKIT_API_KEY=your_key)

### 3. Set up Firebase
- Enable:
- Anonymous Authentication
- Realtime Database (with custom rules)
- Storage (optional: for audio files)

## 🔒 Firebase Rules (Example)
```
{
  "rules": {
    "users": {
      "$uid": {
        ".read": "$uid === auth.uid",
        ".write": "$uid === auth.uid"
      }
    },
    "servers": {
      "$serverId": {
        ".read": "auth != null && root.child('servers').child($serverId).child('owner').val() === auth.uid",
        ".write": "auth != null && root.child('servers').child($serverId).child('owner').val() === auth.uid"
      }
    },
    "clients": {
      "$clientId": {
        ".read": "$clientId === auth.uid",
        ".write": "$clientId === auth.uid"
      }
    }
  }
}

```

## 📄 License
This project is open-source and available under the MIT License.

## 🙌 Contributions
Pull requests are welcome. For major changes, please open an issue first to discuss what you would like to change.