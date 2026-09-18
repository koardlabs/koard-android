# Koard Android SDK Demo App

This Jetpack Compose application exercises the Koard Merchant SDK end to end: authentication, location selection, device enrollment, and NFC transaction flows including sale, preauth, capture, refund (API and EMV), reversal, tip adjustment, and digital receipts.

SDK **1.0.7** fixes login reflection failures in minified apps and adds transaction metadata, `deinit()`, and `isInitialized()`. See the [changelog](CHANGELOG.md#107--2026-09-18) and examples below.

## Prerequisites

- Android Studio Hedgehog (or newer) with the Android SDK 36 platform installed
- JDK 21
- Physical Android 13+ (API 33+) device with NFC hardware
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

The demo explicitly initializes the SDK with `KoardLogLevel.NONE`. Release
builds are minified and non-debuggable, and do not plant a Timber debug logger.

## Project Structure

- `settings.gradle.kts` -- resolves SDK 1.0.7 from the checked-in `libs-maven/` release mirror; other dependencies come from Google and Maven Central.
- `build.gradle.kts` -- Compose-based Android app with `uat` and `prod` flavors.
- `src/main/java/com/koard/android/` -- Jetpack Compose UI:
  - `MainActivity.kt` -- NFC lifecycle registration (`onResume`/`onPause`)
  - `DemoApplication.kt` -- SDK initialization, serialized teardown/reinitialization, and NFC lifecycle coordination
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

The demo includes the complete signed 1.0.7 release under
[`libs-maven/com/koard/koard-android-sdk/1.0.7/`](libs-maven/com/koard/koard-android-sdk/1.0.7/),
including the AAR, POM, signatures, and checksums. It builds directly from this
mirror without requiring a Maven Central upload. The artifact uses the same
`com.koard` coordinate as Maven Central; Java/Kotlin imports remain
`com.koardlabs.merchant.sdk`.

For a later SDK version, update the dependency and the mirror/version filter in
`settings.gradle.kts`, or remove that filter to resolve a published version from
Maven Central:

```kotlin
implementation("com.koard:koard-android-sdk:1.0.7")
```
Upgrading from 1.0.6 requires a clean rebuild of the app and any native Flutter,
React Native, or other separately compiled SDK wrapper. Existing Kotlin calls
can omit the new optional metadata parameter, but payment method JVM signatures
have changed. Java integrations must pass the added metadata argument (`null` to
omit it). Exhaustive `when` expressions over `KoardReaderStatus` also need the newly added
reader states or an `else` branch. Login and enrollment call order is unchanged; using the new lifecycle
APIs is optional. Amounts remain `Int` values in minor currency units.

## Metadata in the demo

Sales, preauthorizations, API refunds, and tap refunds send `JSONObject` metadata
from `utils/TransactionMetadata.kt`. The example includes the local timestamp,
app version/build, device manufacturer/model, and Android version/API level. It
contains no merchant credentials, session tokens, or card data. Adapt the helper
to attach your own order identifiers or business context.

```kotlin
val metadata = JSONObject().put("order_id", "order-123")

// Run SDK calls on Dispatchers.IO and collect tap flows through completion.
sdk.sale(activity = activity, amount = 1500, metadata = metadata)
    .collect { response -> /* Render transaction state. */ }
sdk.refund(transactionId = transactionId, amount = 500, metadata = metadata)
    .getOrThrow()
sdk.refund(
    activity = activity,
    transactionId = transactionId,
    amount = 500,
    withTap = true,
    metadata = metadata
).collect { response -> /* Render transaction state. */ }
```

## SDK reset and session lifecycle

Settings shows `isInitialized()` and provides **Reset SDK and sign out**. This
calls `deinit()` on a worker thread, initializes a fresh SDK with the configured
key/environment, reattaches the foreground Activity for NFC, and returns to login.
The authenticated navigation stack is cleared so its ViewModels and readiness
collectors are recreated for the new session. SDK accessors resolve the current
instance rather than holding a torn-down instance.

Reset attempts reader unenrollment and clears the merchant session. Log in,
select a location, and enroll again before taking payment. Teardown is best
effort; a failed remote unenrollment does not prevent local cleanup. Reset is
refused while a tap transaction is active. Ordinary **Logout** keeps reader
enrollment and navigates only after logout completes.

```kotlin
withContext(Dispatchers.IO) {
    if (KoardMerchantSdk.isInitialized()) {
        KoardMerchantSdk.getInstance().deinit()
    }
    KoardMerchantSdk.initialize(application, apiKey, KoardEnvironment.UAT)
}
```

`isInitialized()` checks singleton availability; it does not indicate login,
enrollment, or payment readiness. Use SDK readiness for those checks.

## Minification, ProGuard, and R8

R8 shrinks, optimizes, and obfuscates an Android app when its release build enables
`isMinifyEnabled`. ProGuard rules tell it which code and reflection metadata must
survive. The host app's R8 pass also processes the SDK, even when the SDK AAR
itself was built without minification. Debug and release APKs can therefore behave
differently with the same API key and backend.

**SDK 1.0.6 had a consumer-rule packaging defect.** Retrofit needs generic type
information to prepare coroutine API calls. R8 could remove it, producing:

```text
java.lang.Class cannot be cast to java.lang.reflect.ParameterizedType
```

This can happen inside `login()` **before any HTTP request is sent**, leaving no
matching API log. The exception alone does not indicate invalid credentials or a
changed server response. The SDK's bundled dependencies use relocated package
names; their keep rules must use those same names. Also, bundled targeted R8 rules
could replace the AAR's ordinary rule file without retaining all SDK protections.

**1.0.7 fixes the AAR packaging and ships the complete consumer rules**, including
Retrofit generic signatures, coroutine continuations, response types, and relocated
dependency rules. Gradle applies them automatically when consuming the complete
AAR from Maven Central or as a local AAR. No extra Koard keep rules or disabling
R8 is required for this fix. Keep the AAR intact if you vendor it; extracting only
`classes.jar` also changes manifest, resource, and dependency handling.

The demo enables minification for release builds. Verify your own release variant:

```bash
./gradlew clean assembleUatRelease
# Or your app's production release task, e.g. assembleProdRelease.
```

Install the rebuilt APK and test login, location selection, and enrollment.
A successful build alone does not verify device behavior. If a failure remains,
capture the full native exception and cause chain, the build variant, SDK version,
and whether minification is enabled. Wrappers should preserve the native stack
trace in diagnostic logs instead of replacing it with only a generic failure label.
Never include merchant PINs, API keys, or session tokens in those logs.

See Android's [consumer keep-rule documentation](https://developer.android.com/topic/performance/app-optimization/library-optimization)
for how libraries supply rules to the consuming app.

## Troubleshooting

- **SDK dependency not found**: Ensure the checked-in `libs-maven` directory is present and its version matches the dependency and repository filter in `settings.gradle.kts` (see *Updating the SDK*).
- **Enrollment errors**: Confirm you are authenticated, have selected a location, and the Visa Tap to Pay Ready app is installed (minimum version 26.06.10).
- **NFC not available**: Verify the device has NFC hardware and that enrollment has completed. The readiness banner on the home screen will highlight missing prerequisites.
- **"Could not load enrolment / txn config blobs" (error 17)**: This usually means stale enrollment data from a previous merchant. `logout()` preserves device enrollment. Explicitly unenroll while the old merchant session is still available, then log out, sign in as the intended merchant, set its location, and re-enroll. See [SDK reset and session lifecycle](#sdk-reset-and-session-lifecycle).
- **Developer mode**: Tap to Pay transactions will fail if developer mode is enabled. Disable it before processing payments: **Settings > System > Developer Options > OFF**.
- **Transaction failures**: Opt in to SDK logging with `initialize(..., logLevel = KoardLogLevel.DEBUG)` and capture the `KoardSDK` Logcat tag plus the full native exception. SDK release logging is off by default. Preserve the native exception and cause chain, along with your release `mapping.txt`.
