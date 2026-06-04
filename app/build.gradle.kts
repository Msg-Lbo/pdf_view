plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val releaseKeystoreFile = file("release.keystore")
val hasReleaseSigning = releaseKeystoreFile.exists() &&
    !providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").orNull.isNullOrBlank() &&
    !providers.environmentVariable("ANDROID_KEY_ALIAS").orNull.isNullOrBlank()
val fallbackKeystoreFile = file("fallback-release.keystore")

android {
    namespace = "com.lightread.pdfreader"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.lightread.pdfreader"
        minSdk = 23
        targetSdk = 35
        versionCode = 5
        versionName = "0.2.1"
    }

    buildFeatures {
        buildConfig = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    signingConfigs {
        create("release") {
            if (hasReleaseSigning) {
                storeFile = releaseKeystoreFile
                storePassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("ANDROID_KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("ANDROID_KEY_PASSWORD").orNull
                    ?: providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").orNull
            }
        }
        create("fallbackRelease") {
            storeFile = fallbackKeystoreFile
            storePassword = "android"
            keyAlias = "fallback"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("fallbackRelease")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

dependencies {
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
}
