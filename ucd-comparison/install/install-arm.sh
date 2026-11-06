#!/bin/bash
# JaKtA comparison, stage 3: makes a clone for one arm from a clone that has the stage 2 ports installed
# (scripts/jakta/ports/install.sh).
#   regen  (J+R): our UnavailablePlanStrategyImpl and GeneratePlanStrategyImpl over theirs, and Stage3Generations
#                 (regeneration when no plan applies or a goal fails; at most 3 generations per goal; see the files)
#   step   (J+S): one constraint added to their system prompt, ASTRA's step instruction in JaKtA's terms
#
#   scripts/jakta/stage3/install.sh regen|step <clone>
set -euo pipefail
ARM=${1:?regen or step}
CLONE=${2:?clone with the ports installed}
HERE=$(cd "$(dirname "$0")" && pwd)
[ -d "$CLONE/jakta-playground/src/main/kotlin/it/unibo/jakta/playground/ports" ] || { echo "no ports in $CLONE" >&2; exit 1; }
case "$ARM" in
regen)
    G="$CLONE/jakta-bdi/src/main/kotlin/it/unibo/jakta/agents/bdi/engine/generation/manager"
    cp "$HERE/regeneration/UnavailablePlanStrategyImpl.kt" "$HERE/regeneration/Stage3Generations.kt" "$G/impl/"
    cp "$HERE/regeneration/GeneratePlanStrategyImpl.kt" "$G/plangeneration/impl/GeneratePlanStrategyImpl.kt"
    ;;
step)
    SYS="$CLONE/jakta-playground/src/main/resources/system.md"
    grep -q "A plan takes one step and adopts its goal again" "$SYS" || \
        sed -i.bak '/^- Be as general and minimal as possible$/a\
- A plan takes one step and adopts its goal again (`achieve` the same goal as its last operation) until the goal holds: write one plan per situation, with conditions that say when it applies, including a plan for when the goal already holds, and make sure each step brings the agent closer to the goal' "$SYS"
    rm -f "$SYS.bak"
    grep -q "A plan takes one step and adopts its goal again" "$SYS" || { echo "step constraint not added" >&2; exit 1; }
    ;;
*) echo "unknown arm $ARM" >&2; exit 1 ;;
esac
echo "$ARM installed into $CLONE"
