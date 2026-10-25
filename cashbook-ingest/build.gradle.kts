plugins {
    id("cashbook.java-conventions")
}

dependencies {
    api(project(":cashbook-core"))
    implementation(libs.pdfbox)
    implementation(libs.tess4j) {
        // only needed for Tess4J's own PDF utilities, which we don't use (PDFBox renders the pages)
        exclude(group = "org.apache.pdfbox", module = "pdfbox-tools")
        exclude(group = "org.apache.pdfbox", module = "pdfbox-debugger")
    }
    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)

    testImplementation(project(":synthetic"))
}

tasks.test {
    // Tess4J loads the native Tesseract library through JNA; Homebrew installs it outside JNA's defaults.
    systemProperty("jna.library.path", listOf("/opt/homebrew/lib", "/usr/local/lib").joinToString(":"))
    systemProperty("log4j2.loggerContextFactory", "org.apache.logging.log4j.simple.SimpleLoggerContextFactory")
}
