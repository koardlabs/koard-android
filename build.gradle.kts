plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.koard.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.koard.android"
        minSdk = 31
        targetSdk = 36
        versionCode = 102
        versionName = "1.0.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

        debug {
            isDebuggable = true
        }
    }
    flavorDimensions += "environment"

    productFlavors {
        create("uat") {
            dimension = "environment"
            isDefault = true
            buildConfigField("String", "ENVIRONMENT", "\"UAT\"")
            buildConfigField("String", "API_KEY", "\"YOUR_API_KEY\"")
            applicationIdSuffix = ".uat"
        }

        create("prod") {
            dimension = "environment"
            buildConfigField("String", "ENVIRONMENT", "\"PROD\"")
            buildConfigField("String", "API_KEY", "\"YOUR_API_KEY\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    kotlinOptions {
        jvmTarget = "21"
    }

    packagingOptions {
        // exclude duplicate files from apache http client package
        exclude("META-INF/DEPENDENCIES")
    }
}

androidComponents {
    beforeVariants { variantBuilder ->
        val isProdDebug = variantBuilder.buildType == "debug" &&
            variantBuilder.productFlavors.any { it.second == "prod" }
        if (isProdDebug) {
            variantBuilder.enable = false
        }
    }
}

dependencies {
    // Koard Android SDK - uses published artifact from local Maven repo (demo/libs-maven)
    // Run ./publish-sdk-locally.sh after making SDK changes
    implementation("com.koardlabs:koard-android-sdk:1.0.5")

    implementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.ui.tooling.preview)

    implementation(libs.timber)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
