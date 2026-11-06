package it.unibo.jakta.playground.ports

import it.unibo.jakta.agents.bdi.dsl.actions.ExternalActionScope
import it.unibo.jakta.agents.bdi.dsl.externalAction
import it.unibo.jakta.agents.bdi.engine.messages.Achieve
import it.unibo.jakta.agents.bdi.engine.messages.Message
import it.unibo.jakta.agents.bdi.engine.messages.Tell
import it.unibo.tuprolog.core.Atom
import it.unibo.tuprolog.core.Struct
import it.unibo.tuprolog.core.Substitution
import it.unibo.tuprolog.core.Term
import it.unibo.tuprolog.unify.Unificator

/*
 * The external actions of the ported tasks, the same as the actions of ASTRA's world modules (the same
 * names, parameters and preconditions). An action whose preconditions do not hold fails (a failed
 * substitution), and then changes nothing.
 */

private fun ExternalActionScope.atom(i: Int): String? = (arguments.getOrNull(i) as? Atom)?.value

private fun ExternalActionScope.fail() = addResults(Substitution.failed())

/**
 * An external action that reports its result afresh at every call. JaKtA's AbstractAction keeps the last
 * substitution an action reported (addResults) between calls, so an action that failed once and reported
 * nothing on a later success would fail again (fault found 4 November, in stage 2's runs): each call starts
 * from the empty substitution, which a failure or an output binding then replaces.
 */
private fun portAction(
    name: String,
    vararg parameters: String,
    f: ExternalActionScope.() -> Unit,
) = externalAction(name, *parameters) {
    addResults(Substitution.empty())
    f()
}

/** Replaces the task's state by the one the change makes, or fails if it makes none. */
private inline fun <reified S : PortState> ExternalActionScope.change(f: (S) -> S?) {
    val env = environment as? PortEnvironment
    val state = env?.state() as? S
    val next = state?.let(f)
    if (env == null || next == null) {
        fail()
    } else {
        updateData("state" to next)
        env.logCheck(next)
    }
}

/** Binds an output argument to the value by unification: a variable is bound; a bound argument must be equal, or the action fails. */
private fun ExternalActionScope.output(
    argument: Term,
    value: Term,
) {
    val mgu = Unificator.default.mgu(argument, value)
    if (mgu.isSuccess) addResults(mgu) else fail()
}

object PortActions {
    // Rooms and keys

    val go = portAction("go", "room") { atom(0)?.let { r -> change<HouseState> { it.go(r) } } ?: fail() }

    /** The next room on a shortest way to the room, as an output argument (ASTRA's next with an ActionParam). */
    val next =
        portAction("next", "room", "next") {
            val state = (environment as? PortEnvironment)?.state() as? HouseState
            val step = atom(0)?.let { state?.next(it) }
            val out = arguments.getOrNull(1)
            if (step == null || out == null) fail() else output(out, Atom.of(step))
        }

    val clean = portAction("clean") { change<HouseState> { it.clean() } }

    val pick = portAction("pick", "item") { atom(0)?.let { i -> change<HouseState> { it.pick(i) } } ?: fail() }

    val drop = portAction("drop", "item") { atom(0)?.let { i -> change<HouseState> { it.drop(i) } } ?: fail() }

    /**
     * Asks the oracle agent where the item is: an achieve message to the oracle, which answers after 500 ms
     * (as ASTRA's oracle does) with a tell message whose content is located(item, room).
     */
    val ask =
        portAction("ask", "item") {
            val state = (environment as? PortEnvironment)?.state() as? HouseState
            val item = atom(0)
            if (state == null || item == null) {
                fail()
            } else {
                val answer = Struct.of("answer", Atom.of(item), Atom.of(state.whereIs(item)), Atom.of(sender))
                sendMessage(ORACLE, Message(sender, Achieve, answer))
            }
        }

    /** The oracle's reply (hidden from the robot's prompt by OracleFilter). */
    val tellLocation =
        portAction("tellLocation", "item", "room", "agent") {
            val (item, room, agent) = listOf(atom(0), atom(1), atom(2))
            if (item == null || room == null || agent == null) {
                fail()
            } else {
                sendMessage(agent, Message(ORACLE, Tell, fact("located", item, room)))
            }
        }

    // Tea

    val fill = portAction("fill") { change<KitchenState> { it.fill() } }

    val boil = portAction("boil") { change<KitchenState> { it.boil() } }

    val take = portAction("take", "item") { atom(0)?.let { i -> change<KitchenState> { it.take(i) } } ?: fail() }

    val putInCup =
        portAction("putInCup", "item") { atom(0)?.let { i -> change<KitchenState> { it.putInCup(i) } } ?: fail() }

    val pour = portAction("pour") { change<KitchenState> { it.pour() } }

    val serve =
        portAction("serve", "person") { atom(0)?.let { p -> change<KitchenState> { it.serve(p) } } ?: fail() }

    // Print

    val clear = portAction("clear") { change<PrintState> { it.clear(System.currentTimeMillis()) } }

    val send =
        portAction("send", "doc") {
            atom(0)?.let { d -> change<PrintState> { it.send(d, System.currentTimeMillis()) } } ?: fail()
        }

    const val ORACLE = "oracle"
}
