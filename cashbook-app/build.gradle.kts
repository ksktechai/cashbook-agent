plugins {
    id("cashbook.java-conventions")
    alias(libs.plugins.quarkus)
}

dependencies {
    implementation(enforcedPlatform(libs.quarkus.bom))
    implementation(project(":cashbook-core"))
    implementation(project(":cashbook-ingest"))
    implementation(project(":cashbook-classify"))
    implementation(project(":cashbook-export"))
    implementation(project(":cashbook-eval"))
    implementation(project(":synthetic"))
    implementation(libs.quarkus.picocli)
    implementation(libs.quarkus.logging.json)
    implementation(libs.quarkus.config.yaml)

    testImplementation(libs.quarkus.junit)
    testImplementation(libs.jackson.databind)
}

// Run the packaged-CLI tests as part of `./gradlew build`, so CI covers them.
tasks.check {
    dependsOn(tasks.named("quarkusIntTest"))
}
