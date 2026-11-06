package it.unibo.jakta.playground.ports

import it.unibo.tuprolog.core.Atom
import it.unibo.tuprolog.core.Struct
import kotlin.random.Random

/*
 * The worlds of the four tasks ported from ASTRA's planning trials (my-planning-trials, JaKtA comparison
 * stage 2): the same rooms, doors, items, kitchen, printer and delays as ASTRA's House, Kitchen and
 * PrintRoom modules, as immutable states. An action makes a new state, or null when it fails (its
 * preconditions do not hold), so that a failed action changes nothing.
 */

/** The state of one task's world: what the agent perceives now, and whether the task's goal holds now. */
sealed interface PortState {
    fun percepts(now: Long): List<Struct>

    fun check(now: Long): Boolean
}

internal fun fact(
    functor: String,
    vararg values: String,
): Struct = Struct.of(functor, values.map { Atom.of(it) })

/** A house of rooms joined by doors (rooms and keys): ASTRA's House. */
data class HouseState(
    val task: String,
    val doors: List<Pair<String, String>>,
    val at: String,
    val dirty: Set<String>,
    // item -> room, or "held"
    val items: Map<String, String>,
) : PortState {
    val rooms: List<String> = doors.flatMap { listOf(it.first, it.second) }.distinct()

    fun door(
        from: String,
        to: String,
    ) = doors.any { it.first == from && it.second == to || it.second == from && it.first == to }

    override fun percepts(now: Long): List<Struct> =
        rooms.map { fact("room", it) } +
            doors.flatMap { listOf(fact("door", it.first, it.second), fact("door", it.second, it.first)) } +
            fact("at", at) +
            (if (at in dirty) listOf(fact("dirty", at)) else emptyList()) +
            items.filterValues { it == at }.keys.map { fact("here", it) } +
            items.filterValues { it == "held" }.keys.map { fact("holding", it) }

    override fun check(now: Long) =
        when (task) {
            "rooms" -> dirty.isEmpty()
            else -> items["keys"] == "hall"
        }

    fun go(room: String) = if (door(at, room)) copy(at = room) else null

    /** The room next to the agent's on a shortest way to the room (its own room if it is there); null for an unknown room. */
    fun next(room: String): String? {
        if (at == room) return room
        val previous = mutableMapOf<String, String>()
        val queue = ArrayDeque(listOf(at))
        val seen = mutableSetOf(at)
        while (queue.isNotEmpty()) {
            val r = queue.removeFirst()
            for (n in rooms) {
                if (door(r, n) && seen.add(n)) {
                    previous[n] = r
                    queue.add(n)
                }
            }
        }
        if (room !in previous) return null
        var step = room
        while (previous.getValue(step) != at) step = previous.getValue(step)
        return step
    }

    fun clean() = copy(dirty = dirty - at)

    fun pick(item: String) = if (items[item] == at) copy(items = items + (item to "held")) else null

    fun drop(item: String) = if (items[item] == "held") copy(items = items + (item to at)) else null

    /** Where the oracle says the item is: its room, or nowhere if it is held or unknown. */
    fun whereIs(item: String) = items[item]?.takeIf { it != "held" } ?: "nowhere"

    companion object {
        /** The rooms task's house: six rooms, with a loop through kitchen, garden, library and study. */
        val ROOMS =
            HouseState(
                "rooms",
                listOf(
                    "hall" to "kitchen", "kitchen" to "pantry", "hall" to "study", "study" to "library",
                    "kitchen" to "garden", "garden" to "library",
                ),
                "hall",
                setOf("pantry", "library", "garden"),
                emptyMap(),
            )

        /** The keys task's house: the keys are upstairs, two rooms from the hall. */
        val KEYS =
            HouseState(
                "keys",
                listOf(
                    "hall" to "lounge", "hall" to "landing", "landing" to "bedroom", "landing" to "bathroom",
                    "lounge" to "study",
                ),
                "hall",
                emptySet(),
                linkedMapOf("keys" to "bedroom", "umbrella" to "hall"),
            )
    }
}

/** A kitchen with a kettle, a cup and a teabag (tea): ASTRA's Kitchen. */
data class KitchenState(
    val kettle: String = "full",
    val places: Map<String, String> = linkedMapOf("cup" to "counter", "teabag" to "cupboard", "sugar" to "cupboard"),
    val inCup: List<String> = emptyList(),
    val served: String? = null,
) : PortState {
    override fun percepts(now: Long): List<Struct> =
        listOf(fact("kettle", kettle)) +
            places.map { (item, place) -> fact("in", item, place) } +
            inCup.map { fact("in", it, "cup") }

    override fun check(now: Long) = served == "alice:tea"

    fun fill() = if (kettle == "empty") copy(kettle = "full") else null

    fun boil() = if (kettle != "empty") copy(kettle = "hot") else null

    fun take(item: String) = if (places[item] == "cupboard") copy(places = places + (item to "counter")) else null

    fun putInCup(item: String) =
        if (item != "cup" && places[item] == "counter" && places["cup"] == "counter") {
            copy(places = places - item, inCup = inCup + item)
        } else {
            null
        }

    fun pour() =
        if (kettle == "hot" && places["cup"] == "counter") copy(kettle = "empty", inCup = inCup + "water") else null

    fun serve(person: String) =
        if (places["cup"] == "counter" && "teabag" in inCup && "water" in inCup) {
            copy(places = places + ("cup" to person), served = "$person:tea")
        } else {
            null
        }
}

/**
 * A printer that starts jammed (print): ASTRA's PrintRoom. A document sent prints for a random 0.2 to 3 s;
 * the printer is busy until then, and the agent then perceives completed(doc) and the printer ready again.
 */
data class PrintState(
    // jammed or ready, before any job in progress
    val printer: String = "jammed",
    val sent: Map<String, Int> = emptyMap(),
    // (doc, when it has printed)
    val jobs: List<Pair<String, Long>> = emptyList(),
) : PortState {
    fun printerNow(now: Long) = if (jobs.any { it.second > now }) "busy" else printer

    fun printed(
        doc: String,
        now: Long,
    ) = jobs.count { it.first == doc && it.second <= now }

    override fun percepts(now: Long): List<Struct> =
        listOf(fact("printer", printerNow(now))) +
            jobs.filter { it.second <= now }.map { it.first }.distinct().map { fact("completed", it) }

    /** When the goal ends: the report has been printed, and sent once. */
    override fun check(now: Long) = printed("report", now) == 1 && sent["report"] == 1

    fun clear(now: Long) = if (printerNow(now) == "jammed") copy(printer = "ready") else null

    fun send(
        doc: String,
        now: Long,
    ) = if (printerNow(now) == "ready") {
        copy(sent = sent + (doc to (sent[doc] ?: 0) + 1), jobs = jobs + (doc to now + Random.nextLong(200, 3001)))
    } else {
        null
    }
}
