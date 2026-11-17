plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}
android {
    namespace = "com.molina.code"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    lint { abortOnError = false }
}
dependencies {
    implementation(project(":core-common"))
    implementation(project(":core-ui")) // molina-29: FileKind + TreeIconPalette bersama (module_gate: feature -> core)
    implementation(libs.androidx.fragment.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.activity.compose)
    implementation(libs.sora.editor)
    implementation(libs.sora.language.textmate)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit) // [MINT-TREE]
}
