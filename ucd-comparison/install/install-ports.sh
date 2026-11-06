#!/bin/bash
# JaKtA comparison, stage 2: puts the ported tasks into a clone of JaKtA's experiment repository
# (experiment-2025-ECAI-generative-bdi-agents v1.1.0). Their code is not changed: our files go in a
# package of their own (it.unibo.jakta.playground.ports), and one Gradle task, runPort, is added to
# jakta-playground's build file, a copy of their runExperiment with our main class.
#
#   scripts/jakta/ports/install.sh <clone>
set -euo pipefail
CLONE=${1:?clone of their repository}
HERE=$(cd "$(dirname "$0")" && pwd)
DEST="$CLONE/jakta-playground/src/main/kotlin/it/unibo/jakta/playground/ports"
mkdir -p "$DEST"
cp "$HERE"/src/it/unibo/jakta/playground/ports/*.kt "$DEST/"
BUILD="$CLONE/jakta-playground/build.gradle.kts"
if ! grep -q '"runPort"' "$BUILD"; then
    cat >> "$BUILD" <<'EOF'

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
EOF
fi
echo "installed into $CLONE"
