pluginManagement {
    includeBuild("build-logic")
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "cashbook-agent"

include(
    "cashbook-core",
    "cashbook-ingest",
    "cashbook-classify",
    "cashbook-export",
    "cashbook-eval",
    "cashbook-app",
    "synthetic",
)
