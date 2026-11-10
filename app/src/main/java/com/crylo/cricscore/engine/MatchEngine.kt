package com.crylo.cricscore.engine

import com.crylo.cricscore.engine.MatchConfig.Companion.BALLS_PER_OVER
import com.crylo.cricscore.engine.MatchEvent.Delivery
import com.crylo.cricscore.engine.MatchEvent.EndInnings
import com.crylo.cricscore.engine.MatchEvent.NewBatter
import com.crylo.cricscore.engine.MatchEvent.NewBowler
import com.crylo.cricscore.engine.MatchEvent.Retire
import com.crylo.cricscore.engine.MatchEvent.StartInnings
import com.crylo.cricscore.engine.MatchEvent.SwapStrike

/**
 * Derives the whole match from its event list. Pure and deterministic: same config and
 * events in, same state out. Events that make no sense in context (a ball with no
 * bowler, a new batter with no empty crease) are skipped rather than thrown on, so a
 * stored match can always be opened.
 */
object MatchEngine {

    fun replay(config: MatchConfig, events: List<MatchEvent>): MatchState {
        val innings = mutableListOf(InningsBuilder(0, config))
        for (event in events) {
            var cur = innings.last()
            if (event is StartInnings && cur.complete && innings.size < 2) {
                cur = InningsBuilder(1, config).also { innings += it }
            }
            val target = if (cur.index == 1) innings[0].runs + 1 else null
            cur.apply(event, target)
        }
        val built = innings.map { it.build() }
        val result = result(config, built)
        return MatchState(config, built, if (result == null) pending(config, built) else null, result)
    }

    /**
     * Drops the last thing the scorer did. Picking a new batter or bowler is a prompt
     * that follows a ball rather than an action of its own, so it goes together with the
     * ball (or retirement, or innings start) that caused it.
     */
    fun undo(events: List<MatchEvent>): List<MatchEvent> {
        val last = events.indexOfLast { it !is NewBatter && it !is NewBowler }
        return if (last < 0) emptyList() else events.subList(0, last)
    }

    private fun result(config: MatchConfig, innings: List<Innings>): MatchResult? {
        val second = innings.getOrNull(1)?.takeIf { it.complete } ?: return null
        val target = innings[0].runs + 1
        return when {
            second.runs >= target -> MatchResult.WonByWickets(second.battingTeam, config.maxWickets - second.wickets)
            second.runs == target - 1 -> MatchResult.Tie
            else -> MatchResult.WonByRuns(innings[0].battingTeam, target - 1 - second.runs)
        }
    }

    private fun pending(config: MatchConfig, innings: List<Innings>): Pending? {
        val cur = innings.last()
        if (cur.complete) return if (cur.index == 0) Pending.StartInnings(1) else null
        if (!cur.started) return Pending.StartInnings(cur.index)
        if (cur.striker == null) return Pending.NewBatter(Slot.STRIKER, cur.available)
        if (cur.nonStriker == null && cur.available.isNotEmpty()) return Pending.NewBatter(Slot.NON_STRIKER, cur.available)
        if (cur.bowler == null) {
            val previous = cur.overs.lastOrNull()?.bowler
            val beforeThat = cur.overs.getOrNull(cur.overs.size - 2)?.bowler
            // Two bowlers swapping ends is the usual pattern, so suggest the one who bowled
            // the over before last; otherwise just move along the fielding side.
            val suggested = beforeThat?.takeIf { it != previous }
                ?: ((previous ?: -1) + 1).mod(config.playersPerSide)
            return Pending.NewBowler(suggested, previous)
        }
        return null
    }
}

private class BatterAcc(val player: Int) {
    var runs = 0
    var balls = 0
    var fours = 0
    var sixes = 0
    var dismissal: Dismissal? = null
    var retired = false

    fun line() = BatterLine(player, runs, balls, fours, sixes, dismissal, retired)
}

private class BowlerAcc(val player: Int) {
    var balls = 0
    var runs = 0
    var wickets = 0
    var maidens = 0
    var wides = 0
    var noBalls = 0

    fun line() = BowlerLine(player, balls, runs, wickets, maidens, wides, noBalls)
}

