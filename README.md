# Wattson for Android

**Gary watches your Rivian. Wattson knows it.**
*A native Android dashboard for your Rivian. Easy to read, useful, and private.*

> **This is a fork.** It is a native Android port of [firerock31/wattson](https://github.com/firerock31/wattson), a self-hosted Python dashboard for Rivian owners. All credit for the original idea, design, tagline, analysis approach, and demo dataset goes to the upstream author. See [Notes for the original author](#notes-for-the-original-author).

---

## What this fork is

Upstream Wattson runs on an always-on machine (Docker + Python). It polls Rivian's API, stores snapshots in SQLite, and generates an HTML dashboard. This fork rebuilds that dashboard as an **installable Android app** (Kotlin + Jetpack Compose) that runs entirely on the phone, with no server.

| | Upstream (`firerock31/wattson`) | This fork |
|---|---|---|
| Runs on | Always-on machine via Docker | Android 8.0+ phone |
| Language | Python | Kotlin, Jetpack Compose, Material 3 |
| Rivian data | Adaptive poller, full trip/charge/drain history derived from snapshots | Live battery, charge limit, range, odometer, and charging state on demand |
| History | Persistent SQLite | 10-day demo history (from upstream `demo.py`) plus drives and charges you log by hand |
| Distribution | `git clone` + `docker compose up` | Signed APK attached to every GitHub Release |

## What was accomplished

- **Native Android app** with five screens (Overview, Drives, Charging, Savings, Battery) and Settings, in a dark Rivian-inspired theme.
- **Data layer**: `WattsonRepository` (app state, persisted settings and session), `DemoDataProvider` (a Kotlin port of upstream `demo.py`: the same Venice home base, the same 10-day Los Angeles itinerary, and the same R2 physics), and `FuelSavings` (EV cost vs a gas SUV).
- **Rivian account linking**: a Kotlin GraphQL client that makes the same calls as `rivian-python-client` 2.1.0, which upstream `auth.py`/`riv.py` used. That is CSRF, login, OTP, vehicle list, and vehicle state. Your password is sent only to Rivian and is never stored.
- **Release pipeline**: GitHub Actions runs unit tests and lint, builds a release APK, and publishes a GitHub Release with notes on every push to `main`.
- **Launcher icon**: a vector adaptive icon (gauge ring plus charging bolt) with a monochrome layer for Android 13+ themed icons.
- **Fixes and hardening**: locale-safe number entry, correct edge-to-edge insets, state that survives rotation, memory-safe hero image, and app backup disabled so Rivian session tokens stay on the device.

## Features

- **Overview**: Rivian-style battery gauge (SoC, rated range, charge limit, charging power), odometer, and 4 tappable stat cards. Includes a simulated 11 kW plug-in so you can watch a charge session build up.
- **Drives**: per-trip miles, kWh, mi/kWh, Wh/mi, average speed, and temperature. Search by place, and log drives by hand.
- **Charging**: home AC, public AC, and DC fast sessions with SoC range, kWh, miles added, and average kW. Tap a DC session to record what you paid per kWh, and it feeds straight into savings.
- **Savings**: lifetime and per-session savings vs a gas SUV (MPG and $/gal configurable).
- **Battery**: night-by-night vampire drain (10 PM to 7 AM), an abnormal-night alert (>1.6x the median), a daily SoC curve, and pack capacity.
- **Settings**: home $/kWh, comparison MPG, gas price, and vehicle name/model. Also link or unlink your Rivian account and reload the demo dataset.

## Download and install

Go to **[Releases](../../releases)** and download the latest `wattson-1.0.N.apk`.

1. Open the `.apk` on an Android 8.0+ phone.
2. Allow **Install unknown apps** for your browser or file manager when prompted.
3. Choose **Explore with Demo Vehicle**, or link your Rivian account.

Each release lists what changed (auto-generated from merged pull requests), how it was signed, and the exact commit it was built from.

### How releases are made

`.github/workflows/android-apk.yml` runs on every push, pull request, and manual run:

| Trigger | Result |
|---|---|
| Pull request | Tests, lint, and APK build. The APK is attached to the workflow run as an artifact. |
| Push to `main` | All of the above, plus a GitHub Release `v1.0.N` with notes and the APK attached. |
| Actions → *Android APK* → *Run workflow* | Same as a push to `main`, for the selected branch. |
| Push a `v*` tag | A release named after that tag. |

### Release signing (optional)

Without signing secrets, the APK is signed with the CI runner's throwaway debug key. It installs fine, but each build can have a different signature, so you may need to uninstall before installing a newer build. For stable in-place updates, add these repository secrets (Settings → Secrets and variables → Actions):

| Secret | Value |
|---|---|
| `WATTSON_KEYSTORE_BASE64` | `base64 -w0 wattson-release.jks` output |
| `WATTSON_KEYSTORE_PASSWORD` | Keystore password |
| `WATTSON_KEY_ALIAS` | Key alias (e.g. `wattson`) |
| `WATTSON_KEY_PASSWORD` | Key password |

Create the keystore once with `keytool -genkeypair -v -keystore wattson-release.jks -alias wattson -keyalg RSA -keysize 4096 -validity 10000` and keep it somewhere safe. Never commit it.

## Build locally

Requires JDK 21 and the Android SDK (platform 36).

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug   # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease                             # debug-signed unless WATTSON_KEYSTORE_* env vars are set
```

## Project layout

```
app/src/main/java/com/example/wattson/
├── MainActivity.kt            # Scaffold, navigation, top bar
├── model/Models.kt            # Trip, ChargingSession, OvernightDrain, settings, ...
├── data/
│   ├── WattsonRepository.kt   # App state (StateFlow), persistence, simulated charging
│   ├── DemoDataProvider.kt    # Port of upstream demo.py
│   ├── FuelSavings.kt         # EV vs gas SUV math
│   └── rivian/                # Rivian GraphQL client and models
└── ui/                        # Compose screens, charts, theme, icons
```

## Tech stack

- **Platform**: Android (minSdk 26, targetSdk 36)
- **Language**: Kotlin 2.2.21
- **UI**: Jetpack Compose with Material 3
- **State**: Repository with Kotlin Coroutines and StateFlow
- **Build**: Gradle 9.3.1, Android Gradle Plugin 9.1.1 (built-in Kotlin)

## Known limitations

- Demo and manually logged history live in memory. Drives, charges, and DC costs you log are lost when Android closes the app. Settings and the linked Rivian session persist.
- A linked account syncs live state only. Rivian's API keeps no trip or charge history; upstream builds that history by polling continuously from a server, which a phone app cannot do without draining the car or the phone.
- Rivian sign-in uses Rivian's unofficial consumer GraphQL API and may break if Rivian changes it. Not affiliated with Rivian.

## Notes for the original author

Hi @firerock31, thanks for building Wattson and sharing it under MIT. Some context on this fork:

- **Attribution and license**: your MIT copyright notice is kept unchanged in [`LICENSE`](LICENSE). The tagline, feature set, drain methodology (10 PM to 7 AM window, 1.6x median threshold), savings model, and demo itinerary all come from your project.
- **Code reused**: `DemoDataProvider.kt` ports `demo.py` (places, itinerary, R2 constants). `RivianApiClient.kt` follows the operations and headers your `auth.py`/`riv.py` used via `rivian-python-client` 2.1.0, including the km-to-miles and meters-to-miles conversions you documented in `riv.py`/`config.py`.
- **Not ported**: the adaptive poller, snapshot-to-trip derivation (`analyze.py`), AAA gas-price scraping, geocoding, weather, route maps, and the encrypted token store. On Android, tokens live in app-private storage with backup disabled.
- **Merging back**: this fork's `main` removes the Python service, so it is not meant to be merged upstream as-is. If you would like an Android client in the original repo, the app could live in an `android/` folder next to the Python code. Happy to open a PR shaped that way.
- **PII check**: your `pii-check.yml` workflow is kept and passes. The only coordinates in the Kotlin code are the fictional demo home base from `demo.py`.
- **Hero image**: `app/src/main/res/drawable-nodpi/rivian_hero.jpg` came with the Android port, and its source is unverified. Replace it if you plan to redistribute.

## License

MIT. See [`LICENSE`](LICENSE). The original work is copyright its upstream author; changes in this fork are released under the same license.
