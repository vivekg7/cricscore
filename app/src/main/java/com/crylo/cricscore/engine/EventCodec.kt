package com.crylo.cricscore.engine

import com.crylo.cricscore.engine.MatchEvent.Delivery
import com.crylo.cricscore.engine.MatchEvent.EndInnings
import com.crylo.cricscore.engine.MatchEvent.NewBatter
import com.crylo.cricscore.engine.MatchEvent.NewBowler
import com.crylo.cricscore.engine.MatchEvent.Retire
import com.crylo.cricscore.engine.MatchEvent.StartInnings
import com.crylo.cricscore.engine.MatchEvent.SwapStrike

/**
 * Stores the event list as one short line per event, e.g. `D NORMAL 4` or
 * `D NORMAL 1 RUN_OUT NON_STRIKER STRIKER`. Plain text keeps the database readable
 * when debugging a match and avoids a serialization library for six event types.
 * Enum names (not ordinals) are written, so reordering an enum can't corrupt history.
 */
object EventCodec {

    fun encode(events: List<MatchEvent>): String = events.joinToString("\n") { encode(it) }

    fun encode(e: MatchEvent): String = when (e) {
        is StartInnings -> "S ${e.striker} ${e.nonStriker} ${e.bowler}"
        is Delivery -> buildString {
            append("D ${e.kind.name} ${e.runs}")
            e.wicket?.let { append(" ${it.type.name} ${it.batter.name} ${it.end.name}") }
        }
        is NewBatter -> "B ${e.player}"
        is NewBowler -> "O ${e.player}"
        is Retire -> "R ${e.batter.name}"
        SwapStrike -> "X"
        EndInnings -> "E"
    }

    /** Lines that don't parse are dropped rather than failing the whole match. */
    fun decode(text: String): List<MatchEvent> = text.lineSequence().mapNotNull { decodeLine(it) }.toList()

    private fun decodeLine(line: String): MatchEvent? = runCatching {
        val t = line.trim().split(' ')
        when (t[0]) {
            "S" -> StartInnings(t[1].toInt(), t[2].toInt(), t[3].toInt())
            "D" -> Delivery(
                kind = DeliveryKind.valueOf(t[1]),
                runs = t[2].toInt(),
                wicket = if (t.size >= 6) Wicket(WicketType.valueOf(t[3]), Slot.valueOf(t[4]), Slot.valueOf(t[5])) else null,
            )
            "B" -> NewBatter(t[1].toInt())
            "O" -> NewBowler(t[1].toInt())
            "R" -> Retire(Slot.valueOf(t[1]))
            "X" -> SwapStrike
            "E" -> EndInnings
            else -> null
        }
    }.getOrNull()
}
