plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}
android {
    namespace = "com.molina.studio"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.molina"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        ndk { abiFilters += "arm64-v8a" }
    }
    signingConfigs {
        create("molina") {
            val ksFile = System.getenv("MOLINA_KEYSTORE_FILE")
            val ksPass = System.getenv("MOLINA_KEYSTORE_PASSWORD")
            if (!ksFile.isNullOrEmpty() && !ksPass.isNullOrEmpty()) {
                storeFile = file(ksFile)
                storeType = "pkcs12"
                storePassword = ksPass
                keyAlias = "molina"
                keyPassword = ksPass
            }
        }
    }
    buildTypes {
        debug {
            val cfg = signingConfigs.getByName("molina")
            if (cfg.storeFile != null) signingConfig = cfg
        }
        release {
            val cfg = signingConfigs.getByName("molina")
            if (cfg.storeFile != null) signingConfig = cfg
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions { jvmTarget = "17" }
    // molina-patch26k: commons-compress dan turunannya membawa berkas META-INF yang sama
    packaging {
        jniLibs { useLegacyPackaging = true; pickFirsts += "**/libc++_shared.so" } // molina-27r: lib7zz.so harus diekstrak agar bisa dieksekusi
        resources {
            excludes += setOf("META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/DEPENDENCIES", "META-INF/{AL2.0,LGPL2.1}")
        }
    }
}
dependencies {
    implementation(project(":core-ui"))
    implementation(project(":core-log"))
    implementation(project(":core-security"))
    implementation(project(":core-storage"))
    implementation(project(":files"))
    implementation(project(":terminal"))
    implementation(project(":terminal-core"))
    implementation(project(":terminal-proot"))
    implementation(project(":settings"))
    implementation(project(":code"))
    // Core library desugaring dulu diperlukan NewPipeExtractor (API Java 11+). Tetap aktif sampai CI membuktikan
    // tidak ada kode lain yang membutuhkannya; setelah itu lepas bersama isCoreLibraryDesugaringEnabled.
    // Dipin 2.0.4: nio 2.1.x + AGP 8.7.x membuat D8 gagal ("android.jar is located outside the root directory",
    // Google issue 362339872). Naikkan lagi setelah AGP diperbarui dan CI release terbukti hijau.
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.0.4")
}
