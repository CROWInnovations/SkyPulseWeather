# SkyPulse Weather — Google Play Store Submission Guide

## 1. Store Listing Copy
- **App Name** *(30 chars max)*: `SkyPulse Weather` (`16 chars`)
- **Website URL**: `https://crowinnovations.com/apps/`
- **Privacy Policy URL**: `https://crowinnovations.com/apps/skypulseweather/privacy/`
- **Short Description** *(80 chars max)*:
  `10-day 3D weather, 15m sync, 1x1–5x2 widgets. No ads. No paywalls.` (`66 chars`)
- **Full Description**:
  ```text
  SkyPulse Weather — "No Ads. No Data Collection. No Paywalls."

  SkyPulse Weather combines rich 3D atmospheric visuals with fast 15-minute forecast updates, swipeable 10-day outlooks, and clean home-screen widgets in every size.

  FEATURES
  • Swipeable 10-Day Forecast & Home Widget: View 24-hour hourly strips and 10-day temperature & precipitation forecasts with M/d date labels and >50% rain callouts, and swipe smoothly right or left across all 10 days directly on your home-screen widget.
  • 5 Widget Size Options (5×2, 4×2, 2×2, 2×1, 1×1): Pick from Mini (1×1), Compact Strip (2×1), Square (2×2), Classic 10-Day (4×2 / 4×3), or Widescreen 10-Day (5×2) widgets—each formatted cleanly for its size.
  • Custom Widget Temp, Top Clock & Date Styling: Optional top centered clock and abbreviated month/day Date Display (Off, Left of Time, Right of Time, or Below Time, e.g. Nov 25) positioned above City/State, Temperature, and Sun/Moon, with independent font size (+ / −), font style (Default, Serif, Mono, Italic), and accent color controls.
  • Balanced 3D Skies & True Moon Phases: Glare-balanced golden sun with animated solar rays, drifting 3D clouds, rain/storm visuals, and centered 3D lunar phase rendering.
  • Fast 15-Minute Automatic Sync: Background updates every 15 minutes keep current temps, wind speed (mph or km/h), UV index, and active U.S. NOAA severe weather alerts up to date.
  • Multiple Locations (GPS, ZIP Code, or City): Track optional foreground GPS alongside saved U.S. 5-digit ZIP codes and global cities.
  • Privacy-First Security: Encrypted TLS 1.2/1.3 connections, strict input sanitization, zero accounts, zero ad networks, zero data collection, and zero paywalls.

  ATTRIBUTION
  Forecast & geocoding data by Open-Meteo.com (CC BY 4.0) · U.S. severe weather alerts by NOAA / National Weather Service.
  ```

## 2. Graphic Assets Included in `play_store_assets/`
- **App Icon (`512 × 512`, 32-bit PNG, `< 1 MB`)**: `icon_512x512.png`
- **Feature Graphic (`1024 × 500`, PNG & `< 1 MB` JPEG)**: `feature_graphic_1024x500.jpg` / `feature_graphic_1024x500.png`
- **Phone & Landscape Screenshots**: `../docs/images/` & `screenshots/`
- **Hosted Privacy Policy URL**: `https://crowinnovations.com/apps/skypulseweather/privacy/` (local copy: `PRIVACY_POLICY.html`)

## 3. Google Play Data Safety Questionnaire Answers
- **Does your app collect or share any of the required user data types?**
  - **Location (Approximate / Precise)**:
    - *Processed ephemerally* over TLS 1.2/1.3 HTTPS solely to return weather forecasts and U.S. weather alerts when the user taps foreground GPS.
    - *Optional*: Users can use all features via manual ZIP/city search without granting location permission.
    - *Shared with third-party advertisers / data brokers*: **No**.
  - **Personal Info / Financial / Contacts / Identifiers / App Activity**: **None collected**.
- **Security Practices**:
  - Data is encrypted in transit (`TLS 1.2/1.3 HTTPS` enforced via `network_security_config.xml` and `usesCleartextTraffic="false"`): **Yes**.

## 4. Technical Release Checklist (v1.2.0 — `versionCode 3`)
- **Signed Release App Bundle (`.aab`)**: `app/build/outputs/bundle/release/app-release.aab`
- **Signed Release APK (`.apk`)**: `app/build/outputs/apk/release/app-release.apk`
- **R8 Deobfuscation Mapping File**: `app/build/outputs/mapping/release/mapping.txt`
- **Target SDK**: `API 35 (Android 15)`, `minSdk 26 (Android 8.0+)`
- **Permissions Audit**: Only `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_COARSE_LOCATION`, `ACCESS_FINE_LOCATION` (foreground-only; `android.hardware.location.gps` marked `required="false"` for Wi-Fi tablet & Chromebook compatibility).
