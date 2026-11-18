pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google(); mavenCentral()
    }
}
rootProject.name = "Molina_Studio"
include(":app", ":files", ":settings", ":core-common", ":core-storage", ":core-log", ":terminal", ":terminal-core", ":core-ui", ":terminal-api", ":terminal-emulator")
include(":terminal-view")
include(":terminal-pty")
include(":terminal-proot")
include(":filemanager")
include(":core-security")
include(":code")

include(":player")