private class PartnershipAcc(val batters: List<Int>) {
    var runs = 0
    var balls = 0
}

private class InningsBuilder(val index: Int, val config: MatchConfig) {
    private val rules = config.rules
    val battingTeam = config.battingTeam(index)

    var started = false
    var complete = false
    var runs = 0
    private var wickets = 0
    private var legalBalls = 0
    private var wides = 0
    private var noBalls = 0
    private var byes = 0
    private var legByes = 0
    private val batters = LinkedHashMap<Int, BatterAcc>()
    private val bowlers = LinkedHashMap<Int, BowlerAcc>()
    private val fallOfWickets = mutableListOf<FallOfWicket>()
    private val partnerships = mutableListOf<PartnershipAcc>()
    private var partnership: PartnershipAcc? = null
    private val overs = mutableListOf<Pair<Int, MutableList<Ball>>>()
    private var overOpen = false
    private var bowlerRunsThisOver = 0
    private var striker: Int? = null
    private var nonStriker: Int? = null
    private var bowler: Int? = null
    private var freeHit = false

    private fun validPlayer(p: Int) = p in 0 until config.playersPerSide

    private fun available(): List<Int> = (0 until config.playersPerSide).filter { p ->
        val b = batters[p]
        p != striker && p != nonStriker && (b == null || (b.retired && b.dismissal == null))
    }

    fun apply(event: MatchEvent, target: Int?) {
        if (event is StartInnings) {
            if (started) return
            with(event) {
                if (!validPlayer(striker) || !validPlayer(nonStriker) || !validPlayer(bowler) || striker == nonStriker) return
                started = true
                this@InningsBuilder.striker = striker
                this@InningsBuilder.nonStriker = nonStriker
                this@InningsBuilder.bowler = bowler
                batters.getOrPut(striker) { BatterAcc(striker) }
                batters.getOrPut(nonStriker) { BatterAcc(nonStriker) }
            }
            return
        }
        if (!started || complete) return
        when (event) {
            is Delivery -> deliver(event, target)
            is NewBatter -> newBatter(event.player)
            is NewBowler -> if (bowler == null && validPlayer(event.player)) bowler = event.player
            is Retire -> retire(event.batter)
            SwapStrike -> if (striker != null && nonStriker != null) swap()
            EndInnings -> complete = true
            is StartInnings -> Unit
        }
    }

    private fun swap() {
        val s = striker
        striker = nonStriker
        nonStriker = s
    }

    private fun newBatter(player: Int) {
        if (player !in available()) return
        when {
            striker == null -> striker = player
            nonStriker == null -> nonStriker = player
            else -> return
        }
        batters.getOrPut(player) { BatterAcc(player) }.retired = false
        partnership = null
    }

    private fun retire(slot: Slot) {
        val player = (if (slot == Slot.STRIKER) striker else nonStriker) ?: return
        batters.getValue(player).retired = true
        if (slot == Slot.STRIKER) striker = null else nonStriker = null
        partnership = null
    }

