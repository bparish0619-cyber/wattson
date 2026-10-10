# Wattson for Android

**Gary watches your Rivian. Wattson knows it.**
*A native Android dashboard for your Rivian. Easy to read, useful, and private.*

> **This is a fork.** It is a native Android port of [firerock31/wattson](https://github.com/firerock31/wattson), a self-hosted Python dashboard for Rivian owners. All credit for the original idea, design, tagline, analysis approach, and demo dataset goes to the upstream author. See [Notes for the original author](#notes-for-the-original-author).

---

## What this fork is

Upstream Wattson runs on an always-on machine (Docker + Python). It polls Rivian's API, stores snapshots in SQLite, derives trips, charges and overnight drain from them, and generates an HTML dashboard. This fork ports that whole pipeline to an **installable Android app** (Kotlin + Jetpack Compose): the phone is the poller, the database and the dashboard, with no server.

| | Upstream (`firerock31/wattson`) | This fork |
|---|---|---|
| Runs on | Always-on machine via Docker | Android 8.0+ phone |
| Language | Python | Kotlin, Jetpack Compose, Material 3 |
| Polling | Adaptive poller (`poller.py`) | Same tiers as a foreground service, or a 15-minute battery-saver job |
| Storage | SQLite snapshots | On-device SQLite snapshots |
| Derivation | `analyze.py` + overnight windows in `report.py` | Kotlin port of the same logic |
| Enrichment | Nominatim, Open-Meteo, AAA scraping, Esri route maps | Same services, cached on the device |
| Tokens | Fernet-encrypted file | AES-GCM with a key in the Android Keystore |
| Distribution | `git clone` + `docker compose up` | Signed APK attached to every GitHub Release |

## What was accomplished

- **Native Android app** with five screens (Overview, Drives, Charging, Savings, Battery) and Settings, in a dark Rivian-inspired theme.
- **Automatic tracking** for a linked Rivian account. Nothing has to be entered by hand except what you paid at public chargers:
  - **Adaptive poller** (`poller.py`): 30 s while driving, 5 min parked, 30 min while the car sleeps. Runs as a foreground service, or as a battery-saver job every 15 min that bursts to 30 s during a drive. Restarts after reboot.
  - **Local storage** (`db.py`): every poll is saved in an on-device SQLite database. Trips, charges and drain are re-derived from it, so history survives restarts and improves as the analysis does.
  - **Snapshot-to-trip derivation** (`analyze.py`): drives (including ones that happened between polls, via odometer bracketing), short-errand rollups, charging sessions classified DC fast vs AC, and clean 10 PM to 7 AM overnight drain with wake-up counts and abnormal-night flags.
  - **Geocoding** (`geocode.py`): trip endpoints and round-trip destinations named via OpenStreetMap Nominatim, cached per ~100 m.
  - **Weather** (`weather.py`): ambient temperature for each trip from Open-Meteo, cached per day and ~11 km cell.
  - **AAA gas prices** (`gasprice.py`): your metro's daily regular-gas average, used per charge day in the savings math.
  - **Route maps**: each drive's GPS path on Esri's dark basemap, as upstream's dashboard, with tiles cached on the phone.
  - **Encrypted token store** (`crypto.py`): the Rivian session is sealed with AES-256-GCM under an Android Keystore key. The password is never stored.
- **Rivian account linking**: a Kotlin GraphQL client that makes the same calls as `rivian-python-client` 2.1.0, which upstream `auth.py`/`riv.py` used.
- **Demo mode** (no account): upstream's `demo.py` simulator ported to Kotlin. It emits snapshots that run through the same pipeline, so the demo shows exactly what a linked car produces. Demo controls (simulated charging, manual logging) are hidden when an account is linked.
- **Release pipeline**: GitHub Actions runs unit tests and lint, builds a release APK, and publishes a GitHub Release with notes on every push to `main`.
- **Launcher icon**: a vector adaptive icon (gauge ring plus charging bolt) with a monochrome layer for Android 13+ themed icons.
- **Fixes and hardening**: locale-safe number entry, correct edge-to-edge insets, state that survives rotation, memory-safe hero image, and app backup disabled so Rivian session tokens stay on the device.

## Features

- **Overview**: Rivian-style battery gauge (SoC, range, charge limit, live charging power from the SoC slope), location, odometer, last sync time, and 4 tappable stat cards.
- **Drives**: per-trip miles, kWh, mi/kWh, Wh/mi, average speed and ambient temperature. Tap a drive for its route map. Search by place, and see short drives rolled up per day.
- **Charging**: home AC, public AC and DC fast sessions with SoC range, kWh, miles added and average kW. Tap an away session to record what you paid per kWh, and it feeds straight into savings.
- **Savings**: lifetime and per-session savings vs a gas SUV at each charge day's AAA gas price.
- **Battery**: night-by-night vampire drain (10 PM to 7 AM), wake-ups, an abnormal-night alert (>1.6x the median), the daily SoC curve, and pack capacity.
- **Settings**: home location (or "use the car's current location"), home $/kWh, comparison MPG, AAA state and metro, usable battery kWh, polling mode, and link/unlink.

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
│   ├── WattsonRepository.kt   # App state (StateFlow): polls, stores, derives, publishes
│   ├── analysis/Analyzer.kt   # Port of analyze.py + report.py overnight windows
│   ├── db/WattsonDb.kt        # SQLite: snapshots, DC prices, place/weather/gas caches
│   ├── enrich/                # Nominatim geocoding, Open-Meteo weather, AAA gas prices
│   ├── poll/                  # Adaptive poll policy, foreground service, WorkManager job, boot receiver
│   ├── secure/TokenStore.kt   # Keystore-backed encrypted Rivian session
│   ├── DemoDataProvider.kt    # Port of upstream demo.py (snapshot simulator)
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

- History starts when you link: Rivian's API keeps no trip or charge history, so Wattson builds it from its own polls (as upstream does).
- Detail depends on polling. Battery saver and Android's Doze mode can delay polls, so drives may be bracketed (start time and speed estimated) and nights without a poll near 10 PM and 7 AM are skipped.
- The demo is in memory only; a linked account's data is stored on the phone (unlinking keeps it).
- Rivian sign-in uses Rivian's unofficial consumer GraphQL API and may break if Rivian changes it. Nominatim, Open-Meteo, AAA and Esri are free public services used lightly and cached. Not affiliated with Rivian.

## Notes for the original author

Hi @firerock31, thanks for building Wattson and sharing it under MIT. Some context on this fork:

- **Attribution and license**: your MIT copyright notice is kept unchanged in [`LICENSE`](LICENSE). The tagline, feature set, drain methodology (10 PM to 7 AM window, 1.6x median threshold), savings model, and demo itinerary all come from your project.
- **Code ported**: `Analyzer.kt` is a line-by-line port of `analyze.py` (trips, bracketing, errands, charges) plus `report.py`'s overnight windows and live-charge rate. `PollPolicy.kt` ports `poller.py`'s tiers, `WattsonDb.kt` mirrors `db.py`, and `Geocoder.kt`, `WeatherService.kt` and `GasPriceService.kt` port `geocode.py`, `weather.py` and `gasprice.py` with the same caching rules. `TokenStore.kt` replaces `crypto.py` with the Android Keystore, `DemoDataProvider.kt` ports `demo.py`'s simulator, and `RouteMap.kt` redraws your Esri trip maps. `RivianApiClient.kt` follows the operations, fields and unit conversions of `auth.py`/`riv.py`/`config.py`.
- **Not ported**: the HTML report itself (the app's screens replace it), the 24-hour activity strip, and the drive-watch file.
- **Merging back**: this fork's `main` removes the Python service, so it is not meant to be merged upstream as-is. If you would like an Android client in the original repo, the app could live in an `android/` folder next to the Python code. Happy to open a PR shaped that way.
- **PII check**: your `pii-check.yml` workflow is kept and passes. The only coordinates in the Kotlin code are the fictional demo home base from `demo.py`.
- **Hero image**: `app/src/main/res/drawable-nodpi/rivian_hero.jpg` came with the Android port, and its source is unverified. Replace it if you plan to redistribute.

## License

MIT. See [`LICENSE`](LICENSE). The original work is copyright its upstream author; changes in this fork are released under the same license.
