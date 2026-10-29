plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "com.portableslam.slam"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "com.portableslam.slam"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".probe"
            versionNameSuffix = "-probe"
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        // This research build intentionally targets arm64 Android phones, not ChromeOS.
        disable += "ChromeOsAbiSupport"
    }
}

dependencies {
    implementation("com.google.ar:core:1.57.0")
    testImplementation("junit:junit:4.13.2")
}
