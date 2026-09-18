package com.koard.android.utils

import android.os.Build
import com.koard.android.BuildConfig
import org.json.JSONObject
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/** Example metadata: mirrors the Kotlin demo without credentials or card data. */
fun transactionMetadata(): JSONObject = JSONObject()
    .put("local_time", OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
    .put("app_version", BuildConfig.VERSION_NAME)
    .put("app_build", BuildConfig.VERSION_CODE)
    .put("device_manufacturer", Build.MANUFACTURER)
    .put("device_model", Build.MODEL)
    .put("os_name", "Android")
    .put("os_version", Build.VERSION.RELEASE)
    .put("os_sdk", Build.VERSION.SDK_INT)
