# Wattson for Android

A native Android port of Wattson (Kotlin + Jetpack Compose). The phone is the poller, the database and the dashboard: no always-on machine, no Docker. It runs the same pipeline as the Python service, ported module by module, and stores everything on the device.

## How it maps to the Python service

| Python | Android (`app/src/main/java/com/example/wattson/`) |
|---|---|
| `poller.py` adaptive tiers | `data/poll/PollPolicy.kt`: 30 s while driving, 5 min parked, 30 min asleep. Runs as a foreground service (**Continuous**, with a status notification) or a 15-minute background job that bursts to 30 s during a drive (**Battery saver**), chosen in Settings. Resumes after reboot. |
| `db.py` | `data/db/WattsonDb.kt`: on-device SQLite with raw snapshots plus caches for places, weather, gas prices and DC prices you enter. Trips, charges and drain are re-derived from the snapshots. |
| `analyze.py` + the overnight windows in `report.py` | `data/analysis/Analyzer.kt`: trips with odometer bracketing, via points and GPS paths; short-errand rollups; AC/DC charge sessions; clean 10 PM to 7 AM drain with trips and charges backed out, wake-up counts and the 1.6x-median flag; live charging power from the SoC slope. |
| `geocode.py` | `data/enrich/Geocoder.kt`: Nominatim, 1 request/s, cached per ~100 m. |
| `weather.py` | `data/enrich/WeatherService.kt`: Open-Meteo, one call per day and ~11 km cell. |
| `gasprice.py` | `data/enrich/GasPriceService.kt`: AAA metro average, at most once per day, priced per charge day. |
| Trip maps in `report.py` | `ui/components/RouteMap.kt`: Esri Dark Gray tiles with the GPS route, tiles cached on the phone. |
| `crypto.py` | `data/secure/TokenStore.kt`: the Rivian session sealed with AES-256-GCM under an Android Keystore key. The password is never stored. |
| `auth.py` / `riv.py` | `data/rivian/RivianApiClient.kt`: the same GraphQL operations, fields and unit conversions as `rivian-python-client` 2.1.0 (CSRF, login, OTP, vehicles, vehicle state). |
| `demo.py` | `data/DemoDataProvider.kt`: the same simulator, emitting snapshots that run through the same analyzer. Choose **Explore with Demo Vehicle** to try it without an account. |

Not ported: the HTML report itself (the app's screens replace it), the 24-hour activity strip, and the drive-watch file.

## Screens

- **Overview**: battery gauge (SoC, range, charge limit, live charging power), location, odometer, last sync, and stat cards.
- **Drives**: per-trip miles, kWh, mi/kWh, Wh/mi, average speed and ambient temperature; tap a drive for its route map; short drives rolled up per day.
- **Charging**: home AC, public AC and DC fast sessions; tap an away session to record what you paid per kWh.
- **Savings**: lifetime and per-session savings vs a gas SUV at each charge day's AAA price.
- **Battery**: night-by-night drain, wake-ups, abnormal nights, the daily SoC curve and pack capacity.
- **Settings**: home location (or "use the car's current location"), home $/kWh, comparison MPG, AAA state and metro, usable battery kWh, polling mode, link/unlink.

## Install

A manual run of the **Android APK** workflow (Actions, *Run workflow*) publishes a GitHub Release `android-v1.0.N` with the APK attached. Pushes and pull requests that touch `android/` build the APK as a workflow artifact. On the phone (Android 8.0+), open the `.apk` and allow *Install unknown apps* when prompted.

After linking an account: allow notifications (Continuous mode), set your home location so home sessions use your rate, and set the AAA state and metro for your area.

### Release signing (optional)

Without signing secrets the APK is signed with the CI runner's debug key, so each build may need an uninstall before installing the next. For in-place updates, add these repository secrets:

| Secret | Value |
|---|---|
| `WATTSON_KEYSTORE_BASE64` | `base64 -w0 wattson-release.jks` output |
| `WATTSON_KEYSTORE_PASSWORD` | Keystore password |
| `WATTSON_KEY_ALIAS` | Key alias (e.g. `wattson`) |
| `WATTSON_KEY_PASSWORD` | Key password |

Create the keystore once with `keytool -genkeypair -v -keystore wattson-release.jks -alias wattson -keyalg RSA -keysize 4096 -validity 10000` and never commit it.

## Build locally

Requires JDK 21 and the Android SDK (platform 36).

```bash
cd android
./gradlew testDebugUnitTest lintDebug assembleDebug   # app/build/outputs/apk/debug/app-debug.apk
```

Tech stack: Kotlin 2.2.21, Jetpack Compose with Material 3, Coroutines and StateFlow, WorkManager, Gradle 9.3.1 with Android Gradle Plugin 9.1.1, minSdk 26, targetSdk 36.

## Known limitations

- History starts when you link: Rivian keeps no trip or charge history, so the app builds it from its own polls.
- Battery saver and Android's Doze mode can delay polls, so drives may be bracketed (start time and speed estimated) and nights without a poll near 10 PM and 7 AM are skipped.
- Not affiliated with Rivian. Sign-in uses Rivian's unofficial API and may break if it changes.
