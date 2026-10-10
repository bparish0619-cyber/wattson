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

## License

MIT
