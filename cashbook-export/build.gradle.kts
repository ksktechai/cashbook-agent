plugins {
    id("cashbook.java-conventions")
}

dependencies {
    api(project(":cashbook-core"))
    implementation(libs.poi.ooxml)
    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)

    testImplementation(project(":synthetic"))
    testImplementation(project(":cashbook-ingest"))
    testImplementation(project(":cashbook-classify"))
}

tasks.test {
    systemProperty("log4j2.loggerContextFactory", "org.apache.logging.log4j.simple.SimpleLoggerContextFactory")
}
