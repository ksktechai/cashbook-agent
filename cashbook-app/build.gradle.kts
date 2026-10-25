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
    // POI logs through Log4j; route it into the JSON log.
    runtimeOnly(libs.log4j2.jboss.logmanager)

    testImplementation(libs.quarkus.junit)
    testImplementation(libs.jackson.databind)
}

// Run the packaged-CLI tests as part of `./gradlew build`, so CI covers them.
tasks.check {
    dependsOn(tasks.named("quarkusIntTest"))
}
