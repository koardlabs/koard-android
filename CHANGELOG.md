# Changelog

All notable changes to the Koard Android SDK (`com.koardlabs:koard-android-sdk`)
are documented here. The format is based on [Keep a Changelog](https://keepachangelog.com),
and this project adheres to [Semantic Versioning](https://semver.org).

> **Upgrading from 1.0.5?** Read the **[Breaking changes](#breaking-changes)**
> section below — each item includes the migration step inline. This release
> contains source-breaking API changes and several
> behavioural changes (methods that used to fail silently now throw, and vice
> versa) — audit your `try/catch` and exhaustive `when` blocks.

## [Unreleased]

## [1.0.6]

This release hardens the SDK for public distribution. It isolates the SDK's
dependencies (shading), replaces the logging stack, tightens what is logged,
makes readiness/failure states explicit and typed (no more silent no-ops), and
removes a leaked third-party type from the public API. Most integrators will
only need the one-line `ButtonProperties` change to compile, but the
behavioural changes below can change runtime behaviour, so please read them.

### Breaking changes

Ordered by how likely you are to hit them.

1. **`KoardButtonProperties` replaces the leaked Visa type in the tap entry
   points.** _(Source-breaking — every integrator that shows an on-reader Cancel
   button hits this.)_
   `sale()`, `preauth()`, `completePartialAuth()`, `refundEmv()` and
   `startTransaction()` previously took
   `List<com.visa.kic.sdk.common.ipc.ButtonProperties>` — a raw Visa KiC
   internal type that should never have crossed the SDK boundary. They now take
   `List<KoardButtonProperties>?` (also now **nullable** — pass `null`/omit for
   the default reader UI).
   `KoardButtonProperties` is a drop-in with the same positional shape:
   `KoardButtonProperties(name: String, xPositionDp: Int, yPositionDp: Int, widthDp: Int, heightDp: Int)`.

2. **Sealed error hierarchies gained members — exhaustive `when` blocks will no
   longer compile.** _(Source-breaking if you `when` over the error type without
   an `else`.)_ `KoardErrorType` and its nested `KoardServiceErrorType` /
   `KicConnectorError` are `sealed`; the new members below make any exhaustive
   `when` non-exhaustive. Add the new branches or an `else`.
   New members:
   - `KoardErrorType.NotReady` and its subtypes: `KernelAppNotInstalled`,
     `DeveloperModeEnabled`, `NotAuthenticated`, `NotEnrolled`,
     `NoActiveLocation`, `ReaderNotStarted`, `Preparing`,
     `CertificateFailed(error)`, `EnrollmentFailed(error)`,
     `PaymentProcessorFailed(error)`.
   - `KoardErrorType.KoardServiceErrorType.UnparseableResponse`.
   - `KoardErrorType.KicConnectorError.KernelAppBusyWithAnotherMerchant`.

3. **Transaction entry points now throw a typed "not ready" reason instead of a
   generic error.** _(Behavioural + source.)_ When the SDK is not ready,
   `sale()`, `preauth()`, `completePartialAuth()` and `refundEmv()` now throw
   `KoardException` whose `error.errorType` is a
   `KoardErrorType.NotReady.*` value (previously a generic
   `KoardServiceErrorType.InvalidRequest`). Branch on the reason: retry on
   `NotReady.Preparing` (the reader is mid-preparation), and prompt the user on
   the terminal reasons (`DeveloperModeEnabled`, `NoActiveLocation`,
   `NotEnrolled`, …). `KoardSdkReadiness.notReadyReason()` exposes the same
   typed reason without attempting a transaction.

4. **`setActiveLocation()` is now backend-authoritative and throws on failure —
   it no longer silently no-ops.** _(Behavioural — wrap it in `try/catch`.)_
   When switching an **enrolled** device to a **different** location it now:
   - throws `KoardException(InvalidRequest)` if **developer mode is on** (the
     Visa kernel will not accept a new profile while developer mode is enabled —
     the switch is refused rather than half-applied);
   - re-binds the terminal on the backend first and **only commits the new
     location locally if the backend approves** — a failed re-bind throws and
     leaves the previous location intact (previously the local cache could move
     ahead of the backend);
   - marks readiness as `Preparing` for the duration, so a tap started
     mid-switch is rejected with `NotReady.Preparing` instead of running against
     a terminal that is still switching;
   - pushes the new location's kernel profile to completion (no timeout).

5. **`logout()` no longer clears device enrollment.** _(Behavioural.)_ Logout is
   now a **session-only** action: it clears the auth token and active location
   but intentionally preserves enrollment (certificates / `vacDeviceId` /
   terminal chain) so the same merchant logging back in does not have to
   re-enroll. **Consequence:** switching the device to a *different* merchant now
   requires an explicit `unenrollDevice()` first — otherwise the device still
   carries the previous merchant's enrollment and Visa KiC returns error 17
   ("Could not load enrolment / txn config blobs"). Call `unenrollDevice()` (or
   `clearEnrollmentState()` for local-only) when you actually intend to
   deprovision.

6. **Bundled dependencies are shaded and removed from the POM.** _(Build-time —
   only affects host apps that leaned on the SDK's transitive deps.)_ OkHttp,
   Okio, Retrofit, kotlinx.serialization and Ktor are now packaged **inside** the
   AAR, relocated under `com.koardlabs.*`, and dropped from the published POM
   (1.0.5 shipped them as ordinary transitive Maven dependencies). This isolates
   the SDK from whatever versions your app uses — no more version clashes — but
   if your app was **relying on the SDK to pull in** OkHttp/Retrofit/etc.
   transitively, you must now declare those yourself. Verified: the fat AAR
   assembles into a host APK with no duplicate-class conflicts.

7. **Binary-incompatible signatures — recompile against 1.0.6.** These are
   source-compatible for Kotlin callers (new parameters have defaults) but the
   bytecode signatures changed, so **you must recompile**, and **Java** callers
   must update call sites:
   - `initialize(application, apiKey, environment, timeoutSeconds)` →
     `initialize(application, apiKey, environment, timeoutSeconds, logLevel)`
     (new trailing `logLevel: KoardLogLevel`, defaulted).
   - `getTransactions()` → `getTransactions(limit: Int? = 50, offset: Int? = 0)`
     for offset paging.
   - `sale(...)` gained a trailing `transactionType: String = "Payment"`
     parameter (see Practice Mode, below).

### Added

- **Alias login** — `login(alias: String)`: sign in with a single opaque alias
  string (QR scan / SSO callback / server-issued provisioning token) instead of
  `login(merchantCode, merchantPin)`. Hits the same `v1/merchant/login` route and
  yields the same session token; the alias is sent on the wire only, never
  stored. (Parity with iOS `login(alias:)`.) The
  `login(merchantCode, merchantPin)` overload is retained.
- **Reactive active location** — `activeLocation: StateFlow<KoardLocation?>`
  plus `refreshActiveLocation(): KoardLocation?`. The resolved active-location
  object is now the single source of truth: updated on `setActiveLocation` and
  when the locations list is (re)fetched, and cleared on `logout`. The existing
  `activeLocationId: String?` is retained.
- **Explicit prepare flow** — `prepare(): Flow<KoardPrepareResponse>` with
  `KoardPrepareStatus`, for driving/observing reader preparation directly.
- **Transaction history pagination** — `getTransactions(limit, offset)` for
  offset-based paging of the active location's transactions.
- **Practice Mode / online-PIN validation** — `sale(...)` accepts
  `transactionType` (`"Payment"` for a real sale, `"TestWithPin"` for Practice
  Mode which forces the PIN-entry UI, KiC Annex A-2). Only valid when the
  enrollment's supported transaction types include it.
- **Terminal lookup** — `getTerminal(terminalId): Result<KoardTerminal>` and the
  new `KoardTerminal` model.
- **Kernel-app lifecycle helpers** — `installKernelApp(activity)` (route the
  user to install the Visa Tap to Pay kernel app), `resetKernelService():
  Result<Unit>`, and `hasActiveTapTransaction(): Boolean`.
- **Configurable logging** — `KoardLogLevel` enum
  (`VERBOSE`/`DEBUG`/`INFO`/`WARN`/`ERROR`/`NONE`), set once via the new
  `initialize(..., logLevel)` parameter.

### Changed

- **Logging stack replaced: Timber removed, `KoardLog` added.** The SDK no
  longer depends on or bundles Timber. All internal logging goes through
  `KoardLog` (backed by `android.util.Log`, tag `"KoardSDK"`), gated by the
  `KoardLogLevel` set at `initialize`. Logging is now possible in **release**
  builds when you opt in with a log level (previously release logs were stripped
  with Timber); the level is set **only** at `initialize` — there is
  intentionally **no runtime `setLogLevel`**. Defaults: `DEBUG` in debug builds,
  `NONE` in release. The SDK's OkHttp HTTP logging follows the same gate.
- **`setActiveLocation()` semantics** — see Breaking change #4.
- **Public tap API no longer leaks the Visa `ButtonProperties` type** — see
  Breaking change #1.

### Fixed

- **Unparseable payment responses no longer throw a raw serialization error —
  they fall back to the receipt.** A single shared safe-decode util is used
  across `auth`/`sale`/`refund`/partial-auth completion and the tappable events:
  when the structured body can't be decoded, the SDK falls back to the receipt
  payload and reports `KoardServiceErrorType.UnparseableResponse` instead of
  letting a `SerializationException` escape. This removes a class of
  "random unhandled exception on an otherwise successful tap" failures.
- **Enrollment desync self-heals.** Fixes the "device reports enrolled but has no
  `vacDeviceId`" state: on the KiC already-enrolled path the SDK now recovers
  `vacDeviceId` from the kernel's `init` extras and persists it, so a subsequent
  location switch / unenroll has the id it needs instead of silently failing.
- **Online-PIN transactions above the card-verification limit reliably present
  the PIN keypad** — the Visa KiC kernel bound-service is promoted to an Android
  foreground service for the duration of a payment so the OS cannot kill it
  mid-session.
- **`logout()` clears the active location** — a subsequent login/enrollment is no
  longer blocked by stale "no active location set" state. (Enrollment is
  preserved — see Breaking change #5.)
- **`AcquirerCustomInfo` is decoded with standard Base64** (not URL-safe Base64).

### Security

- **Secrets are never logged.** Device certificates and the device-enroll API
  response (which carries `xSecretKey`, `xViaHint`, `xRandomValue`,
  `deviceKeys`) are never written to Logcat, even at `VERBOSE`. HTTP request/
  response header logging is redacted (the API key header is not logged), and the
  API-key fingerprint is masked in `AcquirerCustomInfo` logging.
- **API-key rotation clears the session.** If a *different* API key is supplied
  to `initialize`, the SDK clears the cached session token and active location
  (enrollment is preserved). This prevents one merchant's session from carrying
  over to another. The API key itself is held in memory only (sent as the
  `x-koard-apikey` header) and is not persisted.

## [1.0.5]

- Prior public release. Dependencies (OkHttp, Retrofit, Timber, etc.) shipped as
  ordinary transitive Maven dependencies rather than shaded into the AAR. The tap
  entry points exposed the Visa `com.visa.kic.sdk.common.ipc.ButtonProperties`
  type directly. Logging was via Timber (stripped in release).
