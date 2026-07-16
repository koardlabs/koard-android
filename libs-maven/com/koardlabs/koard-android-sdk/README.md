# Koard Android SDK

The Koard Android SDK enables Android applications to accept contactless payments using Tap to Pay on Android technology. This SDK provides comprehensive merchant authentication, transaction processing, location management, and digital receipt capabilities.

## Features

- **Tap to Pay on Android**: Accept contactless payments using NFC technology powered by Visa Cloud POS
- **Merchant Authentication**: Secure login and session management
- **Transaction Processing**: Support for sales, preauthorizations, captures, and refunds
- **Location Management**: Multi-location merchant support with active location selection
- **Transaction History**: Retrieve and filter transaction records
- **Digital Receipts**: Send receipts via email or SMS
- **Error Handling**: Structured error handling with detailed error types
- **Multi-Environment Support**: Development, UAT, and Production environments

## Installation

### AAR (Recommended)

Integrate the SDK by adding the Koard fat AAR (which already bundles Visa KiC dependencies) to your project-level Maven repository or `libs/` folder.

1. Copy the provided `koard-android-<env>-release.aar` into a local Maven repo (for example, `your-project/libs-maven`) or your app module's `libs/` directory.

2. Point Gradle to that repository in `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        maven {
            url = uri("$rootDir/libs-maven")
        }
        google()
        mavenCentral()
    }
}
```

3. Declare the dependency in your app module `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.koardlabs:koard-android-sdk:0.0.1")
}
```

> If you prefer placing the AAR directly in `app/libs/`, replace the Maven reference with `implementation(files("libs/koard-android-dev-release.aar"))`.

## Requirements

- **Minimum SDK**: Android 12 (API level 31)
- **Target SDK**: Android 15 (API level 36)
- **Java Version**: 21
- **Kotlin**: 1.9.0 or higher
- **Dependencies**:
  - AndroidX Core KTX
  - Kotlin Coroutines
  - Retrofit 2 with Kotlin Serialization

## Implementation Guide

### Step 1: SDK Initialization

Initialize the SDK in your `Application.onCreate()` method. **Important**: Initialization must be performed on a worker thread.

```kotlin
import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.koardlabs.merchant.sdk.KoardMerchantSdk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        ProcessLifecycleOwner.get().lifecycleScope.launch(Dispatchers.IO) {
            try {
                KoardMerchantSdk.initialize(
                    application = this@MyApplication,
                    apiKey = "your-api-key",
                    merchantCode = "your-merchant-code",
                    merchantPin = "your-merchant-pin"
                )

                // SDK is ready to use
                val sdk = KoardMerchantSdk.getInstance()
            } catch (e: Exception) {
                // Handle initialization error
            }
        }
    }
}
```

### Step 2: Merchant Authentication

Authenticate the merchant before performing any payment operations:

```kotlin
import com.koardlabs.merchant.sdk.KoardMerchantSdk
import com.koardlabs.merchant.sdk.domain.exception.KoardException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun loginMerchant(): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            val sdk = KoardMerchantSdk.getInstance()
            val success = sdk.login()

            if (success) {
                // Login successful, SDK is ready for transactions
                println("Login successful")
            }
            success
        } catch (e: KoardException) {
            // Handle authentication error
            println("Login failed: ${e.error.shortMessage}")
            false
        }
    }
}
```

### Step 3: Location Setup

For merchants with multiple locations, retrieve and set the active location:

```kotlin
import com.koardlabs.merchant.sdk.domain.KoardLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun setupLocation() {
    withContext(Dispatchers.IO) {
        val sdk = KoardMerchantSdk.getInstance()

        // Get all merchant locations
        sdk.getLocations()
            .onSuccess { locations ->
                if (locations.isNotEmpty()) {
                    val location = locations.first()

                    // Persist the selection on a worker thread
                    sdk.setActiveLocation(location.id)

                    // Retrieve the ID for auditing/UI purposes
                    val activeLocationId = sdk.activeLocationId
                    println("Active location ID: $activeLocationId")
                }
            }
            .onFailure { error ->
                println("Failed to retrieve locations: $error")
            }
    }
}
```

### Step 4: Device Readiness & Enrollment

Subscribe to the SDK's readiness flow to mirror certificate, enrollment, and thin-client states in your UI:

