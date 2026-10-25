plugins {
    id("cashbook.java-conventions")
}

dependencies {
    api(project(":cashbook-core"))
    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)
    implementation(libs.jackson.dataformat.yaml)
    implementation(libs.json.schema.validator)
    implementation(libs.langchain4j.ollama)

    testImplementation(project(":synthetic"))
    testImplementation(project(":cashbook-ingest"))
    testImplementation(libs.wiremock)
}

tasks.test {
    systemProperty("jna.library.path", listOf("/opt/homebrew/lib", "/usr/local/lib").joinToString(":"))
    systemProperty("log4j2.loggerContextFactory", "org.apache.logging.log4j.simple.SimpleLoggerContextFactory")
}
