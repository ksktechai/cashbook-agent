plugins {
    id("cashbook.java-conventions")
}

dependencies {
    api(project(":cashbook-core"))
    implementation(libs.pdfbox)
    implementation(libs.poi.ooxml)
    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)
}

// POI logs through Log4j; use Log4j's built-in simple logger outside the Quarkus app.
val simpleLog4j = "org.apache.logging.log4j.simple.SimpleLoggerContextFactory"

tasks.test {
    systemProperty("log4j2.loggerContextFactory", simpleLog4j)
}

// Regenerates the committed sanitised template after an intentional change to TemplateBuilder.
tasks.register<JavaExec>("updateTemplate") {
    group = "synthetic"
    description = "Writes src/test/resources/template.xlsx from TemplateBuilder."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "nz.co.ksktech.cashbook.synthetic.xlsx.WriteTemplate"
    systemProperty("log4j2.loggerContextFactory", simpleLog4j)
    args(layout.projectDirectory.file("src/test/resources/template.xlsx").asFile.absolutePath)
}
