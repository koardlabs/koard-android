package com.payroc.terminal

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
                    else -> KoardEnvironment.Custom( // UAT
                        koardApiUrl = "https://development-160452576009.us-central1.run.app",
                        enrollmentUrl = "https://development-160452576009.us-central1.run.app",
                        visaCloudPosUrl = "https://sandbox.cloudpos.digital.visa.com",
                        name = "UAT"
                    )
                }

                KoardMerchantSdk.initialize(
                    application = this@DemoApplication,
                    apiKey = BuildConfig.API_KEY,
                    environment = environment
                )
            }
        }
    }
}
