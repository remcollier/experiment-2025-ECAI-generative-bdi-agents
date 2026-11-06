#!/bin/bash
# JaKtA calibration (stage 1, pre-registered 31 October): runs JaKtA's own explorer experiment, its
# code unchanged (experiment-2025-ECAI-generative-bdi-agents v1.1.0), RUNS times per prompt type at
# one temperature, in a JDK 21 container. One lane per call; lanes need clones of their own.
#
#   JAKTA=~/jakta/openai-t0.1 OUT=~/jakta/out/gpt-4.1 MODEL=gpt-4.1 URL=https://api.openai.com/v1/ \
#       TEMP=0.1 RUNS=10 scripts/jakta/run-jakta.sh
#
# The clone's .env holds API_KEY=... (their runExperiment task reads it). Their defaults are kept:
# max tokens 2048, a 60 s run, a 240 s request timeout. Each run logs to $OUT/<run id>/ (mas.log,
# ExplorerBot/chat), and one line per run goes to $OUT/runs.tsv.
#
# Stage 2 (a ported task; the clone needs scripts/jakta/ports/install.sh first): PORT=rooms|tea|keys|print
# runs the runPort task instead, with MAX_TOKENS (8000) and RUN_MILLIS (180000), matched to ASTRA's limits,
# and the two prompt types without remarks:
#
#   PORT=rooms JAKTA=... OUT=... MODEL=... URL=... TEMP=0.1 scripts/jakta/run-jakta.sh
set -uo pipefail
: "${JAKTA:?clone of their repository}" "${OUT:?output folder}" "${MODEL:?model id}" "${URL:?OpenAI-compatible base URL}" "${TEMP:?temperature}"
RUNS=${RUNS:-10}
PORT=${PORT:-}
if [ -n "$PORT" ]; then
    TASK=runPort
    PROMPTS=${PROMPTS:-"PROMPT_WITHOUT_HINTS PROMPT_WITH_HINTS"}
    EXTRA="--port $PORT --max-tokens ${MAX_TOKENS:-8000} --timeout-millis ${RUN_MILLIS:-180000}"
else
    TASK=runExperiment
    PROMPTS=${PROMPTS:-"PROMPT_WITHOUT_HINTS PROMPT_WITH_HINTS PROMPT_WITH_HINTS_AND_REMARKS"}
    EXTRA=""
fi
IMAGE=${IMAGE:-maven:3.9.9-eclipse-temurin-21}
# A Gradle home per lane, seeded from a shared one: lanes in separate containers cannot share a
# Gradle home (each sees the other's lock owner as a foreign process and times out)
GRADLE_HOME_DIR=${GRADLE_HOME_DIR:-$JAKTA/.gradle-home}
SEED=${SEED:-$HOME/jakta/gradle-home}
[ -d "$GRADLE_HOME_DIR" ] || { [ -d "$SEED" ] && cp -a "$SEED" "$GRADLE_HOME_DIR"; }
# Docker's --network host, so that a llama.cpp server on 127.0.0.1 is reachable
NETWORK=${NETWORK:-bridge}
mkdir -p "$OUT" "$GRADLE_HOME_DIR"
OUT=$(cd "$OUT" && pwd)
[ -f "$OUT/runs.tsv" ] || printf "run\tmodel\tprompt\ttemperature\texit\tseconds\n" > "$OUT/runs.tsv"
safe=${MODEL//[:\/]/-}
for prompt in $PROMPTS; do
    for n in $(seq "$RUNS"); do
        id="$safe${PORT:+-$PORT}-t$TEMP-$prompt-$n"
        [ -d "$OUT/$id" ] && continue    # done already (a restarted lane)
        start=$(date +%s)
        docker run --rm --network "$NETWORK" --user "$(id -u):$(id -g)" \
            -v "$JAKTA":/work -v "$OUT":/out -v "$GRADLE_HOME_DIR":/gradle -w /work \
            -e HOME=/tmp -e GRADLE_USER_HOME=/gradle "$IMAGE" \
            ./gradlew -q --console=plain :jakta-playground:$TASK --args="--log-to-file --log-dir /out \
                --lm-server-url $URL --model-id $MODEL --temperature $TEMP --prompt-type $prompt --run-id $id $EXTRA" \
            > "$OUT/$id.console.log" 2>&1
        code=$?
        printf "%s\t%s\t%s\t%s\t%s\t%s\n" "$id" "$MODEL" "$prompt" "$TEMP" "$code" "$(( $(date +%s) - start ))" >> "$OUT/runs.tsv"
    done
done
