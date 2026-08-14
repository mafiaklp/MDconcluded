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
        versionCode = 20
        versionName = "1.2.0"
    }

    buildTypes {
        release { isMinifyEnabled = false }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
