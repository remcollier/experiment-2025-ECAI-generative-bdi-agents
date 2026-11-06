package it.unibo.jakta.playground.ports

import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.terminal.Terminal
import it.unibo.jakta.agents.bdi.dsl.MasScope
import it.unibo.jakta.agents.bdi.dsl.actions.ActionMetadata.meaning
import it.unibo.jakta.agents.bdi.dsl.beliefs.BeliefMetadata.meaning
import it.unibo.jakta.agents.bdi.dsl.goals.TriggerMetadata.meaning
import it.unibo.jakta.agents.bdi.dsl.loggingConfig
import it.unibo.jakta.agents.bdi.dsl.mas
import it.unibo.jakta.agents.bdi.dsl.plans
import it.unibo.jakta.agents.bdi.engine.executionstrategies.ExecutionStrategy
import it.unibo.jakta.agents.bdi.engine.generation.GenerationStrategy
import it.unibo.jakta.agents.bdi.engine.logging.LoggingConfig
import it.unibo.jakta.agents.bdi.generationstrategies.lm.dsl.DSLExtensions.lmGeneration
import it.unibo.jakta.agents.bdi.generationstrategies.lm.pipeline.filtering.ContextFilter
import it.unibo.jakta.agents.bdi.generationstrategies.lm.pipeline.filtering.DefaultFilters.metaPlanFilter
import it.unibo.jakta.agents.bdi.generationstrategies.lm.pipeline.filtering.DefaultFilters.printActionFilter
import it.unibo.jakta.agents.bdi.generationstrategies.lm.pipeline.filtering.ExtendedAgentContext
import it.unibo.jakta.agents.bdi.generationstrategies.lm.pipeline.formatting.DefaultPromptBuilder.systemPrompt
import it.unibo.jakta.playground.AbstractExperiment
import it.unibo.jakta.playground.ModuleLoader.jsonModule
import kotlin.time.Duration.Companion.seconds

/**
 * Four of ASTRA's planning-trials tasks in JaKtA (my-planning-trials, JaKtA comparison stage 2): rooms, tea,
 * keys and print, chosen with --port. The vocabulary is ASTRA's: the same beliefs, goals and actions, with
 * ASTRA's descriptions word for word as their meanings (shown with PROMPT_WITH_HINTS, left out without hints).
 * The system prompt, prompt builders and default filters are JaKtA's own; there are no remarks.
 *
 * --baseline runs the task with hand-written plans and no model, to check the port can be done; with --empty,
 * plans that do nothing, which must fail (as ASTRA's reference rules and empty rules check its tasks).
 */
class PortExperiment : AbstractExperiment() {
    val port: String by option()
        .choice("rooms", "tea", "keys", "print")
        .required()
        .help("The task to run")

    val baseline: Boolean by option()
        .flag()
        .help("Run with hand-written plans and no model")

    val empty: Boolean by option()
        .flag()
        .help("With --baseline, plans that do nothing instead, which must fail")

    override fun createMas(
        logConfig: LoggingConfig,
        genStrat: GenerationStrategy?,
    ) = mas {
        executionStrategy = ExecutionStrategy.oneThreadPerAgent()
        generationStrategy = if (baseline) null else genStrat
        loggingConfig = logConfig
        modules = listOf(jsonModule)
        when (port) {
            "rooms" -> rooms()
            "tea" -> tea()
            "keys" -> keys()
            else -> print()
        }
    }

    // The rooms task: clean every room; dirt is seen only in the room the agent is in
    private fun MasScope.rooms() {
        agent(ROBOT) {
            goals {
                admissible {
                    +achieve("clean_house").meaning { "Every room of the house is clean" }
                }
                +achieve("clean_house")
            }
            beliefs {
                admissible {
                    +fact { "room"("Room") }.meaning { "The room is one of the house's rooms" }
                    +fact { "door"("From", "To") }.meaning {
                        "A door joins the two rooms: the agent can go from the first to the second"
                    }
                    +fact { "at"("Room") }.meaning { "The agent is in the room" }
                    +fact { "dirty"("Room") }.meaning { "The room is dirty; the agent sees dirt only in the room it is in" }
                }
            }
            if (baseline) plans(if (empty) PortBaselines.empty("rooms") else PortBaselines.rooms)
        }
        environment {
            from(PortEnvironment(data = mapOf("state" to HouseState.ROOMS)))
            actions {
                action(PortActions.go).meaning {
                    "Go through the door from the agent's room to the room; fails if there is no door between them"
                }
                action(PortActions.next).meaning {
                    "Find the room next to the agent's on a shortest way to the room, as next " +
                        "(the agent's own room if it is already there); fails for an unknown room"
                }
                action(PortActions.clean).meaning { "Clean the room the agent is in" }
            }
        }
    }

    // The tea task: serve alice a cup of tea; two goals, actions with preconditions, a kettle already full
    private fun MasScope.tea() {
        agent(ROBOT) {
            goals {
                admissible {
                    +achieve("prepare"("Drink")).meaning { "Make the drink in the cup, on the counter" }
                    +achieve("serve"("Drink", "Person")).meaning { "Give the person a cup of the drink" }
                }
                +achieve("serve"("tea", "alice"))
            }
            beliefs {
                admissible {
                    +fact { "kettle"("State") }.meaning { "The kettle is empty, full (of cold water) or hot" }
                    +fact { "in"("Item", "Place") }.meaning {
                        "The item is in the place: the cupboard, the counter, or the cup"
                    }
                }
            }
            if (baseline) plans(if (empty) PortBaselines.empty("tea") else PortBaselines.tea)
        }
        environment {
            from(PortEnvironment(data = mapOf("state" to KitchenState())))
            actions {
                action(PortActions.fill).meaning { "Fill the kettle with cold water; fails unless the kettle is empty" }
                action(PortActions.boil).meaning { "Boil the water in the kettle; fails if the kettle is empty" }
                action(PortActions.take).meaning {
                    "Take the item out of the cupboard and put it on the counter; fails unless it is in the cupboard"
                }
                action(PortActions.putInCup).meaning {
                    "Put an item from the counter into the cup; fails unless the item and the cup are on the counter"
                }
                action(PortActions.pour).meaning {
                    "Pour the water from the kettle into the cup, which empties the kettle; " +
                        "fails unless the water is hot and the cup is on the counter"
                }
                action(PortActions.serve).meaning {
                    "Give the cup to the person; fails unless the cup is on the counter with a teabag and hot water in it"
                }
            }
        }
    }

