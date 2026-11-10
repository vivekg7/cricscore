package com.crylo.cricscore.engine

import com.crylo.cricscore.engine.MatchConfig.Companion.BALLS_PER_OVER

data class Dismissal(val type: WicketType, val bowler: Int?)

data class BatterLine(
    val player: Int,
    val runs: Int,
    val balls: Int,
    val fours: Int,
    val sixes: Int,
    val dismissal: Dismissal?,
    val retired: Boolean,
) {
    val strikeRate: Double get() = if (balls == 0) 0.0 else runs * 100.0 / balls
}

data class BowlerLine(
    val player: Int,
    val balls: Int,
    val runs: Int,
    val wickets: Int,
    val maidens: Int,
    val wides: Int,
    val noBalls: Int,
) {
    val economy: Double get() = runRate(runs, balls)
}

data class Extras(val wides: Int, val noBalls: Int, val byes: Int, val legByes: Int) {
    val total: Int get() = wides + noBalls + byes + legByes
}

data class FallOfWicket(val wicket: Int, val score: Int, val balls: Int, val player: Int)

data class Partnership(val batters: List<Int>, val runs: Int, val balls: Int)

/** One delivery as it appears in the "this over" strip. */
data class Ball(
    val kind: DeliveryKind,
    /** Runs off the bat or run by the batters, excluding penalty runs. */
    val runs: Int,
    /** Everything the delivery added to the total. */
    val total: Int,
    val wicket: WicketType?,
)

data class Over(val bowler: Int, val balls: List<Ball>) {
    val runs: Int get() = balls.sumOf { it.total }
}

data class Innings(
    val index: Int,
    val battingTeam: Int,
    val started: Boolean,
    val complete: Boolean,
    val runs: Int,
    val wickets: Int,
    val legalBalls: Int,
    val extras: Extras,
    /** In batting order. */
    val batters: List<BatterLine>,
    /** In the order they first bowled. */
    val bowlers: List<BowlerLine>,
    val fallOfWickets: List<FallOfWicket>,
    val partnerships: List<Partnership>,
    val overs: List<Over>,
    val striker: Int?,
    val nonStriker: Int?,
    val bowler: Int?,
    /** The next delivery is a free hit. */
    val freeHit: Boolean,
    /** Players who can still come in: yet to bat, or retired and not at the crease. */
    val available: List<Int>,
) {
    val currentPartnership: Partnership? get() = partnerships.lastOrNull()?.takeIf { !complete }

    /** The current over, or the one just finished if no ball of the next has been bowled. */
    val recentOver: Over? get() = overs.lastOrNull()

    /** Batting alone under last-man-standing. */
    val batterAlone: Boolean get() = started && !complete && striker != null && nonStriker == null && available.isEmpty()
}

sealed interface Pending {
    /** Pick the openers and the opening bowler. */
    data class StartInnings(val innings: Int) : Pending
    data class NewBatter(val slot: Slot, val available: List<Int>) : Pending
    data class NewBowler(val suggested: Int, val previous: Int?) : Pending
}

sealed interface MatchResult {
    data class WonByRuns(val team: Int, val runs: Int) : MatchResult
    data class WonByWickets(val team: Int, val wickets: Int) : MatchResult
    data object Tie : MatchResult
}

data class MatchState(
    val config: MatchConfig,
    /** One entry per innings that exists so far; always at least one. */
    val innings: List<Innings>,
    val pending: Pending?,
    val result: MatchResult?,
) {
    val current: Innings get() = innings.last()

    /** Runs the side batting second needs to win, once the first innings is over. */
    val target: Int? get() = innings.first().takeIf { it.complete }?.let { it.runs + 1 }

    val isOver: Boolean get() = result != null

    /** Only while a chase is under way. */
    val runsNeeded: Int? get() = target?.takeIf { current.index == 1 }?.let { (it - current.runs).coerceAtLeast(0) }

    val ballsLeft: Int get() = (config.maxBalls - current.legalBalls).coerceAtLeast(0)

    val requiredRate: Double? get() = runsNeeded?.let { if (ballsLeft == 0) null else it * 6.0 / ballsLeft }

    val canScore: Boolean get() = pending == null && result == null
}

fun runRate(runs: Int, balls: Int): Double = if (balls == 0) 0.0 else runs * 6.0 / balls

/** Cricket notation: 13 balls is "2.1" overs. */
fun oversText(balls: Int): String = "${balls / BALLS_PER_OVER}.${balls % BALLS_PER_OVER}"
