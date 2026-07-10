# Koard Android SDK Demo App

This Jetpack Compose application exercises the Koard Merchant SDK end to end: authentication, location selection, device enrollment, and NFC transaction flows including sale, preauth, capture, refund (API and EMV), reversal, tip adjustment, and digital receipts.

## Prerequisites

- Android Studio Hedgehog (or newer) with the Android SDK 36 platform installed
- JDK 21
- Physical Android 12+ device with NFC hardware
- Visa Tap to Pay Ready app installed on the device (minimum version **26.06.10**)
- Koard merchant credentials (API key from Koard dashboard)

## Quick Start

1. **Configure credentials**
   Edit `build.gradle.kts` and replace the placeholder API key in each product flavor:
   ```kotlin
   buildConfigField("String", "API_KEY", "\"YOUR_API_KEY\"")
   ```
   UAT and PROD flavors can point to different API keys and server environments.

2. **Build & install**
   ```bash
   ./gradlew assembleUatDebug
   ./gradlew installUatDebug
   ```
   Alternatively, open this project directory directly in Android Studio and use the standard Run/Debug targets.

3. **Launch the app and follow the flow**
   - Log in with your merchant code and PIN on the login screen.
   - Use **Select Location** to choose an available merchant location.
   - The SDK automatically enrolls the device after login and location selection.
   - **Disable developer mode** on the device before processing transactions.
   - Start a transaction from the home screen.

## Build Flavors

| Flavor | Environment | Application ID |
|--------|-------------|----------------|
| `uat`  | UAT/Testing | `com.koard.android.uat` |
| `prod` | Production  | `com.koard.android` |

Prod debug builds are disabled — use `prodRelease` for production.

## Project Structure

- `settings.gradle.kts` -- resolves the Koard SDK (`com.koard:koard-android-sdk`) from `mavenCentral()`; no credentials required.
- `libs-maven/` -- the release artifacts (fat AAR bundled with the KiC thin client, plus POM/metadata); the source of truth that `scripts/publish-maven-central.sh` publishes to Maven Central.
- `scripts/publish-maven-central.sh` -- publishes the prebuilt SDK artifacts under `libs-maven/` to Maven Central (requires a Central token and a GPG signing key).
- `build.gradle.kts` -- Compose-based Android app with `uat` and `prod` flavors.
- `src/main/java/com/koard/android/` -- Jetpack Compose UI:
  - `MainActivity.kt` -- NFC lifecycle registration (`onResume`/`onPause`)
  - `DemoApplication.kt` -- SDK initialization
  - `ui/MainScreen.kt` -- Home screen for initiating transactions
  - `ui/TransactionDetailsScreen.kt` -- Transaction detail with refund/capture/reverse/adjust
  - `ui/TransactionHistoryScreen.kt` -- Transaction list
  - `ui/SettingsScreen.kt` -- Enrollment status, location management, device info
  - `navigation/` -- Tab-based navigation (History, Home, Settings)

## Manifest Permissions

The following permissions are required:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.REBOOT" />
<uses-permission android:name="android.permission.NFC" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
```

If using `android:screenOrientation="portrait"`, also add:
```xml
<uses-feature android:name="android.hardware.screen.portrait" android:required="false"/>
```
This prevents Google Play from filtering out POS devices (Sunmi D3, iMin Swan 1 Pro, etc.).

## Updating the SDK

To publish and consume a newer version of the Koard SDK:

1. Drop the new artifacts (AAR, POM, metadata) into `libs-maven/com/koardlabs/koard-android-sdk/<version>/`.
2. Publish them to Maven Central (requires a Central token and GPG signing key):
   ```bash
   ./scripts/publish-maven-central.sh <version>
   ```
   The published coordinate is `com.koard:koard-android-sdk:<version>`.
3. Bump the version in `build.gradle.kts`:
   ```kotlin
   implementation("com.koard:koard-android-sdk:<version>")
   ```
4. Rebuild: `./gradlew assembleUatDebug`

## Troubleshooting

- **SDK dependency not found**: Ensure `mavenCentral()` is in your `settings.gradle.kts` repositories and that the version referenced in `build.gradle.kts` exists on Maven Central (see *Updating the SDK*).
- **Enrollment errors**: Confirm you are authenticated, have selected a location, and the Visa Tap to Pay Ready app is installed (minimum version 26.06.10).
- **NFC not available**: Verify the device has NFC hardware and that enrollment has completed. The readiness banner on the home screen will highlight missing prerequisites.
- **"Could not load enrolment / txn config blobs" (error 17)**: This usually means stale enrollment data from a previous merchant. Log out and log back in -- the SDK clears enrollment state on logout. If the error persists, unenroll the device from the Settings screen and re-enroll.
- **Developer mode**: Tap to Pay transactions will fail if developer mode is enabled. Disable it before processing payments: **Settings > System > Developer Options > OFF**.
- **Transaction failures**: Check Logcat filtered by `com.koardlabs` for detailed SDK logs via Timber.
