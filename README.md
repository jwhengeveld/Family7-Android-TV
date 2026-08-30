# Family7 Android TV App 📺✝️

Een moderne, native **Android TV / Google TV** applicatie voor [Family7](https://www.family7.nl/), gebouwd met **Kotlin**, **Jetpack Compose for TV**, **Material 3**, en **AndroidX Media3 (ExoPlayer)**.

De app biedt volledige ondersteuning voor zowel **Live TV** (Family7 Plus) als de complete **On Demand** videocatalogus met afleveringen, seizoenen en zoekfunctionaliteit.

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
  - Snel alfabetisch filteren op alle beschikbare programma's.
  - Real-time zoekfunctie op titel en thema.
- 🎮 **Geoptimaliseerd voor TV Afstandsbediening (D-Pad)**
  - Vloeiende focus-indicatoren met schaalvergroting en Family7-oranje accenten.
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
| **Video Playback** | AndroidX Media3 ExoPlayer (HLS, Adaptive Streaming) |
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
