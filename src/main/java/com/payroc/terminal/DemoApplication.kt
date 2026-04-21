package com.koard.android

import android.app.Application
import com.koardlabs.merchant.sdk.KoardMerchantSdk
import com.koardlabs.merchant.sdk.domain.KoardEnvironment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import timber.log.Timber

class DemoApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        Timber.plant(Timber.DebugTree())

        /* INITIALIZE THE SDK ON APPLICATION STARTUP */
        runBlocking {
            // Ensure that SDK is initialized before onCreate is finished
            withContext(Dispatchers.IO) {
                val environment = when (BuildConfig.ENVIRONMENT) {
                    "PROD" -> KoardEnvironment.PROD
                    "UAT" -> KoardEnvironment.UAT
                    else -> KoardEnvironment.UAT
                }

                KoardMerchantSdk.initialize(
                    application = this@DemoApplication,
                    apiKey = BuildConfig.API_KEY,
                    environment = environment,
                    timeoutSeconds = 30L
                )
            }
        }
    }
}
