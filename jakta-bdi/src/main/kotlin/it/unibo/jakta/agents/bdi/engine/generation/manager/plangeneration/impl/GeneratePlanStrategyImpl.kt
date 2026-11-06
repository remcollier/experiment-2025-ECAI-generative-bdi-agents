package it.unibo.jakta.agents.bdi.engine.generation.manager.plangeneration.impl

// JaKtA comparison, stage 3, arm J+R (my-planning-trials scripts/jakta/stage3): JaKtA v1.1.0's
// GeneratePlanStrategyImpl with a cap of Stage3Generations.MAX generations per goal in the run (theirs and
// the regenerations stage 3 adds alike): past it, the generation is refused and the intention dropped, as
// ASTRA gives up a goal after its attempts. And before any generation of a goal but its first, the goal's
// plans are removed, so the new ones replace them (also on JaKtA's own path, where a generated goal that
// fails is generated again). Everything not marked "stage 3" is theirs.

import it.unibo.jakta.agents.bdi.engine.actions.effects.IntentionChange
import it.unibo.jakta.agents.bdi.engine.context.AgentContext
import it.unibo.jakta.agents.bdi.engine.context.ContextUpdate.REMOVAL
import it.unibo.jakta.agents.bdi.engine.events.AchievementGoalInvocation
import it.unibo.jakta.agents.bdi.engine.plans.PlanLibrary
import it.unibo.tuprolog.core.Struct
import it.unibo.jakta.agents.bdi.engine.environment.Environment
import it.unibo.jakta.agents.bdi.engine.executionstrategies.ExecutionResult
import it.unibo.jakta.agents.bdi.engine.executionstrategies.feedback.PGPFailure.GenerationFailure
import it.unibo.jakta.agents.bdi.engine.executionstrategies.feedback.PGPSuccess
import it.unibo.jakta.agents.bdi.engine.generation.manager.impl.Stage3Generations
import it.unibo.jakta.agents.bdi.engine.generation.GenerationFailureResult
import it.unibo.jakta.agents.bdi.engine.generation.GenerationStrategy
import it.unibo.jakta.agents.bdi.engine.generation.PgpID
import it.unibo.jakta.agents.bdi.engine.generation.manager.plangeneration.GeneratePlanStrategy
import it.unibo.jakta.agents.bdi.engine.generation.manager.plangeneration.GenerationResultBuilder
import it.unibo.jakta.agents.bdi.engine.generation.manager.plangeneration.PlanGenerationResult
import it.unibo.jakta.agents.bdi.engine.goals.GeneratePlan
import it.unibo.jakta.agents.bdi.engine.intentions.Intention
import it.unibo.jakta.agents.bdi.engine.logging.LoggingConfig
import it.unibo.jakta.agents.bdi.engine.logging.loggers.AgentLogger

internal class GeneratePlanStrategyImpl(
    private val genResProcessor: GenerationResultBuilder,
    override val loggingConfig: LoggingConfig?,
    override val logger: AgentLogger?,
) : GeneratePlanStrategy {
    private val stateManager = GenerationStateInitializer(loggingConfig, logger)
    private val errorHandler = GenerationErrorHandler(logger)

    override fun generatePlan(
        genGoal: GeneratePlan,
        intention: Intention,
        generationStrategy: GenerationStrategy,
        context: AgentContext,
        environment: Environment,
    ): ExecutionResult {
        // stage 3: at most Stage3Generations.MAX generations per goal
        val goalValue = genGoal.value
        val previous = Stage3Generations.count(goalValue)
        if (!Stage3Generations.start(goalValue)) {
            logger?.warn { "Stage 3 generation refused for ${Stage3Generations.key(goalValue)}: ${Stage3Generations.MAX} already" }
            logger?.log { IntentionChange(intention, REMOVAL) }
            return ExecutionResult(
                context.copy(intentions = context.intentions.deleteIntention(intention.id)),
                feedback = GenerationFailure("Stage 3: at most ${Stage3Generations.MAX} generations per goal"),
            )
        }
        // stage 3: a generation after the goal's first replaces the goal's plans (those on its invocation)
        val kept =
            if (previous == 0) {
                context.planLibrary.plans
            } else {
                context.planLibrary.plans.filterNot {
                    it.trigger is AchievementGoalInvocation &&
                        (it.trigger.value as? Struct)?.let { v ->
                            v.functor == goalValue.functor && v.arity == goalValue.arity
                        } == true
                }
            }
        logger?.warn {
            "Stage 3 generation ${previous + 1} for ${Stage3Generations.key(goalValue)}: " +
                "${context.planLibrary.plans.size - kept.size} plans removed"
        }
        val pruned = if (previous == 0) context else context.copy(planLibrary = PlanLibrary.of(kept))
        val updatedGenerationStrategy =
            genGoal.generationConfig
                ?.let { generationStrategy.updateGenerationConfig(it) }
                ?: generationStrategy

        val generationState =
            stateManager.setupGenerationStart(
                pruned,
                updatedGenerationStrategy,
                genGoal,
                environment,
            )

        /*
         * Request the plan generation.
         */
        val planGenResult = updatedGenerationStrategy.requestBlockingGeneration(generationState)

        return when (planGenResult) {
            is GenerationFailureResult -> errorHandler.handleFailure(intention, pruned, planGenResult)
            is PlanGenerationResult ->
                processPlanGenerationResult(
                    generationState.pgpID,
                    genGoal,
                    intention,
                    pruned,
                    planGenResult,
                )
            else -> errorHandler.handleUnknownResult(intention, pruned)
        }
    }

    private fun processPlanGenerationResult(
        pgpID: PgpID,
        genGoal: GeneratePlan,
        intention: Intention,
        context: AgentContext,
        planGenResult: PlanGenerationResult,
    ): ExecutionResult =
        genResProcessor
            .buildResult(
                genGoal,
                context,
                intention,
                planGenResult,
            ).copy(
                feedback =
                    PGPSuccess.GenerationCompleted(
                        pgpID,
                        genGoal,
                        planGenResult.generatedPlanLibrary,
                        planGenResult.generatedAdmissibleGoals,
                        planGenResult.generatedAdmissibleBeliefs,
                    ),
            )
}
