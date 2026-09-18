package com.koard.android

import android.app.Application
import com.koardlabs.merchant.sdk.KoardMerchantSdk
import com.koardlabs.merchant.sdk.domain.KoardEnvironment
import com.koardlabs.merchant.sdk.domain.KoardLogLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

class DemoApplication : Application() {
    private val sdkMutex = Mutex()

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())

        runBlocking { initializeSdk() }
    }

    suspend fun initializeSdk() = withContext(Dispatchers.IO) {
        sdkMutex.withLock {
            if (!KoardMerchantSdk.isInitialized()) initializeSdkInstance()
        }
    }

    /** Explicit reader/session teardown. Ordinary logout preserves enrollment. */
    suspend fun resetSdk() = withContext(Dispatchers.IO) {
        sdkMutex.withLock {
            if (KoardMerchantSdk.isInitialized()) {
                val sdk = KoardMerchantSdk.getInstance()
                check(!sdk.hasActiveTapTransaction()) {
                    "Finish or cancel the current transaction before resetting the SDK."
                }
                sdk.deinit()
            }
            initializeSdkInstance()
        }
    }

    // Serialize Activity NFC callbacks with deinit/reinitialization so a callback
    // cannot use the singleton while it is being replaced.
    suspend fun withInitializedSdk(action: suspend (KoardMerchantSdk) -> Unit) =
        withContext(Dispatchers.IO) {
            sdkMutex.withLock {
                if (KoardMerchantSdk.isInitialized()) action(KoardMerchantSdk.getInstance())
            }
        }

    private fun initializeSdkInstance() {
        val environment = when (BuildConfig.ENVIRONMENT) {
            "PROD" -> KoardEnvironment.PROD
            else -> KoardEnvironment.UAT
        }
        KoardMerchantSdk.initialize(
            application = this,
            apiKey = BuildConfig.API_KEY,
            environment = environment,
            timeoutSeconds = 30L,
            logLevel = KoardLogLevel.NONE
        )
    }
}
