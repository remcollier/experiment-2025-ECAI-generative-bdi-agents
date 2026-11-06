package it.unibo.jakta.playground.ports

import it.unibo.jakta.agents.bdi.engine.AgentID
import it.unibo.jakta.agents.bdi.engine.actions.ExternalAction
import it.unibo.jakta.agents.bdi.engine.beliefs.Belief
import it.unibo.jakta.agents.bdi.engine.beliefs.BeliefBase
import it.unibo.jakta.agents.bdi.engine.environment.impl.EnvironmentImpl
import it.unibo.jakta.agents.bdi.engine.logging.loggers.MasLogger
import it.unibo.jakta.agents.bdi.engine.messages.MessageQueue
import it.unibo.jakta.agents.bdi.engine.perception.Perception

/**
 * The environment of a ported task, on the pattern of GridWorldEnvironment: the task's state is kept in the
 * environment's data, and the agent's percepts are recomputed from it at every perception. Whenever the
 * task's check (whether its goal holds) changes, the environment logs "Task check: true" or "false", so that
 * a run can be judged from its logs at the moment its goal ends, as ASTRA's harness judges it.
 */
// CPD-OFF
class PortEnvironment(
    agentIDs: Map<String, AgentID> = emptyMap(),
    externalActions: Map<String, ExternalAction> = emptyMap(),
    messageBoxes: Map<AgentID, MessageQueue> = emptyMap(),
    override var perception: Perception = Perception.empty(),
    data: Map<String, Any> = emptyMap(),
    override val logger: MasLogger? = null,
) : EnvironmentImpl(externalActions, agentIDs, messageBoxes, perception, data, logger) {
    init {
        perception = Perception.of(beliefs())
    }

    fun state() = data["state"] as? PortState

    private fun beliefs(): List<Belief> {
        val state = state() ?: return emptyList()
        val now = System.currentTimeMillis()
        logCheck(state, now, made = false)
        return state.percepts(now).map { Belief.wrap(it, wrappingTag = Belief.SOURCE_PERCEPT) }
    }

    /**
     * Logs the task's check if it has changed since it was last logged. The check is of the latest state an
     * action has made (an action passes its new state as made), not of this copy's, which may be older: an
     * agent can perceive from an environment its own action has not yet updated, and its check must not
     * flicker back.
     */
    fun logCheck(
        state: PortState,
        now: Long = System.currentTimeMillis(),
        made: Boolean = true,
    ) {
        val log = logger ?: return
        synchronized(Companion) {
            if (made) latest = state
            val check = (latest ?: state).check(now)
            if (check != lastCheck) {
                lastCheck = check
                log.info { "Task check: $check" }
            }
        }
    }

    override fun updateData(newData: Map<String, Any>): PortEnvironment = copy(data = newData)

    override fun percept(): BeliefBase {
        perception = Perception.of(beliefs())
        return super.percept()
    }

    override fun copy(
        agentIDs: Map<String, AgentID>,
        externalActions: Map<String, ExternalAction>,
        messageBoxes: Map<AgentID, MessageQueue>,
        perception: Perception,
        data: Map<String, Any>,
        logger: MasLogger?,
    ): PortEnvironment = PortEnvironment(agentIDs, externalActions, messageBoxes, perception, data, logger)

    companion object {
        // One MAS per process (a run), so the latest state and the last check logged can be kept here
        private var latest: PortState? = null
        private var lastCheck: Boolean? = null
    }
}
// CPD-ON
