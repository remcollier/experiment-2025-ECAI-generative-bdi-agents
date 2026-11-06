package it.unibo.jakta.agents.bdi.engine.generation.manager.impl

// JaKtA comparison, stage 3, arm J+R (my-planning-trials scripts/jakta/stage3): JaKtA v1.1.0's
// UnavailablePlanStrategyImpl with regeneration added. Installed over their file only in the J+R
// clones (scripts/jakta/stage3/install.sh); everything below that is not marked "stage 3" is theirs.
//
// What JaKtA does (v1.1.0): it generates plans for an achievement goal with no relevant plan, through a
// partial plan on the goal's failure trigger, guarded by missing_plan_for(g), which runs GeneratePlan(g)
// then achieve g. That belief is never removed, so a goal that went through generation is generated again
// when it fails. It does not generate again when a goal's plans exist but none applies, nor when a goal
// that got its plans in the same reply as another (a subgoal the model invented) fails.
//
// Stage 3 adds, for an achievement goal g, a regeneration through their own path (handlePlanNotFound) when
// g's plans exist but none applies, and when g fails with no plan for its failure, if g has had fewer than
// Stage3Generations.MAX generations (the cap is enforced where generations happen, in the stage 3
// GeneratePlanStrategyImpl, which also removes g's plans before any generation but g's first, so the new
// plans replace them).
// The prompt is theirs, unchanged: it shows the new state, not the old plans or why they failed.

import it.unibo.jakta.agents.bdi.engine.actions.effects.EventChange
import it.unibo.jakta.agents.bdi.engine.context.AgentContext
import it.unibo.jakta.agents.bdi.engine.context.ContextUpdate.ADDITION
import it.unibo.jakta.agents.bdi.engine.events.AchievementGoalFailure
import it.unibo.jakta.agents.bdi.engine.events.AchievementGoalInvocation
import it.unibo.jakta.agents.bdi.engine.events.Event
import it.unibo.jakta.agents.bdi.engine.events.TestGoalInvocation
import it.unibo.jakta.agents.bdi.engine.executionstrategies.ExecutionResult
import it.unibo.jakta.agents.bdi.engine.executionstrategies.feedback.ExecutionFeedback
import it.unibo.jakta.agents.bdi.engine.executionstrategies.feedback.NegativeFeedback.InapplicablePlan
import it.unibo.jakta.agents.bdi.engine.executionstrategies.feedback.NegativeFeedback.PlanNotFound
import it.unibo.jakta.agents.bdi.engine.executionstrategies.feedback.PGPSuccess
import it.unibo.jakta.agents.bdi.engine.generation.GenerationStrategy
import it.unibo.jakta.agents.bdi.engine.generation.manager.UnavailablePlanStrategy
import it.unibo.jakta.agents.bdi.engine.logging.loggers.AgentLogger
import it.unibo.jakta.agents.bdi.engine.plans.PartialPlan
import it.unibo.jakta.agents.bdi.engine.plans.Plan
import it.unibo.tuprolog.core.Struct

