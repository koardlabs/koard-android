# Koard Android SDK Demo App

This Jetpack Compose application exercises the Koard Merchant SDK end to end: authentication, location selection, device enrollment, and sample NFC transaction flows. The demo is designed to run independently from the SDK source yet stay in lockstep through a local Maven repository.

## Prerequisites

- Android Studio Hedgehog (or newer) with the Android SDK 36 platform installed
- JDK 21
- Physical Android 12+ device with NFC hardware
- Visa Kernel (KiC) app installed on the device
- Koard merchant credentials (API key, merchant code, merchant PIN)

## Quick Start

1. **Configure credentials**
   Edit `build.gradle.kts` and replace the placeholder values in each product flavor:
   ```kotlin
   buildConfigField("String", "API_KEY", "\"YOUR_API_KEY\"")
   buildConfigField("String", "MERCHANT_CODE", "\"YOUR_MERCHANT_CODE\"")
   buildConfigField("String", "MERCHANT_PIN", "\"YOUR_MERCHANT_PIN\"")
   ```
   UAT and PROD flavors can point to different credentials or server environments.

2. **Build & install**
   ```bash
   # Run inside this directory
   ./gradlew assembleUatDebug
   ./gradlew installUatDebug
   ```
   Alternatively, open this project directory directly in Android Studio and use the standard Run/Debug targets.

3. **Launch the app and follow the flow**
   - Tap **Authenticate Merchant** to log in with the flavor-specific credentials.
   - Use **Select Location** to choose an available merchant location (required before enrollment).
   - Tap **Enroll Device**; the SDK now throws a descriptive error if no active location is set.
   - Once enrollment succeeds, start a sample NFC transaction from the home screen.

## Project Structure

- `settings.gradle.kts` – configures this Gradle project to pull dependencies from the bundled `libs-maven/` directory.
- `libs-maven/` – self-contained local Maven repository that houses the Koard SDK artifacts (fat AAR + KiC dependencies).
- `build.gradle.kts` – Compose-based Android app with `uat` and `prod` flavors (prod debug builds are disabled by `androidComponents`).
- `src/` – Jetpack Compose UI, including `MainScreen` (readiness dashboard) and `SettingsScreen` (certificate + enrollment management).

## Updating the SDK Dependency

To point the demo at a newer Koard SDK build, replace the artifacts inside `libs-maven/com/koardlabs/koard-android-sdk/` with the updated version (maintaining the existing directory structure and metadata). After copying the new files, rebuild the app (`./gradlew assembleUatDebug`) so Gradle resolves the refreshed dependency.

## In-App Tips

- The home screen surfaces `sdk.readinessState` messages (certificate state, enrollment, location status). Watch this area to confirm the sequence before processing NFC transactions.
- Use the **Settings** tab to clear enrollment data or regenerate certificates when testing edge cases.
- Enrollment requires a location; if the button is disabled or you see “No active location set,” pick a location through the sheet first.

## Troubleshooting

- **SDK dependency not found**: Ensure `libs-maven` exists and contains the `com/koardlabs/koard-android-sdk` artifacts. Rerun `./publish-sdk-locally.sh`.
- **Enrollment errors**: Confirm you are authenticated, have selected a location, and the Visa Kernel app is installed. Errors are surfaced in-app and in Logcat via Timber.
- **NFC not available**: Verify the device has NFC hardware and that `sdk.isNfcSupported` is true after enrollment. The readiness banner will highlight missing prerequisites.

© Koard Labs. All rights reserved.
