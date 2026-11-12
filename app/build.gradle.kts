plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

dependencies {
    implementation("androidx.work:work-runtime-ktx:2.10.1")
}

android {
    namespace = "com.phoneguard.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.phoneguard.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}