    private fun deliver(d: Delivery, target: Int?) {
        val s = striker ?: return
        val bowlerId = bowler ?: return
        // Under last-man-standing the lone batter has no partner to swap ends with.
        val alone = nonStriker == null
        if (alone && available().isNotEmpty()) return // a new batter is still to come in
        val r = d.runs.coerceAtLeast(0)
        val freeHitBall = freeHit
        val bat = batters.getValue(s)
        val bowl = bowlers.getOrPut(bowlerId) { BowlerAcc(bowlerId) }
        if (!overOpen) {
            overs += bowlerId to mutableListOf()
            overOpen = true
            bowlerRunsThisOver = 0
        }

        val legal: Boolean
        val total: Int
        val againstBowler: Int
        when (d.kind) {
            DeliveryKind.NORMAL -> {
                legal = true; total = r; againstBowler = r
                bat.score(r)
            }
            DeliveryKind.WIDE -> {
                legal = !rules.wideReball; total = rules.wideRuns + r; againstBowler = total
                wides += total
                bowl.wides++
            }
            DeliveryKind.NO_BALL -> {
                legal = !rules.noBallReball; total = rules.noBallRuns + r; againstBowler = total
                noBalls += rules.noBallRuns
                bowl.noBalls++
                bat.score(r)
            }
            DeliveryKind.BYE, DeliveryKind.LEG_BYE -> {
                legal = true; total = r; againstBowler = 0
                if (d.kind == DeliveryKind.BYE) byes += r else legByes += r
                bat.balls++
            }
        }
        runs += total
        bowl.runs += againstBowler
        bowlerRunsThisOver += againstBowler
        if (legal) {
            legalBalls++
            bowl.balls++
        }
        val p = partnership ?: PartnershipAcc(listOfNotNull(striker, nonStriker)).also {
            partnership = it
            partnerships += it
        }
        p.runs += total
        if (legal) p.balls++

        val wicket = d.wicket?.takeIf { canDismiss(it.type, d.kind, freeHitBall) }
        overs.last().second += Ball(d.kind, r, total, wicket?.type)

        if (wicket == null || wicket.type != WicketType.RUN_OUT) {
            if (!alone && r % 2 == 1) swap()
        }
        if (wicket != null) dismiss(wicket, s, bowlerId, alone)

        freeHit = when (d.kind) {
            DeliveryKind.NO_BALL -> rules.freeHit
            DeliveryKind.WIDE -> freeHitBall // a free hit carries over a wide
            else -> false
        }

        if (legal && legalBalls % BALLS_PER_OVER == 0) {
            if (bowlerRunsThisOver == 0) bowl.maidens++
            if (!alone) swap()
            bowler = null
            overOpen = false
        }

        if (legalBalls >= config.maxBalls) complete = true
        if (target != null && runs >= target) complete = true
        if (!complete && (striker == null || nonStriker == null) && available().isEmpty()) {
            val survivor = striker ?: nonStriker
            if (rules.lastManStanding && survivor != null) {
                striker = survivor
                nonStriker = null
            } else {
                complete = true
            }
        }
    }

    private fun BatterAcc.score(r: Int) {
        runs += r
        balls++
        if (r == 4) fours++
        if (r == 6) sixes++
    }

    private fun canDismiss(type: WicketType, kind: DeliveryKind, freeHitBall: Boolean): Boolean = when {
        type == WicketType.RUN_OUT -> true
        freeHitBall || kind == DeliveryKind.NO_BALL -> false
        kind == DeliveryKind.WIDE -> type == WicketType.STUMPED || type == WicketType.HIT_WICKET
        else -> true
    }

    private fun dismiss(w: Wicket, s: Int, bowlerId: Int, alone: Boolean) {
        val ns = nonStriker
        val out: Int
        if (w.type == WicketType.RUN_OUT && !alone) {
            out = if (w.batter == Slot.STRIKER) s else ns!!
            val survivor = if (out == s) ns else s
            // The new batter takes guard at the end where the wicket was broken.
            if (w.end == Slot.STRIKER) {
                striker = null; nonStriker = survivor
            } else {
                striker = survivor; nonStriker = null
            }
        } else {
            out = s
            if (striker == s) striker = null else nonStriker = null
        }
        wickets++
        val credited = if (w.type.creditsBowler) bowlerId else null
        batters.getValue(out).dismissal = Dismissal(w.type, credited)
        if (credited != null) bowlers.getValue(bowlerId).wickets++
        fallOfWickets += FallOfWicket(wickets, runs, legalBalls, out)
        partnership = null
    }

    fun build() = Innings(
        index = index,
        battingTeam = battingTeam,
        started = started,
        complete = complete,
        runs = runs,
        wickets = wickets,
        legalBalls = legalBalls,
        extras = Extras(wides, noBalls, byes, legByes),
        batters = batters.values.map { it.line() },
        bowlers = bowlers.values.map { it.line() },
        fallOfWickets = fallOfWickets.toList(),
        partnerships = partnerships.map { Partnership(it.batters, it.runs, it.balls) },
        overs = overs.map { (b, balls) -> Over(b, balls.toList()) },
        striker = striker,
        nonStriker = nonStriker,
        bowler = bowler,
        freeHit = freeHit,
        available = available(),
    )
}