```kotlin
val sdk = KoardMerchantSdk.getInstance()

lifecycleScope.launch {
    sdk.readinessState.collect { readiness ->
        if (readiness.hasBlockingIssue) {
            showError(readiness.getStatusMessage())
        }

        if (!readiness.hasActiveLocation) {
            promptLocationSelection()
        }
    }
}
```

`setActiveLocation(locationId: String)` must be called (and succeed) before enrolling. `enrollDevice()` throws a `KoardException` if no location is active. Use the readiness state to surface these requirements to the end user.

#### Device enrollment

After `login()` succeeds and a location is active, call `enrollDevice()`:

```kotlin
lifecycleScope.launch(Dispatchers.IO) {
    val sdk = KoardMerchantSdk.getInstance()
    try {
        val message = sdk.enrollDevice()
        Timber.d(message)
    } catch (e: KoardException) {
        Timber.e(e, "Enrollment failed: ${e.error.shortMessage}")
    }
}
```

### Step 5: Register Activity for NFC

Register your payment activity to handle NFC events:

```kotlin
import android.app.Activity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PaymentActivity : AppCompatActivity() {

    override fun onResume() {
        super.onResume()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val sdk = KoardMerchantSdk.getInstance()
                sdk.registerActivityForNfc(this@PaymentActivity)
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    override fun onPause() {
        super.onPause()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val sdk = KoardMerchantSdk.getInstance()
                sdk.unregisterActivityForNfc(this@PaymentActivity)
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
}
```

### Step 6: Transaction Processing

<!-- TODO: Add sale transaction example once implemented -->

#### Preauthorization

<!-- TODO: Add preauthorization example once implemented -->

```kotlin
// Placeholder for preauthorization implementation
// suspend fun createPreauth(amount: Int): Result<KoardTransaction>
```

#### Capture

Capture a previously authorized transaction:

```kotlin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun captureTransaction(transactionId: String) {
    withContext(Dispatchers.IO) {
        // TODO: Expose capturePayment in KoardMerchantSdk
        // val sdk = KoardMerchantSdk.getInstance()
        // sdk.capturePayment(transactionId)
    }
}
```

### Step 7: Transaction Management

#### Refund a Transaction

```kotlin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun refundTransaction(transactionId: String, amount: Int? = null) {
    withContext(Dispatchers.IO) {
        val sdk = KoardMerchantSdk.getInstance()

        // Refund full amount if amount is null, or partial amount
        sdk.refundTransaction(
            transactionId = transactionId,
            amount = amount, // Amount in cents, or null for full refund
            eventId = java.util.UUID.randomUUID().toString()
        )
            .onSuccess { transaction ->
                println("Refund successful: ${transaction.id}")
            }
            .onFailure { error ->
                println("Refund failed: $error")
            }
    }
}
```

#### Reverse a Transaction

<!-- TODO: Add reverse transaction example once exposed in SDK -->

```kotlin
// Placeholder for reverse implementation
// suspend fun reverseTransaction(transactionId: String)
```

#### Adjust Tip

<!-- TODO: Add tip adjustment example once exposed in SDK -->

```kotlin
// Placeholder for tip adjustment implementation
// suspend fun adjustTip(transactionId: String, tipAmount: Int)
```

### Step 8: Transaction History

Retrieve transaction history:

```kotlin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun getTransactionHistory() {
    withContext(Dispatchers.IO) {
        val sdk = KoardMerchantSdk.getInstance()

        sdk.getTransactions()
            .onSuccess { transactionDetails ->
                println("Total transactions: ${transactionDetails.total}")
                transactionDetails.transactions.forEach { transaction ->
                    println("Transaction: ${transaction.id} - Amount: ${transaction.amount}")
                }
            }
            .onFailure { error ->
                println("Failed to retrieve transactions: $error")
            }
    }
}
```

Get a specific transaction:

```kotlin
suspend fun getTransactionDetails(transactionId: String) {
    withContext(Dispatchers.IO) {
        val sdk = KoardMerchantSdk.getInstance()

        sdk.getTransaction(transactionId)
            .onSuccess { transaction ->
                println("Transaction details: $transaction")
            }
            .onFailure { error ->
                println("Failed to retrieve transaction: $error")
            }
    }
}
```

### Step 9: Send Receipts

Send transaction receipts via email or SMS:

