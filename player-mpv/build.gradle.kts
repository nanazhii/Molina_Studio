plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}
android {
    namespace = "com.molina.player"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
        consumerProguardFiles("proguard-rules.pro") // keep MPVLib (JNI)
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
    lint { abortOnError = false }
}
dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.media)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.material)
    api(libs.kotlinx.coroutines.android)
    implementation(project(":core-common"))
    implementation(project(":core-log"))
    implementation(project(":core-security"))
    implementation(project(":core-stream"))
    testImplementation(libs.junit)
}
