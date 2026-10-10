# Wattson for Android

**Gary watches your Rivian. Wattson knows it.**  
*A native Android dashboard for your Rivian. Easy to read, useful, and private.*

---

## Overview

Wattson is an Android application built with **Kotlin and Jetpack Compose** implementing a comprehensive EV companion for Rivian owners and enthusiasts. It tracks per-trip efficiency, charging history with home electricity rates and DC fast-charge cost recording, overnight vampire drain, and lifetime fuel savings compared against a gas SUV.

## Key Features

- **Vehicle Overview & Live Gauge**:
  - Rivian-style segmented donut gauge with current battery SoC%, rated range, charging power (kW), and target charge limit (90%).
  - Live vehicle status: Parked vs Charging, location tags, odometer reading.
  - Interactive charging simulation: plug in / unplug to view live charging curves and power.
- **Drive & Trip History**:
  - Full drive record with distance (miles), energy used (kWh), efficiency (mi/kWh), Wh/mi, average speed (mph), and ambient temperature (°F).
  - Search and filter by start and destination locations.
  - Interactive dialog to simulate or log custom drives.
- **Charging Log & Cost Tracking**:
  - Full record of Home AC and DC Fast charging sessions with start/end SoC%, kWh added, miles added, and average kW rate.
  - **Interactive DC Fast Charge Cost Entry**: Click on any DC fast charge session to record what you paid per kWh (or $0.00 for free charging), which immediately feeds into lifetime fuel savings!
- **Fuel Savings vs Gas SUV**:
  - Compare total EV electricity costs against an equivalent gasoline SUV (configurable MPG, default 25 MPG, and gas $/gal, default $4.50/gal).
  - Detailed session-by-session breakdown table showing kWh, EV cost, gas equivalent, and net dollars saved.
- **Overnight Vampire Drain Tracking**:
  - 9-hour clean window (10 PM to 7 AM) night-by-night idle battery loss analysis.
  - Abnormal drain alert indicator (>1.6x baseline median) to spot vehicle wake-up issues.
  - Interactive bar chart with baseline median reference line and night selection.
  - Daily battery SoC curve and pack health metrics.
- **Settings & Configuration**:
  - Adjust home electricity rate ($/kWh), gas SUV comparison MPG, and gas price ($/gal).
  - Customize vehicle display name and model.
  - 1-tap reload of the authentic 10-day fictional Los Angeles driving dataset.

## Tech Stack

- **Platform**: Android (minSdk 26, targetSdk 36)
- **Language**: Kotlin 2.2.21
- **UI Framework**: Jetpack Compose with Material Design 3
- **Architecture**: MVVM with Kotlin Coroutines and StateFlow
- **Theme**: Rivian automotive dark aesthetic (`#0B0D10` background, `#7ED321` Rivian green, `#F5A623` amber)
- **Build System**: Gradle 9.3.1 with Android Gradle Plugin 9.1.1

## Download a Test APK

Every push and pull request runs the **Android APK** workflow (`.github/workflows/android-apk.yml`), which runs unit tests and lint, then builds a release APK.

- **Direct download (recommended)**: Actions → *Android APK* → *Run workflow*. When it finishes, the APK is attached to a new pre-release named `Wattson test build N` on the Releases page. Pushing a `v*` tag does the same.
- **Workflow artifact**: open any workflow run and download `wattson-apk-N` (a zip containing the `.apk`; requires being signed in to GitHub).

To install, open the `.apk` on an Android 8.0+ phone and allow *Install unknown apps* for your browser or file manager when prompted.

### Release signing (optional)

Without signing secrets, the release APK is signed with the CI runner's throwaway debug key. It installs fine, but each build can have a different signature, so you may need to uninstall before installing a newer build. For stable updates, add these repository secrets (Settings → Secrets and variables → Actions):

| Secret | Value |
|---|---|
| `WATTSON_KEYSTORE_BASE64` | `base64 -w0 wattson-release.jks` output |
| `WATTSON_KEYSTORE_PASSWORD` | Keystore password |
| `WATTSON_KEY_ALIAS` | Key alias (e.g. `wattson`) |
| `WATTSON_KEY_PASSWORD` | Key password |

Create the keystore once with `keytool -genkeypair -v -keystore wattson-release.jks -alias wattson -keyalg RSA -keysize 4096 -validity 10000` and keep it somewhere safe; never commit it.

## Build Locally

Requires JDK 21 and the Android SDK (platform 36).

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug   # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease                             # debug-signed unless WATTSON_KEYSTORE_* env vars are set
```

## Known Limitations

- Trip, charging, and drain history is generated demo data held in memory; drives, charges, and DC costs you log are lost when the app process restarts. Settings and the linked Rivian session persist.
- A linked Rivian account syncs live battery level, charge limit, range, odometer, and charging state. Rivian's API exposes no trip or charge history, so a linked account starts with an empty log.
- Rivian sign-in uses Rivian's unofficial consumer GraphQL API (the same calls as `rivian-python-client`) and may break if Rivian changes it.

## License

MIT