```kotlin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun sendTransactionReceipt(
    transactionId: String,
    email: String? = null,
    phoneNumber: String? = null
) {
    withContext(Dispatchers.IO) {
        val sdk = KoardMerchantSdk.getInstance()

        // Send to email, phone, or both
        sdk.sendReceipt(
            transactionId = transactionId,
            email = email,
            phoneNumber = phoneNumber
        )
            .onSuccess { response ->
                println("Receipt sent successfully")
            }
            .onFailure { error ->
                println("Failed to send receipt: $error")
            }
    }
}

// Usage examples:
// Send to email only
sendTransactionReceipt(transactionId, email = "customer@example.com")

// Send to phone only
sendTransactionReceipt(transactionId, phoneNumber = "+1234567890")

// Send to both
sendTransactionReceipt(
    transactionId,
    email = "customer@example.com",
    phoneNumber = "+1234567890"
)
```

### Step 10: Error Handling

The SDK uses structured error handling with `KoardException`:

```kotlin
import com.koardlabs.merchant.sdk.domain.exception.KoardException
import com.koardlabs.merchant.sdk.domain.KoardErrorType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun handleSdkOperation() {
    withContext(Dispatchers.IO) {
        try {
            val sdk = KoardMerchantSdk.getInstance()
            sdk.login()
        } catch (e: KoardException) {
            when (e.error.errorType) {
                is KoardErrorType.KoardServiceErrorType.ConnectionError -> {
                    // Network connectivity issue
                    println("No network connection: ${e.error.shortMessage}")
                }
                is KoardErrorType.KoardServiceErrorType.HttpError -> {
                    // HTTP error from the API
                    val httpError = e.error.errorType as KoardErrorType.KoardServiceErrorType.HttpError
                    println("API error (${httpError.code}): ${e.error.shortMessage}")
                }
                is KoardErrorType.KoardServiceErrorType.UnexpectedError -> {
                    // Unexpected error (parsing, etc.)
                    println("Unexpected error: ${e.error.shortMessage}")
                }
                is KoardErrorType.DeviceNotProvisionedError -> {
                    // Device needs to be provisioned
                    println("Device not provisioned: ${e.error.shortMessage}")
                }
                else -> {
                    println("Error: ${e.error.shortMessage}")
                }
            }
        } catch (e: IllegalStateException) {
            // SDK not initialized
            println("SDK not initialized: ${e.message}")
        }
    }
}
```

### Step 11: Session Management

Check authentication status:

```kotlin
fun checkAuthenticationStatus() {
    try {
        val sdk = KoardMerchantSdk.getInstance()

        if (sdk.isAuthenticated) {
            println("Merchant is authenticated")

            // Check NFC support
            if (sdk.isNfcSupported) {
                println("NFC payments are enabled")
            }
        } else {
            println("Merchant needs to login")
        }
    } catch (e: IllegalStateException) {
        println("SDK not initialized")
    }
}
```

### Step 12: Logout and Cleanup

Logout the merchant and clear session data:

```kotlin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun logoutMerchant() {
    withContext(Dispatchers.IO) {
        try {
            val sdk = KoardMerchantSdk.getInstance()
            sdk.logout()
            println("Logout successful")
        } catch (e: Exception) {
            println("Logout failed: ${e.message}")
        }
    }
}
```

## Complete Payment Workflows

<!-- TODO: Add complete workflow examples once sale transactions are implemented -->

### Workflow 1: Preauthorization → Capture

```kotlin
// TODO: Complete workflow example
```

### Workflow 2: Preauthorization → Incremental Authorization → Capture

```kotlin
// TODO: Complete workflow example with incremental auth
```

### Workflow 3: Direct Sale

```kotlin
// TODO: Complete workflow example for direct sale
```

## Best Practices

### Transaction Idempotency

Use unique event IDs (UUIDs) for transaction operations to ensure idempotency:

```kotlin
import java.util.UUID

suspend fun performIdempotentOperation() {
    val eventId = UUID.randomUUID().toString()

    sdk.refundTransaction(
        transactionId = "txn_123",
        amount = 1000, // $10.00 in cents
        eventId = eventId
    )
}
```

### Worker Thread Execution

All SDK operations must be executed on a worker thread (not the main UI thread):

```kotlin
// ✅ Correct: Using Dispatchers.IO
lifecycleScope.launch(Dispatchers.IO) {
    val sdk = KoardMerchantSdk.getInstance()
    sdk.login()
}

// ❌ Incorrect: Main thread execution will throw an error
lifecycleScope.launch(Dispatchers.Main) {
    sdk.login() // Will throw IllegalStateException
}
```

