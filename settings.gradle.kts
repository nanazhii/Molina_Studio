pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google(); mavenCentral()
        // JitPack hanya untuk NewPipeExtractor + nanojson (grup com.github.TeamNewPipe); hash dipin
        // di scripts/newpipe/pins.sha256 dan diverifikasi tugas verifyNewPipePins.
        exclusiveContent {
            forRepository { maven("https://jitpack.io") } // gate:allow JitPack khusus NewPipeExtractor, hash dipin
            filter { includeGroup("com.github.TeamNewPipe") }
        }
    }
}
rootProject.name = "Molina_Studio"
include(":app", ":files", ":settings", ":core-common", ":core-storage", ":core-log", ":terminal", ":terminal-core", ":core-ui", ":terminal-api", ":terminal-emulator")
include(":terminal-view")
include(":terminal-pty")
include(":terminal-proot")
include(":filemanager")
include(":picker")
include(":core-security")
include(":code")

include(":player")

include(":engine-newpipe")
