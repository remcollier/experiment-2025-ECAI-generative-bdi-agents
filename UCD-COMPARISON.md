# UCD's comparison of JaKtA and ASTRA

This is a fork of [pslab-unibo/experiment-2025-ECAI-generative-bdi-agents](https://github.com/pslab-unibo/experiment-2025-ECAI-generative-bdi-agents). We used it to compare JaKtA's plan generation with plan generation in [ASTRA](https://gitlab.com/astra-language/astra-core), for a paper in preparation (EMAS 2027).

Apart from this file, `main` is the same as upstream. Our code is on three branches, each made from the tag **v1.1.0** (b8c67d5), the version we ran:

| Branch | What it adds to v1.1.0 | JaKtA's code changed? |
|---|---|---|
| [`ucd/ports`](https://github.com/remcollier/experiment-2025-ECAI-generative-bdi-agents/tree/ucd/ports) | Four tasks ported from ASTRA's planning trials (rooms, tea, keys, print), as a new experiment in jakta-playground (package `it.unibo.jakta.playground.ports`), and a Gradle task, `runPort`. It also adds the folder `ucd-comparison/`, with the README, our run and judging scripts, and the checks. | No: new files only, and the `runPort` task added to `jakta-playground/build.gradle.kts` |
| [`ucd/regen`](https://github.com/remcollier/experiment-2025-ECAI-generative-bdi-agents/tree/ucd/regen) | `ucd/ports`, plus arm **J+R**: JaKtA with a regeneration policy like ASTRA's. A goal is generated again when its plans exist but none applies, or when it fails with no plan for its failure, up to 3 generations per goal; the goal's old plans are removed before it is generated again. | Yes: our versions of `UnavailablePlanStrategyImpl` and `GeneratePlanStrategyImpl`, and a new `Stage3Generations.kt` (jakta-bdi), each change marked "stage 3" |
| [`ucd/step`](https://github.com/remcollier/experiment-2025-ECAI-generative-bdi-agents/tree/ucd/step) | `ucd/ports`, plus arm **J+S**: one constraint added to the system prompt, ASTRA's step instruction in JaKtA's terms ("A plan takes one step and adopts its goal again … until the goal holds") | Yes: one line added to `jakta-playground/src/main/resources/system.md` |

J+R and J+S are our variants, made for the comparison; they are not JaKtA as released. Everything else (the engine, generator, prompt builders and context filters) is as in v1.1.0 on every branch.

The tasks, how to run them, what we checked, and a fault in our first version of the ports (since fixed) are described in [`ucd-comparison/README.md`](https://github.com/remcollier/experiment-2025-ECAI-generative-bdi-agents/blob/ucd/ports/ucd-comparison/README.md) on `ucd/ports`.

Rem Collier, School of Computer Science, University College Dublin. Apache License 2.0, as the rest of the repository.
