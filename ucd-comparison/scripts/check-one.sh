#!/bin/bash
# JaKtA comparison, stage 3: one check case (checks/<case>.json) in the stock and J+R check clones made by
# check-regen.sh, as check-regen.sh runs them.   check-one.sh <case>
set -uo pipefail
KIT=${KIT:-$HOME/jakta/stage3-kit}
name=$1
OUT=${OUT:-$HOME/jakta/s3-out/check-one-$name-$(date +%H%M%S)}
mkdir -p "$OUT"; port=18900
for kind in stock regen; do
    port=$((port + 1)); log="$OUT/$kind-$name.stub.jsonl"
    python3 -I "$KIT/stub_script.py" $port "$log" "$KIT/checks/$name.json" & stub=$!; sleep 1
    docker run --rm --network host --user "$(id -u):$(id -g)" -v "$HOME/jakta/s3-check-$kind":/work -v "$OUT":/out \
        -v "$HOME/jakta/s3-check-$kind/.gradle-home":/gradle -w /work -e HOME=/tmp -e GRADLE_USER_HOME=/gradle \
        maven:3.9.9-eclipse-temurin-21 ./gradlew -q --console=plain :jakta-playground:runPort --args="--log-to-file \
        --log-dir /out --lm-server-url http://127.0.0.1:$port/ --model-id stub --temperature 0.1 \
        --prompt-type PROMPT_WITH_HINTS --port tea --timeout-millis 30000 --run-id $kind-$name" > "$OUT/$kind-$name.console.log" 2>&1
    echo "$kind-$name requests $(wc -l < "$log")"; kill $stub; wait $stub 2>/dev/null
done
python3 -I "$KIT/ports_results.py" "$OUT" 2>/dev/null | cut -f2-
for kind in stock regen; do echo "== $kind"; grep -h "pour\|serve\" \|Stage 3\|Environment has been updated" "$OUT/$kind-$name"/Mas-*.log | grep -v "belief" | cut -c12-200 | head -14; done
