package it.unibo.jakta.playground.ports

import it.unibo.jakta.agents.bdi.dsl.plans
import it.unibo.tuprolog.core.Var

/**
 * Hand-written plans for the ported tasks (--baseline, no model), as ASTRA's reference rules are: each port
 * must succeed with them before any model run. Hand-written plans name the beliefs' sources (percept, self,
 * the oracle); generated plans need not, as JaKtA matches their conditions whatever the source.
 * Also the oracle's own plan, which is part of the keys task in every run.
 */
object PortBaselines {
    internal val R = Var.of("R")
    internal val N = Var.of("N")
    internal val I = Var.of("I")
    internal val L = Var.of("L")
    internal val X = Var.of("X")
    internal val A = Var.of("A")
    internal val D = Var.of("D")
    internal val P = Var.of("P")

    /** Visit every room (marking it visited), cleaning each room found dirty. */
    val rooms =
        plans {
            +achieve("clean_house") onlyIf { "at"(R).fromPercept and "dirty"(R).fromPercept } then {
                execute("clean")
                achieve("clean_house")
            }
            +achieve("clean_house") onlyIf { "at"(R).fromPercept and not("visited"(R).fromSelf) } then {
                add("visited"(R))
                achieve("clean_house")
            }
            +achieve("clean_house") onlyIf { "room"(R).fromPercept and not("visited"(R).fromSelf) } then {
                execute("next"(R, N))
                execute("go"(N))
                achieve("clean_house")
            }
            +achieve("clean_house") then { }
        }

    /** Prepare the tea step by step from the kitchen as it is, then serve it. */
    val tea =
        plans {
            +achieve("serve"(D, P)) then {
                achieve("prepare"(D))
                execute("serve"(P))
            }
            +achieve("prepare"(D)) onlyIf { "kettle"("empty").fromPercept } then {
                execute("fill")
                achieve("prepare"(D))
            }
            +achieve("prepare"(D)) onlyIf { "kettle"("full").fromPercept } then {
                execute("boil")
                achieve("prepare"(D))
            }
            +achieve("prepare"(D)) onlyIf { "in"("cup", "cupboard").fromPercept } then {
                execute("take"("cup"))
                achieve("prepare"(D))
            }
            +achieve("prepare"(D)) onlyIf { "in"("teabag", "cupboard").fromPercept } then {
                execute("take"("teabag"))
                achieve("prepare"(D))
            }
            +achieve("prepare"(D)) onlyIf { "in"("teabag", "counter").fromPercept } then {
                execute("putInCup"("teabag"))
                achieve("prepare"(D))
            }
            +achieve("prepare"(D)) onlyIf { "kettle"("hot").fromPercept } then {
                execute("pour")
            }
        }

    /** Ask the oracle, wait for its answer by sleeping and adopting the goal again, fetch the item, bring it back. */
    val keys =
        plans {
            +achieve("fetch"(I, L)) onlyIf { "holding"(I).fromPercept and "at"(L).fromPercept } then {
                execute("drop"(I))
            }
            +achieve("fetch"(I, L)) onlyIf { "holding"(I).fromPercept } then {
                achieve("go_to"(L))
                achieve("fetch"(I, L))
            }
            +achieve("fetch"(I, L)) onlyIf { "here"(I).fromPercept } then {
                execute("pick"(I))
                achieve("fetch"(I, L))
            }
            +achieve("fetch"(I, L)) onlyIf { "located"(I, X).source(PortActions.ORACLE) } then {
                achieve("go_to"(X))
                achieve("fetch"(I, L))
            }
            +achieve("fetch"(I, L)) onlyIf { "asked"(I).fromSelf } then {
                iact("sleep"(100))
                achieve("fetch"(I, L))
            }
            +achieve("fetch"(I, L)) then {
                execute("ask"(I))
                add("asked"(I))
                achieve("fetch"(I, L))
            }
            +achieve("go_to"(L)) onlyIf { "at"(L).fromPercept } then { }
            +achieve("go_to"(L)) then {
                execute("next"(L, N))
                execute("go"(N))
                achieve("go_to"(L))
            }
        }

    /** Clear the jam, send the document once, then wait (sleeping and adopting the goal again) until it has printed. */
    val print =
        plans {
            +achieve("printed"(D)) onlyIf { "completed"(D).fromPercept } then { }
            +achieve("printed"(D)) onlyIf { "printer"("jammed").fromPercept } then {
                execute("clear")
                achieve("printed"(D))
            }
            +achieve("printed"(D)) onlyIf { "printer"("ready").fromPercept and not("sent"(D).fromSelf) } then {
                execute("send"(D))
                add("sent"(D))
                achieve("printed"(D))
            }
            +achieve("printed"(D)) then {
                iact("sleep"(100))
                achieve("printed"(D))
            }
        }

    /** Plans that do nothing for the task's goal (--empty): the run must fail. */
    fun empty(port: String) =
        plans {
            when (port) {
                "rooms" -> +achieve("clean_house") then { }
                "tea" -> +achieve("serve"(D, P)) then { }
                "keys" -> +achieve("fetch"(I, L)) then { }
                else -> +achieve("printed"(D)) then { }
            }
        }

    /** The oracle: answers a question after 500 ms, as ASTRA's oracle does. */
    val oracle =
        plans {
            +achieve("answer"(I, X, A)) then {
                iact("sleep"(500))
                execute("tellLocation"(I, X, A))
            }
        }
}