internal class UnavailablePlanStrategyImpl(
    override val logger: AgentLogger?,
) : UnavailablePlanStrategy {
    override fun handleUnavailablePlans(
        selectedEvent: Event,
        relevantPlans: List<Plan>,
        isApplicablePlansEmpty: Boolean,
        context: AgentContext,
        generationStrategy: GenerationStrategy?,
    ): ExecutionResult =
        when {
            relevantPlans.isEmpty() ->
                // stage 3: a goal that failed with no plan for its failure is generated again
                regenerate(selectedEvent, context, generationStrategy, "failed")
                    ?: handlePlanNotFound(selectedEvent, context, generationStrategy)
            isApplicablePlansEmpty ->
                // stage 3: a goal whose plans exist but none applies is generated again
                regenerate(selectedEvent, context, generationStrategy, "no applicable plan")
                    ?: handleFailedPlanPreconditions(selectedEvent, relevantPlans, context)
            // not expected since either relevantPlans and/or applicablePlans should be empty
            else -> ExecutionResult(newAgentContext = context)
        }

    /** stage 3: the regeneration, or null where JaKtA's own handling applies. */
    private fun regenerate(
        selectedEvent: Event,
        context: AgentContext,
        generationStrategy: GenerationStrategy?,
        reason: String,
    ): ExecutionResult? {
        if (generationStrategy == null) return null
        val trigger = selectedEvent.trigger
        val goal = trigger.value as? Struct ?: return null
        // The failure of a goal is handled by regenerating the goal; an invocation with no relevant plan
        // is JaKtA's own first generation, left to it
        val invocation =
            when {
                trigger is AchievementGoalFailure -> AchievementGoalInvocation(goal)
                trigger is AchievementGoalInvocation && reason == "no applicable plan" -> trigger
                else -> return null
            }
        val key = Stage3Generations.key(goal)
        val done = Stage3Generations.count(goal)
        if (done >= Stage3Generations.MAX) return null
        logger?.warn { "Stage 3 regeneration for $key ($reason) after $done generations" }
        return handlePlanNotFound(Event.of(invocation, selectedEvent.intention), context, generationStrategy)
    }

    override fun handlePlanNotFound(
        selectedEvent: Event,
        context: AgentContext,
        generationStrategy: GenerationStrategy?,
    ): ExecutionResult {
        val initialGoal = selectedEvent.trigger
        val newPlan =
            if (generationStrategy != null &&
                (initialGoal is AchievementGoalInvocation || initialGoal is TestGoalInvocation)
            ) {
                GenerationPlanBuilder.getGenerationPlan(initialGoal)
            } else {
                null
            }
        val feedback =
            if (newPlan != null && newPlan.parentGenerationGoal != null) {
                val goal =
                    generationStrategy?.generationConfig?.let {
                        newPlan.parentGenerationGoal!!.copy(generationConfig = it)
                    } ?: newPlan.parentGenerationGoal!!
                PGPSuccess.GenerationRequested(
                    generationStrategy,
                    goal,
                )
            } else {
                PlanNotFound(initialGoal)
            }

        return createResult(selectedEvent, context, feedback, newPlan)
    }

    private fun handleFailedPlanPreconditions(
        selectedEvent: Event,
        relevantPlans: List<Plan>,
        context: AgentContext,
    ): ExecutionResult {
        val feedback =
            InapplicablePlan(
                relevantPlans
                    .map {
                        val ignoreSource = it is PartialPlan
                        it.checkApplicability(selectedEvent, context.beliefBase, ignoreSource)
                    }.filter { it.error == null && it.trigger != null && it.guards != null }
                    .also {
                        it.forEach { plan ->
                            logger?.warn { "Plan ${plan.trigger?.value} is not applicable" }
                            plan.guards?.forEach { guard ->
                                if (!guard.value) logger?.warn { "Failing guard ${guard.key}" }
                            }
                        }
                    },
            )

        return createResult(selectedEvent, context, feedback)
    }

    private fun createResult(
        selectedEvent: Event,
        context: AgentContext,
        feedback: ExecutionFeedback,
        newPlan: PartialPlan? = null,
    ): ExecutionResult =
        if (newPlan != null) {
            val failureTrigger = selectedEvent.trigger
            val newBelief = GenerationPlanBuilder.getMissingPlanBelief(failureTrigger.value)
            val newEvent = GenerationPlanBuilder.getFailureEvent(selectedEvent)
            ExecutionResult(
                newAgentContext =
                    context.copy(
                        planLibrary = context.planLibrary.addPlan(newPlan),
                        beliefBase = context.beliefBase.add(newBelief).updatedBeliefBase,
                        events = newEvent?.let { context.events + it } ?: context.events,
                    ),
                feedback = feedback,
            ).also {
                newEvent?.let { logger?.log { EventChange(it, ADDITION) } }
            }
        } else {
            ExecutionResult(
                newAgentContext = context,
                feedback = feedback,
            )
        }
}
