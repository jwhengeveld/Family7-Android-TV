# Family7 Android TV App 📺✝️

[![Android CI](https://github.com/jwhengeveld/Family7-Android-TV/actions/workflows/android-build.yml/badge.svg)](https://github.com/jwhengeveld/Family7-Android-TV/actions/workflows/android-build.yml)
[![Release](https://img.shields.io/github/v/release/jwhengeveld/Family7-Android-TV?color=orange&label=Latest%20APK)](https://github.com/jwhengeveld/Family7-Android-TV/releases/latest)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-purple.svg)](https://kotlinlang.org)
[![Compose TV](https://img.shields.io/badge/Jetpack%20Compose-TV%201.0.0-blue.svg)](https://developer.android.com/jetpack/compose/tv)
[![Media3](https://img.shields.io/badge/AndroidX-Media3%20ExoPlayer-green.svg)](https://developer.android.com/media/media3)

Een moderne, native **Android TV / Google TV** applicatie voor [Family7](https://www.family7.nl/), ontwikkeld in **Kotlin** met **Jetpack Compose for TV**, **Material 3**, en **AndroidX Media3 (ExoPlayer)**.

De app biedt volledige ondersteuning voor zowel **Live TV** (Family7 Plus livestream) als de complete **On Demand** videotheek met alle programma's, seizoenen, afleveringen en zoekfunctie.

---

## 📸 Schermafbeeldingen (Screenshots)

| Inlogscherm (Drupal Auth) | On Demand Startscherm |
|---|---|
| ![Inlogscherm](docs/screenshots/01_login.png) | ![On Demand Startscherm](docs/screenshots/02_home.png) |

| Programmadetails & Afleveringen | On Demand Video Playback (HD HLS) |
|---|---|
| ![Programmadetails](docs/screenshots/03_detail.png) | ![Video Player](docs/screenshots/04_ondemand_player.png) |

| Live TV (Family7 Plus Uitzending) | A-Z Videotheek & Zoeken |
|---|---|
| ![Live TV Uitzending](docs/screenshots/05_livetv_player.png) | ![A-Z Videotheek](docs/screenshots/06_search_az.png) |

---

## ✨ Functionaliteiten

- 🔴 **Live TV Uitzending (Family7 Plus)**
  - Directe HLS-streamweergave via de Streampartner streaming backend.
  - Elektronische Programmagids (EPG) overlay met huidige en volgende programma's.
  - Automatische kwaliteitsselectie (tot 1080p Full HD) met minimale latentie.
- 🎬 **On Demand Videotheek**
  - Featured Hero Banner met uitgelichte programma's.
  - Dynamische rijen ("Aanbevolen", "Mijn lijst", "Originals", "Documentaires", "Bijbelstudie", etc.).
  - Programmadetailpagina's met synopsis, seizoenen, afleveringsoverzichten en speelduur.
- 🔍 **A-Z Catalogus & Zoeken**
  - Snel alfabetisch filteren op alle 194+ beschikbare programma's.
  - Real-time zoekfunctie op titel en thema.
- 🎮 **Geoptimaliseerd voor TV Afstandsbediening (D-Pad)**
  - Vloeiende focus-indicatoren met schaalvergroting (1.06x) en Family7-oranje accenten.
  - Leanback launcher compatibel voor Android TV, Google TV, en smart home portals (zoals Meta Portal Go).
- 🔐 **Veilige Authenticatie**
  - Drupal authenticatie met sessie- en cookiebeheer.
  - Inloggegevens worden nooit hardcoded opgeslagen; optionele veilige versleutelde lokale opslag via AndroidX Security EncryptedSharedPreferences.

---

## 🛠️ Architectuur & Tech Stack

| Component | Technologie |
|---|---|
| **Taal** | Kotlin 2.1.0 |
| **UI Framework** | Jetpack Compose for TV / Compose Material 3 |
| **Video Playback** | AndroidX Media3 ExoPlayer 1.5.1 (HLS, Adaptive Streaming) |
| **Networking & HTTP** | OkHttp 4.12.0 met persistente CookieJar |
| **HTML / Scraping** | Jsoup 1.18.3 & Streampartner Recursive Unpacker |
| **Afbeeldingen** | Coil 2.7.0 (Compose AsyncImage met disk caching) |
| **Beveiliging** | AndroidX Security Crypto (EncryptedSharedPreferences) |
| **Minimaal Android OS** | Android 7.0 (API Level 24+) / TV Android 10+ |

---

## 🚀 Installatie & Sideloading (APK)

1. Download de nieuwste APK uit de [Releases](https://github.com/jwhengeveld/Family7-Android-TV/releases) sectie (`family7-androidtv-v1.0.0.apk`).
2. Installeer op uw Android TV of aangesloten apparaat via ADB:
   ```bash
   adb connect <IP_VAN_UW_TV>:5555
   adb install -r family7-androidtv-v1.0.0.apk
   ```
3. Start de app op via het Android TV startscherm of direct via:
   ```bash
   adb shell am start -n nl.family7.tv/.MainActivity
   ```

---

## 💻 Zelf Bouwen (Build from Source)

Vereisten: **Android Studio Meerkat / Ladybug** of **JDK 17+** en Android SDK 35.

```bash
git clone https://github.com/jwhengeveld/Family7-Android-TV.git
cd Family7-Android-TV

# Compileer de debug APK
./gradlew assembleDebug

# De APK is te vinden in:
# app/build/outputs/apk/debug/app-debug.apk
```

---

## 📄 Licentie

Dit project is ontwikkeld voor openbaar gebruik en compatibel met Family7 Plus abonnementen. Alle programmacontent en handelsmerken zijn eigendom van [Family7](https://www.family7.nl/).