    // The keys task: fetch the keys to the hall; their place is known only by asking the oracle
    private fun MasScope.keys() {
        agent(ROBOT) {
            goals {
                admissible {
                    +achieve("go_to"("Room")).meaning { "Be in the room" }
                    +achieve("fetch"("Item", "Room")).meaning { "Bring the item to the room, and put it down there" }
                }
                +achieve("fetch"("keys", "hall"))
            }
            beliefs {
                admissible {
                    +fact { "room"("Room") }.meaning { "The room is one of the house's rooms" }
                    +fact { "door"("From", "To") }.meaning {
                        "A door joins the two rooms: the agent can go from the first to the second"
                    }
                    +fact { "at"("Room") }.meaning { "The agent is in the room" }
                    +fact { "here"("Item") }.meaning {
                        "The item is in the agent's room; the agent sees only the items in the room it is in"
                    }
                    +fact { "holding"("Item") }.meaning { "The agent is holding the item" }
                    +fact { "located"("Item", "Room") }.meaning {
                        "The oracle has said that the item is in the room (nowhere, if it does not know)"
                    }
                }
            }
            if (baseline) plans(if (empty) PortBaselines.empty("keys") else PortBaselines.keys)
        }
        agent(PortActions.ORACLE) {
            plans(PortBaselines.oracle)
        }
        environment {
            from(PortEnvironment(data = mapOf("state" to HouseState.KEYS)))
            actions {
                action(PortActions.go).meaning {
                    "Go through the door from the agent's room to the room; fails if there is no door between them"
                }
                action(PortActions.next).meaning {
                    "Find the room next to the agent's on a shortest way to the room, as next " +
                        "(the agent's own room if it is already there); fails for an unknown room"
                }
                action(PortActions.pick).meaning { "Pick up the item; fails unless it is in the agent's room" }
                action(PortActions.drop).meaning {
                    "Put down an item the agent is holding, in the agent's room; fails if it is not holding it"
                }
                action(PortActions.ask).meaning {
                    "Ask the oracle agent where the item is. " +
                        "The oracle answers later, with an inform message whose content is located(item, room)"
                }
                action(PortActions.tellLocation)
            }
        }
    }

    // The print task: print the report; the printer starts jammed, and printing takes a while
    private fun MasScope.print() {
        agent(ROBOT) {
            goals {
                admissible {
                    +achieve("printed"("Doc")).meaning { "The document has been printed" }
                }
                +achieve("printed"("report"))
            }
            beliefs {
                admissible {
                    +fact { "printer"("State") }.meaning { "The printer is jammed, ready or busy" }
                    +fact { "completed"("Doc") }.meaning { "The document has been printed" }
                }
            }
            if (baseline) plans(if (empty) PortBaselines.empty("print") else PortBaselines.print)
        }
        environment {
            from(PortEnvironment(data = mapOf("state" to PrintState())))
            actions {
                action(PortActions.clear).meaning { "Clear a paper jam; fails unless the printer is jammed" }
                action(PortActions.send).meaning {
                    "Send the document to the printer; fails unless the printer is ready. " +
                        "The document takes a while to print; when it has, the agent perceives completed(doc)"
                }
            }
        }
    }

    override fun createLoggingConfig(expName: String) =
        loggingConfig {
            logToFile = this@PortExperiment.logToFile
            logToConsole = this@PortExperiment.logToConsole
            logToServer = this@PortExperiment.logToServer
            logLevel = this@PortExperiment.logLevel.level
            logDir = "${this@PortExperiment.logDir}/$expName"
            logServerUrl = this@PortExperiment.logServerUrl
        }

    override fun createGenerationStrategy() =
        lmGeneration {
            url = lmServerUrl
            token = lmServerToken
            model = modelId
            maxTokens = this@PortExperiment.maxTokens
            temperature = this@PortExperiment.temperature
            requestTimeout = 240.seconds
            systemPromptBuilder = systemPrompt
            userPromptBuilder = this@PortExperiment.promptType.builder
            contextFilters = listOf(metaPlanFilter, printActionFilter, oracleFilter)
        }

    companion object {
        const val ROBOT = "Robot"

        /** Leaves the oracle's own reply action out of the robot's prompt. */
        val oracleFilter =
            object : ContextFilter {
                override val name = "OracleFilter"

                override fun filter(extendedContext: ExtendedAgentContext): ExtendedAgentContext =
                    extendedContext.copy(
                        externalActions =
                            extendedContext.externalActions.filterNot {
                                it.actionSignature.name == PortActions.tellLocation.actionSignature.name
                            },
                    )
            }
    }
}

fun main(args: Array<String>): Unit =
    PortExperiment()
        .context { terminal = Terminal(ansiLevel = AnsiLevel.TRUECOLOR, interactive = true) }
        .main(args)
