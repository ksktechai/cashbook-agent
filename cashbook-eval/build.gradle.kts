plugins {
    id("cashbook.java-conventions")
}

dependencies {
    api(project(":cashbook-core"))
    api(project(":cashbook-ingest"))
    api(project(":cashbook-classify"))
    api(project(":cashbook-export"))
    implementation(libs.poi.ooxml)
    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)

    testImplementation(project(":synthetic"))
    testImplementation(libs.wiremock)
    testImplementation(libs.json.schema.validator)
}

tasks.test {
    systemProperty("log4j2.loggerContextFactory", "org.apache.logging.log4j.simple.SimpleLoggerContextFactory")
}
