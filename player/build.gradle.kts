plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.molina.player"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
        ndk { abiFilters += "arm64-v8a" }
        buildConfigField("String", "VERSION_NAME", "\"molina-player-ebf1b3f\"")
        buildConfigField("int", "VERSION_CODE", "1")
        buildConfigField("String", "FLAVOR", "\"default\"")
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
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.media:media:1.8.0")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("com.google.android.material:material:1.14.0")
    implementation(project(":core-common"))
    implementation(project(":core-security")) // molina-resolver: UrlPolicy
    implementation(project(":core-ui")) // molina-picker: ikon dan warna sama dengan file manager
}
