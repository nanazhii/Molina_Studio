plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}
// Nomor commit pendek untuk pelacakan APK CI; build di luar CI memakai "dev".
val commitSha: String = providers.environmentVariable("GITHUB_SHA").orElse("").get().take(7).ifEmpty { "dev" }

android {
    namespace = "com.molina.settings"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
        buildConfigField("String", "COMMIT_SHA", "\"$commitSha\"")
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(project(":core-ui"))
    implementation(project(":core-log"))
}
