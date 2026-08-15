plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.brewtap.xbloom"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.brewtap.xbloom"
        minSdk = 26
        targetSdk = 35
        versionCode = 51
        versionName = "1.5.1"
    }

    signingConfigs {
        create("stableDevelopment") {
            storeFile = rootProject.file("signing/brewtap-test.keystore")
            storePassword = "brewtap2026"
            keyAlias = "brewtap"
            keyPassword = "brewtap2026"
        }
    }

    buildTypes {
        debug { signingConfig = signingConfigs.getByName("stableDevelopment") }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("stableDevelopment")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("com.google.mlkit:text-recognition:16.0.1")
    testImplementation("junit:junit:4.13.2")
}
