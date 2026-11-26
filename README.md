<p align="center">
  <img src="./docs/images/hero_banner.jpg" alt="SkyPulse Weather App Banner" width="100%" />
</p>

<h1 align="center">SkyPulse Weather App</h1>

<p align="center">
  <em>"No Ads. No Data Collection. No Paywalls."</em>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android%208.0%2B-38BDF8?logo=android&logoColor=white" alt="Android 8.0+" />
  <img src="https://img.shields.io/badge/Version-1.2.0-3B82F6" alt="Version 1.2.0" />
  <a href="https://github.com/CROWInnovations/SkyPulseWeather/releases/latest/download/SkyPulseWeather-v1.2.0.apk"><img src="https://img.shields.io/badge/Download-Android%20APK%20(v1.2.0)-10B981?logo=android&logoColor=white" alt="Download Android APK v1.2.0" /></a>
  <a href="./LICENSE"><img src="https://img.shields.io/badge/License-Apache%202.0-22C55E" alt="Apache 2.0 License" /></a>
  <a href="https://crowinnovations.com/apps/"><img src="https://img.shields.io/badge/Built%20By-CROW%20Innovations-F59E0B" alt="CROW Innovations" /></a>
</p>

---

**SkyPulse Weather App** is a fast, clean, privacy-first Android weather app and home-screen widget suite built for people who want accurate 10-day forecasts, rich 3D sky visuals, and flexible widget sizes—without pop-up ads, data collection, or paywalls.

### Direct Android Installation (APK)

- **Latest Signed Release APK (`v1.2.0`)**: [**Download `SkyPulseWeather-v1.2.0.apk`**](https://github.com/CROWInnovations/SkyPulseWeather/releases/latest/download/SkyPulseWeather-v1.2.0.apk) *(or view [GitHub Releases](https://github.com/CROWInnovations/SkyPulseWeather/releases/latest))*
- Signed with the production release key (`com.crowinnovations.skypulseweather`, `Android 8.0+ / API 26–35`), allowing clean manual installation and seamless in-place updates alongside future releases.

---

## Screenshots (Washington, DC)

| 10-Day Swipeable Home Widget | Widget Sizes (`5×2`, `2×2`, `2×1`, `1×1`) | Full-Screen Forecast & Top Clock |
| :---: | :---: | :---: |
| <img src="./docs/images/washington_dc_widget.png" alt="Washington DC 10-Day Swipeable Home Screen Widget" width="255" /> | <img src="./docs/images/washington_dc_widget_sizes.png" alt="Washington DC 5x2 2x2 2x1 1x1 Widget Sizes" width="255" /> | <img src="./docs/images/washington_dc_fullscreen.png" alt="Washington DC Full Screen Forecast" width="255" /> |

| Clean Settings & Font/Color Controls | Widescreen / Landscape View |
| :---: | :---: |
| <img src="./docs/images/washington_dc_settings.png" alt="SkyPulse Weather Settings" width="260" /> | <img src="./docs/images/washington_dc_landscape.png" alt="Washington DC Landscape Forecast" width="460" /> |

---

## What It Does

- **Swipeable 10-Day Forecast & Home Widget**: Full 10-day high/low and precipitation forecasts with `M/d EEE` date labels (e.g., `11/25 Wed`) and automatic rain `%` callouts beside rainy clouds above `50%`, plus instant, smooth horizontal finger swiping right and left across all 10 days (`Next 5 ›` / `‹ Prev 5` also supported) directly on the home-screen widget without triggering launcher widget-resize popups.
- **5 Dedicated Widget Size Options (`5×2`, `4×2`, `2×2`, `2×1`, `1×1`)**: Choose the full-width widescreen 10-day forecast card (`5×2`), classic 10-day forecast card (`4×2` / `4×3`), square weather card (`2×2`), compact horizontal strip (`2×1`), or mini temperature tile (`1×1`)—each tailored cleanly to its allotted space and freely resizable.
- **Custom Widget Temperature, Top Clock & Date Styling (Up to `74sp`)**: Customize font size (`+` / `−`, up to `74sp` for both Widget Temperature and Top Centered Clock), font style (`Default`, `Serif`, `Mono`, `Italic`), and accent color (`White`, `Sky`, `Gold`, `Orange`, `Mint`, `Violet`)—plus an optional abbreviated month/day **Date Display** dropdown (`Off`, `Left of Time`, `Right of Time`, or `Below Time`, e.g., `Nov 25`) that automatically inherits your chosen Time font and color.
- **15-Minute Automatic Sync**: Background updates run every **15 minutes** via Android `WorkManager` alongside instant 1-tap refresh.
- **Balanced 3D Skies, Animated Solar Rays & Prominent 3D Moon Phases**: Glare-balanced golden daytime sun with pulsing/rotating solar rays, drifting 3D cloud variations, rain and storm visuals, active U.S. NOAA severe weather alerts, and large, astronomically accurate 3D moon phases over a starry Milky Way sky with centered lunar phase labels.
- **Flexible Units & Multi-City Support**: Toggle Fahrenheit (`°F`) or Celsius (`°C`), wind speed in `mph` or `km/h`, and save multiple U.S. ZIP codes, global cities, or optional foreground GPS.

---

## Security & Privacy — Built In from the Ground Up

SkyPulse Weather applies modern mobile security standards so your phone and location stay private:

- **No Ads, No Data Collection, No Paywalls**: Contains zero advertising SDKs, zero behavioral analytics trackers, zero account walls, and zero subscription gates.
- **Foreground-Only Location & Neighborhood Rounding**: If you choose to use GPS, the app checks your location only while open on screen (never in the background), rounds coordinates to a general ~2 km area before requesting weather data, and excludes local location caches from cloud backups.
- **Encrypted Connections Only**: All weather and alert updates are locked to modern encrypted HTTPS (`TLS 1.2 / 1.3`) using trusted system certificates, blocking unencrypted traffic and unsafe redirects.
- **Sanitized Inputs & Anti-Hijack Protection**: Every search entry and optional custom server link is automatically cleaned and validated before use to block malformed inputs or local-network probing, while touch-overlay protection prevents malicious apps from spoofing taps over the screen.

---

## Build from Source

Requires **JDK 17** and the **Android SDK (`compileSdk 35`, `minSdk 26`)**:

```bash
./gradlew testDebugUnitTest assembleRelease bundleRelease
```

---

## License & Attribution

- Licensed under the [Apache License, Version 2.0](./LICENSE) (see [NOTICE](./NOTICE)).
- Weather & geocoding data by [Open-Meteo.com](https://open-meteo.com) · U.S. severe weather alerts by [NOAA / National Weather Service](https://www.weather.gov).
- Official Apps Directory: [https://crowinnovations.com/apps/](https://crowinnovations.com/apps/)
- Privacy Policy: [https://crowinnovations.com/apps/skypulseweather/privacy/](https://crowinnovations.com/apps/skypulseweather/privacy/)
