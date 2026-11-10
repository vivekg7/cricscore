package com.crylo.cricscore.ui

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalResources
import com.crylo.cricscore.R
import com.crylo.cricscore.engine.Ball
import com.crylo.cricscore.engine.BatterLine
import com.crylo.cricscore.engine.DeliveryKind
import com.crylo.cricscore.engine.Innings
import com.crylo.cricscore.engine.MatchConfig
import com.crylo.cricscore.engine.MatchResult
import com.crylo.cricscore.engine.MatchState
import com.crylo.cricscore.engine.WicketType
import com.crylo.cricscore.engine.oversText
import java.util.Locale

/**
 * Text for everything the engine reports as data. Plain functions over [Resources] so
 * the share-as-text builder and the composables word things identically.
 */

fun Resources.playerName(config: MatchConfig, team: Int, player: Int): String =
    config.players[team][player].ifBlank { getString(R.string.player_n, player + 1) }

@Composable @ReadOnlyComposable
fun res(): Resources = LocalResources.current

fun decimal(value: Double): String = String.format(Locale.getDefault(), "%.2f", value)

fun oneDecimal(value: Double): String = String.format(Locale.getDefault(), "%.1f", value)

fun Resources.score(inn: Innings): String = getString(R.string.score_runs_wickets, inn.runs, inn.wickets)

fun Resources.scoreWithOvers(config: MatchConfig, inn: Innings): String =
    getString(R.string.score_with_overs, score(inn), oversText(inn.legalBalls), config.overs)

fun Resources.ballLabel(ball: Ball): String {
    val base = when (ball.kind) {
        DeliveryKind.NORMAL -> if (ball.wicket != null && ball.runs == 0) "" else ball.runs.toString()
        DeliveryKind.WIDE -> getString(R.string.ball_wide) + plus(ball.runs)
        DeliveryKind.NO_BALL -> getString(R.string.ball_no_ball) + plus(ball.runs)
        DeliveryKind.BYE -> getString(R.string.ball_bye, ball.runs)
        DeliveryKind.LEG_BYE -> getString(R.string.ball_leg_bye, ball.runs)
    }
    if (ball.wicket == null) return base
    val w = getString(R.string.ball_wicket)
    return if (base.isEmpty()) w else "$base+$w"
}

private fun plus(runs: Int) = if (runs > 0) "+$runs" else ""

fun Resources.dismissalText(config: MatchConfig, inn: Innings, b: BatterLine): String {
    val d = b.dismissal
    if (d == null) {
        return getString(
            when {
                b.retired -> R.string.dismissal_retired
                else -> R.string.dismissal_not_out
            },
        )
    }
    val bowler = d.bowler?.let { playerName(config, config.fieldingTeam(inn.index), it) } ?: ""
    return when (d.type) {
        WicketType.BOWLED -> getString(R.string.dismissal_bowled, bowler)
        WicketType.CAUGHT -> getString(R.string.dismissal_caught, bowler)
        WicketType.LBW -> getString(R.string.dismissal_lbw, bowler)
        WicketType.STUMPED -> getString(R.string.dismissal_stumped, bowler)
        WicketType.HIT_WICKET -> getString(R.string.dismissal_hit_wicket, bowler)
        WicketType.RUN_OUT -> getString(R.string.dismissal_run_out)
    }
}

fun Resources.wicketTypeName(type: WicketType): String = getString(
    when (type) {
        WicketType.BOWLED -> R.string.wicket_bowled
        WicketType.CAUGHT -> R.string.wicket_caught
        WicketType.LBW -> R.string.wicket_lbw
        WicketType.RUN_OUT -> R.string.wicket_run_out
        WicketType.STUMPED -> R.string.wicket_stumped
        WicketType.HIT_WICKET -> R.string.wicket_hit_wicket
    },
)

fun Resources.resultText(state: MatchState): String? {
    val c = state.config
    return when (val r = state.result) {
        null -> null
        MatchResult.Tie -> getString(R.string.result_tie)
        is MatchResult.WonByRuns -> getQuantityString(R.plurals.result_won_by_runs, r.runs, c.teams[r.team], r.runs)
        is MatchResult.WonByWickets -> getQuantityString(R.plurals.result_won_by_wickets, r.wickets, c.teams[r.team], r.wickets)
    }
}

/** One line for lists: the result, or where the match stands. */
fun Resources.statusText(state: MatchState): String {
    resultText(state)?.let { return it }
    val c = state.config
    val cur = state.current
    val needed = state.runsNeeded
    return when {
        !cur.started && cur.index == 0 -> getString(R.string.status_not_started)
        needed != null && cur.started -> getQuantityString(R.plurals.status_need, needed, c.teams[cur.battingTeam], needed, state.ballsLeft)
        cur.complete || !cur.started -> getString(R.string.status_innings_break, state.target ?: 0)
        else -> getString(R.string.status_batting, c.teams[cur.battingTeam])
    }
}
