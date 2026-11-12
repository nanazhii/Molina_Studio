plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}
android {
    namespace = "com.molina.cinema"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.molina.cinema"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        ndk { abiFilters += "arm64-v8a" }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(project(":core-ui"))
    implementation(project(":core-log"))
    implementation(project(":mpv-native"))
    implementation(libs.kotlinx.coroutines.android)
}
