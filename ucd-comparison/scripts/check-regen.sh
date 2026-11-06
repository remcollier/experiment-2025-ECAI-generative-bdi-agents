#!/bin/bash
# JaKtA comparison, stage 3: checks J+R without a model. Two clones of the ports clone, stock and with
# regeneration; each case's scripted replies (checks/*.json) are served by stub_script.py, and the tea port
# is run once per case in each clone. Prints the outcome (ports_results.py) and the requests the stub got.
#
#   KIT=~/jakta/stage3-kit SRC=~/jakta/ports-src ~/jakta/stage3-kit/check-regen.sh
set -uo pipefail
KIT=${KIT:-$HOME/jakta/stage3-kit}
SRC=${SRC:-$HOME/jakta/ports-src}
OUT=${OUT:-$HOME/jakta/s3-out/check-regen-$(date +%F-%H%M)}
mkdir -p "$OUT"
for kind in stock regen; do
    rm -rf "$HOME/jakta/s3-check-$kind"; cp -a "$SRC" "$HOME/jakta/s3-check-$kind"
    [ $kind = regen ] && "$KIT/install.sh" regen "$HOME/jakta/s3-check-$kind"
done
port=18700
for case in "$KIT"/checks/*.json; do
    name=$(basename "$case" .json)
    for kind in stock regen; do
        port=$((port + 1))
        log="$OUT/$kind-$name.stub.jsonl"
        python3 -I "$KIT/stub_script.py" $port "$log" "$case" & stub=$!
        sleep 1
        id="$kind-$name"
        docker run --rm --network host --user "$(id -u):$(id -g)" -v "$HOME/jakta/s3-check-$kind":/work -v "$OUT":/out \
            -v "$HOME/jakta/s3-check-$kind/.gradle-home":/gradle -w /work -e HOME=/tmp -e GRADLE_USER_HOME=/gradle \
            maven:3.9.9-eclipse-temurin-21 ./gradlew -q --console=plain :jakta-playground:runPort --args="--log-to-file \
            --log-dir /out --lm-server-url http://127.0.0.1:$port/ --model-id stub --temperature 0.1 \
            --prompt-type PROMPT_WITH_HINTS --port tea --timeout-millis 30000 --run-id $id" > "$OUT/$id.console.log" 2>&1
        echo "$id exit $? requests $(wc -l < "$log" 2>/dev/null || echo 0)"
        kill $stub 2>/dev/null; wait $stub 2>/dev/null
    done
done
python3 -I "$KIT/ports_results.py" "$OUT" 2>&1 | cut -f2- 
grep -h "Stage 3 regeneration" "$OUT"/*/*.log 2>/dev/null | sed 's/.*WARN *//' | sort | uniq -c
