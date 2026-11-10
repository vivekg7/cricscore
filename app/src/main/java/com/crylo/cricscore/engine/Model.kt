package com.crylo.cricscore.engine

/**
 * The match setup. Nothing here changes once a ball is bowled, except player names,
 * which are display-only: the engine refers to players by their index in the team.
 */
data class MatchConfig(
    /** Team names. Index 0 bats first. */
    val teams: List<String>,
    val overs: Int,
    val playersPerSide: Int,
    /** Per team, exactly [playersPerSide] names. A blank name means "Player n". */
    val players: List<List<String>> = List(2) { List(playersPerSide) { "" } },
    val rules: Rules = Rules(),
) {
    init {
        require(teams.size == 2 && players.size == 2)
        require(overs >= 1 && playersPerSide >= 2)
        require(players.all { it.size == playersPerSide })
    }

    val maxBalls: Int get() = overs * BALLS_PER_OVER

    /** Wickets a side can lose before it is all out. */
    val maxWickets: Int get() = playersPerSide - if (rules.lastManStanding) 0 else 1

    fun battingTeam(innings: Int) = innings % 2
    fun fieldingTeam(innings: Int) = 1 - innings % 2

    companion object {
        const val BALLS_PER_OVER = 6
    }
}

/** The house rules that differ from ground to ground. */
data class Rules(
    /** Penalty runs for a wide (0 is common in gully cricket). */
    val wideRuns: Int = 1,
    /** Whether a wide has to be bowled again (does not count as a ball of the over). */
    val wideReball: Boolean = true,
    /** Penalty runs for a no-ball, added on top of anything hit off it. */
    val noBallRuns: Int = 1,
    val noBallReball: Boolean = true,
    /** The delivery after a no-ball is a free hit: only a run out can dismiss the batter. */
    val freeHit: Boolean = true,
    /** The last batter may carry on alone once their partner is out. */
    val lastManStanding: Boolean = false,
    /** Show the "retire batter" action. */
    val allowRetire: Boolean = true,
)

/** A batter position, also used for the two ends of the pitch (striker's end, bowler's end). */
enum class Slot { STRIKER, NON_STRIKER }

enum class DeliveryKind { NORMAL, WIDE, NO_BALL, BYE, LEG_BYE }

enum class WicketType {
    BOWLED, CAUGHT, LBW, RUN_OUT, STUMPED, HIT_WICKET;

    /** Every dismissal except a run out goes down against the bowler. */
    val creditsBowler: Boolean get() = this != RUN_OUT
}

/**
 * @property batter which batter is out, as they stood when the ball was bowled.
 * @property end for a run out, the end at which the wicket was broken. That end is
 *   where the incoming batter takes guard, so it decides who faces next.
 */
data class Wicket(
    val type: WicketType,
    val batter: Slot = Slot.STRIKER,
    val end: Slot = batter,
)

/**
 * The match is nothing but a list of these. Every number on screen is derived by
 * replaying the list from the start, which is what makes undo a matter of dropping
 * events off the end.
 */
sealed interface MatchEvent {
    data class StartInnings(val striker: Int, val nonStriker: Int, val bowler: Int) : MatchEvent

    /**
     * @property runs runs off the bat for [DeliveryKind.NORMAL] and [DeliveryKind.NO_BALL];
     *   runs actually run (or a boundary) for byes, leg byes and wides. Penalty runs for
     *   wides and no-balls come from [Rules] and are never included here.
     */
    data class Delivery(
        val kind: DeliveryKind,
        val runs: Int = 0,
        val wicket: Wicket? = null,
    ) : MatchEvent

    data class NewBatter(val player: Int) : MatchEvent
    data class NewBowler(val player: Int) : MatchEvent

    /** Batter retires not out. They may come back in later. */
    data class Retire(val batter: Slot) : MatchEvent

    /** Fixes the strike by hand when the scorer got it wrong. */
    data object SwapStrike : MatchEvent

    /** Closes the current innings early (declaration, bad light, mum calling). */
    data object EndInnings : MatchEvent
}
