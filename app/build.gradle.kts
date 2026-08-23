plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android { namespace = "com.h2pro.app"; compileSdk = 35
    defaultConfig { applicationId = "com.h2pro.app"; minSdk = 23; targetSdk = 35; versionCode = 1; versionName = "1.0" }
}

kotlin { jvmToolchain(17) }
