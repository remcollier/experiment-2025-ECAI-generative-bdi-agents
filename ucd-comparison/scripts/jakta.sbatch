#!/bin/bash
# JaKtA calibration (stage 1, pre-registered 31 October), the Sonic half: JaKtA's own explorer
# experiment, its code unchanged (v1.1.0, b8c67d5), against a model served by llama.cpp in this job,
# with the same server settings as ASTRA's Sonic series (llm.sh: 3 slots, 48k context). Three lanes
# (temperatures 0.1, 0.5, 0.9) run side by side, each in a clone of its own with its own Gradle home
# (seeded from jakta/gradle-home), as run-jakta.sh does on agentic with Docker.
# Copy to $SONIC_DIR (beside llm.sh) and submit from there:
#
#   sbatch --gres=gpu:2 --export=ALL,MODEL=ggml-org/gpt-oss-120b-GGUF,NAME=gpt-oss-120b jakta.sbatch
#   sbatch jakta.sbatch                                  (gpt-oss-20b)
#
# Needs $SONIC_DIR/jakta/src (their repository at v1.1.0) and a warmed $SONIC_DIR/jakta/gradle-home.
# Stage 2: PORTS="rooms tea keys print" (or PORT=one of them) runs ported tasks one after another (runPort), from $SONIC_DIR/jakta/ports-src, a copy of
# src with the ports installed by scripts/jakta/ports/install.sh, with MAX_TOKENS 8000 and RUN_MILLIS 180000 and the two prompt types
# without remarks; the output folder is then ...-jakta-s2-<model>-<port>. PROMPTS_SET overrides the prompt types.
# Stage 2 rerun and stage 3 (4 November): PORTS_SRC names the clone the lanes copy (default ports-src; ports-src-v2
# has the fixed ports, ports-src-regen and ports-src-step the J+R and J+S arms), SERIES the output folders'
# series (default jakta-s2: ...-<SERIES>-<model>-<port>), and the lanes are lane-<SERIES>-<model>-<port>-t<t>
# for any SERIES but jakta-s2 (whose lanes keep their stage 2 names), so no lane of another series is reused.
# Their defaults are kept: max tokens 2048, a 60 s run, a 240 s request timeout.
#SBATCH --job-name=jakta-calib
#SBATCH --partition=csgpu
#SBATCH --gres=gpu:1
#SBATCH --constraint=a100|l40s
#SBATCH --nodes=1
#SBATCH --ntasks=1
#SBATCH --cpus-per-task=8
#SBATCH --time=04:00:00
#SBATCH --output=results/%x-%j.out
set -uo pipefail
cd "$SLURM_SUBMIT_DIR"
. ./sonic.env
RESULTS="$SONIC_DIR/results/jakta-$SLURM_JOB_ID.txt"
: > "$RESULTS"
RUN="$SONIC_DIR/runs/jakta-$SLURM_JOB_ID"
mkdir -p "$RUN"
RUNS=${RUNS:-10}
MODEL=${MODEL:-ggml-org/gpt-oss-20b-GGUF}
NAME=${NAME:-gpt-oss-20b}
KWARGS=${KWARGS-'{"reasoning_effort":"low"}'}
# The ported tasks to run one after another in this job (stage 2), or none for JaKtA's own explorer
PORTS=${PORTS:-${PORT:-}}
PORTS_SRC=${PORTS_SRC:-ports-src}
SERIES=${SERIES:-jakta-s2}
LANE_TAG=$([ "$SERIES" = jakta-s2 ] && echo "" || echo "$SERIES-")
J="$SONIC_DIR/jakta"
result INFO "job $SLURM_JOB_ID node $(hostname)"
result INFO "$(nvidia-smi --query-gpu=name,memory.total --format=csv,noheader | paste -sd ";")"

. ./llm.sh
start_llm "$MODEL" "$KWARGS" && result PASS "llama.cpp serving $MODEL on the GPU at $LLM_URL" \
    || { result FAIL "llama.cpp did not start (see $RUN/llama.log)"; exit 1; }

lane() {  # temperature
    local t=$1 clone="$J/lane-${PORT:+$LANE_TAG}$NAME${PORT:+-$PORT}-t$1" prompt n id start code
    local from=src; [ -n "$PORT" ] && from=$PORTS_SRC
    [ -d "$clone" ] || cp -a "$J/$from" "$clone"
    [ -d "$clone/.gradle-home" ] || cp -a "$J/gradle-home" "$clone/.gradle-home"
    printf "API_KEY=%s\n" "$LLM_KEY" > "$clone/.env"
    for prompt in $PROMPTS; do
        for n in $(seq "$RUNS"); do
            id="$NAME${PORT:+-$PORT}-t$t-$prompt-$n"
            [ -d "$OUT/$id" ] && continue    # done already (a restarted job)
            start=$(date +%s)
            apptainer exec --bind /scratch --pwd "$clone" --env HOME=/tmp --env GRADLE_USER_HOME="$clone/.gradle-home" \
                "$SONIC_DIR/images/maven" ./gradlew -q --console=plain :jakta-playground:$TASK --args="--log-to-file \
                    --log-dir $OUT --lm-server-url $LLM_URL/ --model-id $NAME --temperature $t --prompt-type $prompt --run-id $id $EXTRA" \
                > "$OUT/$id.console.log" 2>&1
            code=$?
            printf "%s\t%s\t%s\t%s\t%s\t%s\n" "$id" "$NAME" "$prompt" "$t" "$code" "$(( $(date +%s) - start ))" >> "$OUT/runs.tsv"
        done
    done
}

for PORT in ${PORTS:-explorer}; do
    [ "$PORT" = explorer ] && PORT=""
    if [ -n "$PORT" ]; then
        TASK=runPort
        PROMPTS=${PROMPTS_SET:-"PROMPT_WITHOUT_HINTS PROMPT_WITH_HINTS"}
        EXTRA="--port $PORT --max-tokens ${MAX_TOKENS:-8000} --timeout-millis ${RUN_MILLIS:-180000}"
        OUT="$J/out/$(date +%F)-$SERIES-$NAME-$PORT"
    else
        TASK=runExperiment
        PROMPTS=${PROMPTS_SET:-"PROMPT_WITHOUT_HINTS PROMPT_WITH_HINTS PROMPT_WITH_HINTS_AND_REMARKS"}
        EXTRA=""
        OUT="$J/out/$(date +%F)-jakta-calib-jakta-$NAME"
    fi
    mkdir -p "$OUT"
    [ -f "$OUT/runs.tsv" ] || printf "run\tmodel\tprompt\ttemperature\texit\tseconds\n" > "$OUT/runs.tsv"
    start=$(date +%s)
    lanes=()
    for t in 0.1 0.5 0.9; do lane $t & lanes+=($!); done
    wait "${lanes[@]}"
    result INFO "${PORT:-explorer}: took $(( ($(date +%s) - start) / 60 )) min; $(tail -n +2 "$OUT/runs.tsv" | wc -l) runs in $OUT"
done
stop_llm
