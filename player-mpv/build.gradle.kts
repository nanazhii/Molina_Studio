// molina-p12
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}
android {
    namespace = "com.molina.player.mpv"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
        buildConfigField("String", "VERSION_NAME", "\"2026-09-17-release\"") // versi upstream yang divendor
        buildConfigField("int", "VERSION_CODE", "48")
        consumerProguardFiles("proguard-rules.pro")
        ndk { abiFilters += "arm64-v8a" }
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.media)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.material)
}
