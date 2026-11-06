# JaKtA and ASTRA compared: the ports and variants used (UCD)

This fork holds the code we added to JaKtA to compare its plan generation with plan generation in [ASTRA](https://gitlab.com/astra-language/astra-core), for a paper in preparation (EMAS 2027). Everything here starts from this repository's tag **v1.1.0** (b8c67d5), the version we ran. `main` is left as it is upstream.

Rem Collier, School of Computer Science, University College Dublin.

## Branches

| Branch | What it adds to v1.1.0 | JaKtA's code changed? |
|---|---|---|
| `ucd/ports` | Four tasks ported from ASTRA's planning trials, as a new experiment in jakta-playground (package `it.unibo.jakta.playground.ports`), one Gradle task `runPort`, and this folder | No: new files only, and the `runPort` task appended to `jakta-playground/build.gradle.kts` |
| `ucd/regen` | `ucd/ports`, plus our regeneration policy (arm "J+R") | Yes: our versions of `UnavailablePlanStrategyImpl` and `GeneratePlanStrategyImpl`, and a new `Stage3Generations.kt` (jakta-bdi), every change marked "stage 3" |
| `ucd/step` | `ucd/ports`, plus one constraint added to the system prompt (arm "J+S") | Yes: one line added to `jakta-playground/src/main/resources/system.md` |

The engine, generator, prompt builders and context filters are otherwise unchanged in every branch. J+R and J+S are our variants for the comparison, not JaKtA as released.

## The tasks

Your explorer tests navigation under partial observability: one goal, with a helper action that suggests a direction. The ports test structures it does not:

- **rooms** (`clean_house`): clean every dirty room in a small house, where dirt is seen only in the agent's room. It tests coverage of what the agent cannot see. Its helper, `next(Room, Next)`, gives the next room on the way to a room, through an output argument.
- **tea** (`serve(tea, alice)`, with `prepare(Drink)`): actions with preconditions (`fill`, `boil`, `take`, `putInCup`, `pour`, `serve`), two goals, and a start state where the kettle is already full.
- **keys** (`fetch(keys, hall)`, with `go_to(Room)`): the item's location is unknown. The agent asks an oracle agent, which replies with a tell of `located(Item, Room)` after 500 ms. It tests communication and waiting for a belief.
- **print** (`printed(report)`): a printer that starts jammed (`clear`, then `send`), with `completed(Doc)` arriving after a random 0.2–3 s. It tests waiting for an action's effect.

Each world is an immutable state in the environment, and percepts are recomputed from it at every perception, as in your grid world. An action whose preconditions fail fails and changes nothing. The descriptions (`.meaning`) are word for word those of the ASTRA tasks. Success is judged as ASTRA's harness judges it: the environment logs `Task check: true|false` whenever the check changes, and a run succeeds if the robot's intention ends, not by failing, with the last check true (`scripts/ports_results.py`).

Checks before any model run: hand-written plans (`--baseline`) succeed 10/10 on each port, and plans that do nothing (`--baseline --empty`) fail.

## Running

As your `runExperiment`, with `--port rooms|tea|keys|print`:

```
./gradlew :jakta-playground:runPort --args="--port tea --lm-server-url https://api.openai.com/v1/ \
    --model-id gpt-4.1 --temperature 0.5 --prompt-type PROMPT_WITH_HINTS --max-tokens 8000 \
    --timeout-millis 180000 --run-id test --log-to-file --log-dir out"
./gradlew :jakta-playground:runPort --args="--port tea --baseline"   # hand-written plans, no model
```

The `.env` file holds `API_KEY=...`, as for `runExperiment`. We used the prompt types PROMPT_WITHOUT_HINTS and PROMPT_WITH_HINTS; the tasks have no remarks. The limits were 8,000 tokens and 180 s per run, matched to ASTRA's. We used temperatures 0.1, 0.5 and 0.9, with 10 runs each.

## This folder

- `install/`: the scripts that make each branch from a clone of v1.1.0 (`install-ports.sh <clone>`, then `install-arm.sh regen|step <clone>`), and the J+R files.
- `scripts/`: what we ran.
  - `run-jakta.sh` runs one lane in a JDK 21 container; `jakta.sbatch` does the same on a Slurm cluster, with apptainer.
  - `ports_results.py` judges the runs; `extract_replies.py` gets the model's replies from the chat logs; `ports_failcauses.py` and `jakta_failures.py` code why runs failed.
  - `stub_model.py` and `stub_script.py` are scripted OpenAI-compatible servers, used to check the prompts and the J+R arm without a model. `check-regen.sh` and `check-one.sh` run those checks, with the scenarios in `checks/`.

  The paths in their comments and defaults are those of our machines and our repository (`scripts/jakta/...`); adjust them to yours.

## A fault in our first version of the ports

Our first port actions reported failure with `addResults(Substitution.failed())`, but reported success only by changing the state. Because an action keeps its result between calls, an action that failed once then failed for the rest of the run. This was our bug, not JaKtA's. The ports here have the fix: every action starts from `Substitution.empty()` (`portAction` in `PortActions.kt`). We reran every affected run with it.

## Licence

As the rest of the repository, Apache License 2.0. The J+R files carry a notice at the top saying they are our changes. J+S's one-line change to `system.md` is described here, not marked in the file, because a notice in the file would become part of the prompt.
