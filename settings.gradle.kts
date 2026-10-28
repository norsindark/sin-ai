pluginManagement {
    includeBuild("build-logic")
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "sin-ai"

include(
    ":app",
    ":modules:common",
    ":modules:storage-service",
    ":modules:media-processor",
    ":modules:ai-engine",
    ":modules:identity",
    ":modules:subtitle-workflow"
)
