package it.unibo.jakta.agents.bdi.engine.generation.manager.impl

import it.unibo.tuprolog.core.Struct
import java.util.concurrent.ConcurrentHashMap

// JaKtA comparison, stage 3, arm J+R (my-planning-trials scripts/jakta/stage3): generations per goal
// (functor/arity) in the run, shared by the capped GeneratePlanStrategyImpl and the regenerating
// UnavailablePlanStrategyImpl. One run per JVM, and only the robot generates, so a JVM-wide count is the run's.
object Stage3Generations {
    // ASTRA's goal attempts: at most 3 generations per goal
    const val MAX = 3
    private val counts = ConcurrentHashMap<String, Int>()

    fun key(goal: Struct) = "${goal.functor}/${goal.arity}"

    fun count(goal: Struct) = counts.getOrDefault(key(goal), 0)

    /** Records a generation for the goal; false (and nothing recorded) if it has had MAX already. */
    fun start(goal: Struct): Boolean {
        var allowed = false
        counts.compute(key(goal)) { _, n -> val c = n ?: 0; if (c < MAX) { allowed = true; c + 1 } else c }
        return allowed
    }
}
