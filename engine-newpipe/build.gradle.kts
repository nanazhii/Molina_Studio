import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}
android {
    namespace = "com.molina.engine.newpipe"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

// Versi dipin; harus sama dengan NewPipeInfo.VERSION dan nama berkas di scripts/newpipe/pins.sha256.
val newpipeVersion = "v0.26.5"

dependencies {
    api(project(":core-stream"))
    implementation(project(":core-log"))
    implementation("com.github.TeamNewPipe:NewPipeExtractor:$newpipeVersion")
}

// Konfigurasi terpisah (bukan variant Android) agar yang diperiksa persis jar JVM hasil resolve NewPipeExtractor.
val newpipePins by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}
dependencies { add("newpipePins", "com.github.TeamNewPipe:NewPipeExtractor:$newpipeVersion") }

// Gerbang rantai pasok: setiap jar pada pins.sha256 wajib ada dengan SHA-256 yang sama. Jika versi
// transitif bergeser atau isi jar berubah, build gagal sebelum kompilasi.
val verifyNewPipePins by tasks.registering {
    notCompatibleWithConfigurationCache("memeriksa hash artefak")
    val pinsFile = rootProject.file("scripts/newpipe/pins.sha256")
    val infoFile = file("src/main/java/com/molina/engine/newpipe/NewPipeInfo.kt")
    inputs.file(pinsFile)
    inputs.file(infoFile)
    doLast {
        if (!infoFile.readText().contains("VERSION = \"$newpipeVersion\"")) {
            throw GradleException("NewPipeInfo.VERSION tidak sama dengan newpipeVersion ($newpipeVersion); samakan keduanya.")
        }
        val pins = pinsFile.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .associate { line ->
                val parts = line.split(Regex("\\s+"), limit = 2)
                require(parts.size == 2) { "baris pins.sha256 tidak valid: $line" }
                parts[1].trim() to parts[0].lowercase()
            }
        val files = newpipePins.resolve().associateBy { it.name }
        val problems = mutableListOf<String>()
        for ((name, expected) in pins) {
            val f = files[name]
            if (f == null) { problems += "tidak ada di hasil resolve: $name"; continue }
            val actual = MessageDigest.getInstance("SHA-256").digest(f.readBytes())
                .joinToString("") { "%02x".format(it) }
            if (actual != expected) problems += "SHA-256 TIDAK COCOK: $name"
        }
        val extra = files.keys - pins.keys
        if (extra.isNotEmpty()) problems += "jar tanpa pin: " + extra.sorted().joinToString()
        if (problems.isNotEmpty()) {
            throw GradleException(
                "Verifikasi pin NewPipe gagal:\n  - " + problems.joinToString("\n  - ") +
                    "\nTinjau dulu sebelum memperbarui scripts/newpipe/pins.sha256."
            )
        }
        logger.lifecycle("Pin NewPipe terverifikasi: ${pins.size} jar.")
    }
}
tasks.matching { it.name.startsWith("compile") && it.name.endsWith("Kotlin") }
    .configureEach { dependsOn(verifyNewPipePins) }