### Environment Configuration

The SDK supports three environments configured via build flavors:

```kotlin
// In build.gradle.kts
productFlavors {
    create("prod") {
        dimension = "environment"
        // Production: https://api.koard.com
    }
    create("uat") {
        dimension = "environment"
        // UAT: https://api.uat.koard.com
    }
    create("dev") {
        dimension = "environment"
        // Development: https://api.dev.koard.com
    }
}
```

Build for specific environment:
```bash
# Development
./gradlew assembleDevDebug

# UAT
./gradlew assembleUatDebug

# Production
./gradlew assembleProdRelease
```

## Troubleshooting

<!-- TODO: Add common issues and solutions -->

### Common Issues

#### SDK Not Initialized

**Error**: `IllegalStateException: Instance is null. Did you forget to call initialize?`

**Solution**: Ensure `KoardMerchantSdk.initialize()` is called in your `Application.onCreate()` on a worker thread before accessing `getInstance()`.

#### Thread Enforcement Error

**Error**: Operations failing with thread-related errors

**Solution**: All SDK operations must be called from a worker thread. Use `Dispatchers.IO` with coroutines:

```kotlin
lifecycleScope.launch(Dispatchers.IO) {
    // SDK operations here
}
```

#### NFC Not Supported

**Error**: `isNfcSupported` returns false

**Solution**:
1. Ensure `enableNfcTransactionsAsync()` has been called successfully
2. Verify device has NFC hardware capability
3. Check that Visa Kernel app is installed on the device

<!-- TODO: Add more troubleshooting scenarios -->

## Building the SDK

<!-- TODO: Add SDK development and building instructions -->

### Build Commands

```bash
# Clean build
./gradlew clean

# Build SDK library
./gradlew :merhcant-sdk:build

# Build specific flavor
./gradlew :merhcant-sdk:assembleDevDebug

# Run tests
./gradlew :merhcant-sdk:test
```

## Migration Guides

<!-- TODO: Add migration guides for version updates -->

## Error Types Reference

The SDK provides structured error handling through the `KoardErrorType` sealed class:

- `KoardServiceErrorType.ConnectionError`: Network connectivity issues
- `KoardServiceErrorType.HttpError`: HTTP errors with status codes
- `KoardServiceErrorType.UnexpectedError`: Parsing or unexpected errors
- `DeviceNotProvisionedError`: Device provisioning required

## API Reference

### KoardMerchantSdk

Main SDK class providing all payment functionality.

#### Initialization

- `initialize(application: Application, apiKey: String, merchantCode: String, merchantPin: String)`: Initialize the SDK
- `readinessState: StateFlow<KoardSdkReadiness>`: Observe certificates, enrollment, and thin-client readiness

#### Authentication

- `login(): Boolean`: Authenticate merchant
- `logout()`: Logout and clear session
- `isAuthenticated: Boolean`: Check authentication status

#### Location Management

- `getLocations(): Result<List<KoardLocation>>`: Retrieve all locations
- `setActiveLocation(locationId: String)`: Persist the active location ID (must run on worker thread)
- `activeLocationId: String?`: Retrieve the stored location ID

#### NFC Configuration

- `enrollDevice(): String`: Enrolls the device for tap-to-pay.
- `enableNfcTransactionsAsync(serverEncPub: String, serverAuthPub: String)`: Advanced enrollment entry point.
- `registerActivityForNfc(activity: Activity)`: Register activity for NFC events
- `unregisterActivityForNfc(activity: Activity)`: Unregister activity
- `isNfcSupported: Boolean`: Check NFC support status
- `isDeviceEnrolled: Boolean`: Check Visa enrollment state
- `isCertificateGenerated(): Boolean`: Check device certificates

#### Transaction Management

- `getTransactions(): Result<KoardTransactionDetails>`: Get transaction history
- `getTransaction(transactionId: String): Result<KoardTransaction>`: Get specific transaction
- `refundTransaction(transactionId: String, amount: Int?, eventId: String?): Result<KoardTransaction>`: Refund transaction

#### Receipts

- `sendReceipt(transactionId: String, email: String?, phoneNumber: String?): Result<PaymentOperationResponse>`: Send receipt

## Support

For technical support or questions:
- Email: support@koard.com
- Documentation: https://docs.koard.com

## License

<!-- TODO: Add license information -->

Copyright © 2025 Koard Labs. All rights reserved.
