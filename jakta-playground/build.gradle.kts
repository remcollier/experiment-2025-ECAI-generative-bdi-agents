import java.util.Properties

plugins {
    alias(libs.plugins.kotlinx)
    id("python-dvc")
}

dependencies {
    implementation(project(":jakta-dsl"))
    implementation(project(":jakta-plan-generation"))

    implementation(libs.kotlin.coroutines)
    implementation(libs.ktor.network)
    implementation(libs.bundles.ktor.client)
    implementation(libs.bundles.kotlin.testing)
    implementation(libs.bundles.kotlin.logging)
    implementation(libs.openai)
    implementation(libs.clikt)
    implementation(libs.bundles.koin)
}

tasks.register<JavaExec>("runExperiment") {
    val keystoreFile = project.rootProject.file(".env")
    val properties = Properties()
    properties.load(keystoreFile.inputStream())

    environment = mapOf("API_KEY" to properties.getProperty("API_KEY"))
    description = "Run the explorer agent sample with the given experimental config."
    group = "application"

    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "${project.group}.playground.explorer.ExplorerRunnerKt"
}

tasks.register<JavaExec>("replayExperiment") {
    description = "Run the explorer agent sample by reusing already generated responses."
    group = "application"

    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "${project.group}.playground.explorer.ExplorerExperimentReplayerKt"
}

tasks.register<JavaExec>("runBaseline") {
    description = "Run the explorer agent sample with the baseline plans."
    group = "application"

    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "${project.group}.playground.explorer.BaselineExplorerRunnerKt"
}

tasks.register<JavaExec>("analyzePGP") {
    val keystoreFile = project.rootProject.file(".env")
    val properties = Properties()
    properties.load(keystoreFile.inputStream())

    environment = mapOf("API_KEY" to properties.getProperty("API_KEY"))
    description = "Evaluate each PGP attempt."
    group = "application"

    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "${project.group}.playground.evaluation.apps.AnalyzePGPKt"
}

tasks.register<JavaExec>("runDomesticRobot") {
    description = "Run the domestic robot application."
    group = "application"

    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "${project.group}.playground.domesticrobot.DomesticRobotRunnerKt"
}

// Added for the ASTRA comparison (my-planning-trials, JaKtA comparison stage 2): runs a ported task
tasks.register<JavaExec>("runPort") {
    val keystoreFile = project.rootProject.file(".env")
    val properties = Properties()
    properties.load(keystoreFile.inputStream())

    environment = mapOf("API_KEY" to properties.getProperty("API_KEY"))
    description = "Run one of the tasks ported from ASTRA's planning trials."
    group = "application"

    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "${project.group}.playground.ports.PortExperimentKt"
}
