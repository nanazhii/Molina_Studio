pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google(); mavenCentral()
        // JitPack hanya untuk grup com.github.TeamNewPipe; hash dipin di scripts/newpipe/pins.sha256.
        exclusiveContent {
            forRepository { maven("https://jitpack.io") } // gate:allow JitPack khusus NewPipeExtractor (dipin hash)
            filter { includeGroup("com.github.TeamNewPipe") }
        }
    }
}
rootProject.name = "Molina_Studio"
include(":app", ":files", ":settings", ":core-common", ":core-storage", ":core-log", ":terminal", ":terminal-core", ":cinema", ":core-ui", ":player-api", ":terminal-api", ":terminal-emulator")
include(":terminal-view")
include(":terminal-pty")
include(":terminal-proot")
include(":filemanager")
include(":core-security")
include(":code")
include(":core-stream")
include(":engine-newpipe")
