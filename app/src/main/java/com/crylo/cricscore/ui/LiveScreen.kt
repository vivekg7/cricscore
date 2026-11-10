package com.crylo.cricscore.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.crylo.cricscore.R
import com.crylo.cricscore.engine.DeliveryKind
import com.crylo.cricscore.engine.MatchEvent
import com.crylo.cricscore.engine.MatchState
import com.crylo.cricscore.engine.Pending
import com.crylo.cricscore.engine.Slot
import com.crylo.cricscore.engine.Wicket
import com.crylo.cricscore.engine.WicketType
import com.crylo.cricscore.engine.oversText
import com.crylo.cricscore.engine.runRate

private sealed interface LiveDialog {
    data class Extra(val kind: DeliveryKind) : LiveDialog
    data object Out : LiveDialog
    data object RunOut : LiveDialog
    data class Rename(val team: Int, val player: Int) : LiveDialog
    data object Players : LiveDialog
    data object EndInnings : LiveDialog
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveScreen(
    match: MatchItem,
    onBack: () -> Unit,
    onRecord: (MatchEvent) -> Unit,
    onUndo: () -> Unit,
    onScorecard: () -> Unit,
    onRename: (team: Int, player: Int, name: String) -> Unit,
    onPlayers: (List<List<String>>) -> Unit,
    onNewMatch: () -> Unit,
) {
    val state = match.state
    val c = match.config
    val inn = state.current
    var dialog by remember { mutableStateOf<LiveDialog?>(null) }
    var menu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.live_title, c.teams[0], c.teams[1]), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_back), stringResource(R.string.action_back)) }
                },
                actions = {
                    TextButton(onClick = onScorecard) { Text(stringResource(R.string.action_scorecard)) }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(painterResource(R.drawable.ic_more), stringResource(R.string.action_more)) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            val item: @Composable (Int, Boolean, () -> Unit) -> Unit = { text, enabled, action ->
                                DropdownMenuItem(text = { Text(stringResource(text)) }, enabled = enabled, onClick = { menu = false; action() })
                            }
                            item(R.string.menu_edit_players, true) { dialog = LiveDialog.Players }
                            item(R.string.menu_swap_strike, state.canScore && inn.nonStriker != null) { onRecord(MatchEvent.SwapStrike) }
                            if (c.rules.allowRetire) {
                                item(R.string.menu_retire_striker, state.canScore) { onRecord(MatchEvent.Retire(Slot.STRIKER)) }
                                item(R.string.menu_retire_non_striker, state.canScore && inn.nonStriker != null) {
                                    onRecord(MatchEvent.Retire(Slot.NON_STRIKER))
                                }
                            }
                            item(R.string.menu_end_innings, state.canScore) { dialog = LiveDialog.EndInnings }
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ScoreHeader(state)
                if (inn.started) {
                    ThisOver(state)
                    CreaseCard(state) { team, p -> dialog = LiveDialog.Rename(team, p) }
                }
            }
            Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.navigationBarsPadding().padding(12.dp)) {
                    val pending = state.pending
                    when {
                        state.isOver -> MatchOverPanel(state, onScorecard, onNewMatch, onUndo)
                        pending is Pending.StartInnings -> StartInningsPanel(state, pending.innings, onRecord, onUndo)
                        pending is Pending.NewBatter -> NewBatterPanel(state, pending, onRecord, onUndo)
                        pending is Pending.NewBowler -> NewBowlerPanel(state, pending, onRecord, onUndo)
                        else -> Keypad(
                            onRuns = { onRecord(MatchEvent.Delivery(DeliveryKind.NORMAL, it)) },
                            onExtra = { dialog = LiveDialog.Extra(it) },
                            onOut = { dialog = LiveDialog.Out },
                            onUndo = onUndo,
                        )
                    }
                }
            }
        }
    }

    when (val d = dialog) {
        null -> Unit
        is LiveDialog.Extra -> ExtraDialog(d.kind, { dialog = null }) { runs ->
            dialog = null
            onRecord(MatchEvent.Delivery(d.kind, runs))
        }
        LiveDialog.Out -> OutDialog(freeHit = inn.freeHit, onDismiss = { dialog = null }) { type ->
            if (type == WicketType.RUN_OUT) {
                dialog = LiveDialog.RunOut
            } else {
                dialog = null
                onRecord(MatchEvent.Delivery(DeliveryKind.NORMAL, 0, Wicket(type)))
            }
        }
        LiveDialog.RunOut -> RunOutDialog(state, { dialog = null }) {
            dialog = null
            onRecord(it)
        }
        is LiveDialog.Rename -> RenameDialog(res().playerName(c, d.team, d.player), { dialog = null }) {
            dialog = null
            onRename(d.team, d.player, it)
        }
        LiveDialog.Players -> PlayersDialog(state, { dialog = null }) {
            dialog = null
            onPlayers(it)
        }
        LiveDialog.EndInnings -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.end_innings_title)) },
            text = { Text(stringResource(R.string.end_innings_text)) },
            confirmButton = {
                TextButton(onClick = { dialog = null; onRecord(MatchEvent.EndInnings) }) { Text(stringResource(R.string.menu_end_innings)) }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun ScoreHeader(state: MatchState) {
    val r = res()
    val c = state.config
    val inn = state.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.teams[inn.battingTeam], style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (inn.freeHit && state.canScore) {
                    Surface(color = MaterialTheme.colorScheme.tertiary, shape = MaterialTheme.shapes.small) {
                        Text(
                            stringResource(R.string.free_hit),
                            color = MaterialTheme.colorScheme.onTertiary,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(r.score(inn), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.overs_of, oversText(inn.legalBalls), c.overs),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 12.dp, bottom = 8.dp),
                )
            }
            val parts = buildList {
                add(stringResource(R.string.crr, decimal(runRate(inn.runs, inn.legalBalls))))
                state.target?.let { add(stringResource(R.string.target, it)) }
                state.requiredRate?.let { add(stringResource(R.string.rrr, decimal(it))) }
            }
            Text(parts.joinToString("   "), style = MaterialTheme.typography.bodyMedium)
            if (!state.isOver) {
                Text(r.statusText(state), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
            if (inn.index == 1) {
                val first = state.innings[0]
                Text(
                    stringResource(R.string.first_innings_line, c.teams[first.battingTeam], r.scoreWithOvers(c, first)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThisOver(state: MatchState) {
    val r = res()
    val over = state.current.recentOver ?: return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(R.string.this_over, r.playerName(state.config, state.config.fieldingTeam(state.current.index), over.bowler), over.runs),
            style = MaterialTheme.typography.labelLarge,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            over.balls.forEach { ball ->
                val (bg, fg) = when {
                    ball.wicket != null -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.onError
                    ball.kind == DeliveryKind.NORMAL && ball.runs >= 4 -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
                    ball.kind != DeliveryKind.NORMAL -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
                }
                Box(
                    Modifier.height(36.dp).background(bg, CircleShape).padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(r.ballLabel(ball), color = fg, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 2.dp))
                }
            }
        }
    }
}

@Composable
private fun CreaseCard(state: MatchState, onRename: (team: Int, player: Int) -> Unit) {
    val r = res()
    val c = state.config
    val inn = state.current
    val bat = c.battingTeam(inn.index)
    val field = c.fieldingTeam(inn.index)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 8.dp)) {
            TableRow(stringResource(R.string.col_batter), battingHeader(), header = true)
            listOf(inn.striker to true, inn.nonStriker to false).forEach { (p, onStrike) ->
                if (p != null) {
                    val b = inn.batters.first { it.player == p }
                    val name = r.playerName(c, bat, p) + if (onStrike) " *" else ""
                    TableRow(
                        name,
                        listOf(b.runs.toString(), b.balls.toString(), b.fours.toString(), b.sixes.toString(), oneDecimal(b.strikeRate)),
                        bold = onStrike,
                        onClick = { onRename(bat, p) },
                    )
                }
            }
            inn.currentPartnership?.let {
                Text(
                    stringResource(R.string.partnership, it.runs, it.balls),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            TableRow(stringResource(R.string.col_bowler), bowlingHeader(), header = true)
            val bowlerId = inn.bowler ?: inn.recentOver?.bowler
            bowlerId?.let { p ->
                val b = inn.bowlers.firstOrNull { it.player == p }
                TableRow(
                    r.playerName(c, field, p),
                    if (b == null) listOf("0.0", "0", "0", "0", "-") else listOf(oversText(b.balls), b.maidens.toString(), b.runs.toString(), b.wickets.toString(), decimal(b.economy)),
                    onClick = { onRename(field, p) },
                )
            }
        }
    }
}

/** A name column and fixed-width number columns, shared by the live card and the scorecard. */
@Composable
fun TableRow(
    name: String,
    cells: List<String>,
    header: Boolean = false,
    bold: Boolean = false,
    sub: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val style = if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium
    val color = if (header) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified
    val weight = if (bold) FontWeight.Bold else null
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = if (header) 4.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, style = style, color = color, fontWeight = weight, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub != null) {
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
        }
        cells.forEachIndexed { i, cell ->
            Text(
                cell,
                style = style,
                color = color,
                fontWeight = weight,
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                modifier = Modifier.width(if (i == cells.lastIndex) 52.dp else 36.dp),
            )
        }
    }
}

@Composable
private fun Keypad(onRuns: (Int) -> Unit, onExtra: (DeliveryKind) -> Unit, onOut: () -> Unit, onUndo: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        KeyRow {
            (0..3).forEach { n -> Key(n.toString(), colors.secondaryContainer, colors.onSecondaryContainer) { onRuns(n) } }
        }
        KeyRow {
            Key("4", colors.primary, colors.onPrimary) { onRuns(4) }
            Key("5", colors.secondaryContainer, colors.onSecondaryContainer) { onRuns(5) }
            Key("6", colors.primary, colors.onPrimary) { onRuns(6) }
            Key(stringResource(R.string.key_out), colors.error, colors.onError) { onOut() }
        }
        KeyRow {
            Key(stringResource(R.string.ball_wide), colors.tertiaryContainer, colors.onTertiaryContainer) { onExtra(DeliveryKind.WIDE) }
            Key(stringResource(R.string.ball_no_ball), colors.tertiaryContainer, colors.onTertiaryContainer) { onExtra(DeliveryKind.NO_BALL) }
            Key(stringResource(R.string.key_bye), colors.tertiaryContainer, colors.onTertiaryContainer) { onExtra(DeliveryKind.BYE) }
            Key(stringResource(R.string.key_leg_bye), colors.tertiaryContainer, colors.onTertiaryContainer) { onExtra(DeliveryKind.LEG_BYE) }
        }
        OutlinedButton(onClick = onUndo, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Icon(painterResource(R.drawable.ic_undo), null, Modifier.size(18.dp))
            Text(stringResource(R.string.action_undo), Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun KeyRow(content: @Composable RowScope.() -> Unit) =
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)

@Composable
private fun RowScope.Key(label: String, bg: Color, fg: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.weight(1f).height(64.dp),
        shape = MaterialTheme.shapes.large,
        colors = ButtonDefaults.buttonColors(containerColor = bg, contentColor = fg),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
    ) { Text(label, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayerPicker(label: String, team: Int, state: MatchState, options: List<Int>, selected: Int?, note: (Int) -> String? = { null }, onSelect: (Int) -> Unit) {
    val r = res()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { p ->
                val text = r.playerName(state.config, team, p) + (note(p)?.let { " ($it)" } ?: "")
                FilterChip(selected = selected == p, onClick = { onSelect(p) }, label = { Text(text) })
            }
        }
    }
}

@Composable
private fun PanelActions(confirm: String, enabled: Boolean, onConfirm: () -> Unit, onUndo: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (onUndo != null) {
            OutlinedButton(onClick = onUndo, modifier = Modifier.weight(1f).height(52.dp)) { Text(stringResource(R.string.action_undo)) }
        }
        Button(onClick = onConfirm, enabled = enabled, modifier = Modifier.weight(2f).height(52.dp)) { Text(confirm) }
    }
}

@Composable
private fun StartInningsPanel(state: MatchState, innings: Int, onRecord: (MatchEvent) -> Unit, onUndo: () -> Unit) {
    val c = state.config
    val bat = c.battingTeam(innings)
    val field = c.fieldingTeam(innings)
    var striker by rememberSaveable(innings) { mutableStateOf(0) }
    var nonStriker by rememberSaveable(innings) { mutableStateOf(1) }
    var bowler by rememberSaveable(innings) { mutableStateOf(0) }
    val all = (0 until c.playersPerSide).toList()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            stringResource(if (innings == 0) R.string.start_first_innings else R.string.start_second_innings, c.teams[bat], state.target ?: 0),
            style = MaterialTheme.typography.titleMedium,
        )
        Column(Modifier.verticalScrollLimited(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PlayerPicker(stringResource(R.string.pick_striker), bat, state, all, striker) { striker = it; if (nonStriker == it) nonStriker = all.first { p -> p != it } }
            PlayerPicker(stringResource(R.string.pick_non_striker), bat, state, all.filter { it != striker }, nonStriker) { nonStriker = it }
            PlayerPicker(stringResource(R.string.pick_bowler), field, state, all, bowler) { bowler = it }
        }
        PanelActions(
            stringResource(R.string.action_start),
            striker != nonStriker,
            { onRecord(MatchEvent.StartInnings(striker, nonStriker, bowler)) },
            if (innings > 0) onUndo else null,
        )
    }
}

@Composable
private fun NewBatterPanel(state: MatchState, pending: Pending.NewBatter, onRecord: (MatchEvent) -> Unit, onUndo: () -> Unit) {
    val team = state.config.battingTeam(state.current.index)
    var pick by remember(pending) { mutableStateOf(pending.available.first()) }
    val retired = stringResource(R.string.dismissal_retired)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.verticalScrollLimited()) {
            PlayerPicker(stringResource(R.string.pick_new_batter), team, state, pending.available, pick, note = { p ->
                retired.takeIf { state.current.batters.any { it.player == p && it.retired } }
            }) { pick = it }
        }
        PanelActions(stringResource(R.string.action_confirm), true, { onRecord(MatchEvent.NewBatter(pick)) }, onUndo)
    }
}

@Composable
private fun NewBowlerPanel(state: MatchState, pending: Pending.NewBowler, onRecord: (MatchEvent) -> Unit, onUndo: () -> Unit) {
    val c = state.config
    val team = c.fieldingTeam(state.current.index)
    var pick by remember(pending) { mutableStateOf(pending.suggested) }
    val last = stringResource(R.string.last_over_note)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.over_complete, oversText(state.current.legalBalls)), style = MaterialTheme.typography.titleMedium)
        Column(Modifier.verticalScrollLimited()) {
            PlayerPicker(
                stringResource(R.string.pick_next_bowler),
                team,
                state,
                (0 until c.playersPerSide).toList(),
                pick,
                note = { p -> last.takeIf { p == pending.previous } },
            ) { pick = it }
        }
        PanelActions(stringResource(R.string.action_confirm), true, { onRecord(MatchEvent.NewBowler(pick)) }, onUndo)
    }
}

@Composable
private fun MatchOverPanel(state: MatchState, onScorecard: () -> Unit, onNewMatch: () -> Unit, onUndo: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(res().resultText(state).orEmpty(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onUndo, modifier = Modifier.weight(1f).height(52.dp)) { Text(stringResource(R.string.action_undo)) }
            FilledTonalButton(onClick = onScorecard, modifier = Modifier.weight(1f).height(52.dp)) { Text(stringResource(R.string.action_scorecard)) }
            Button(onClick = onNewMatch, modifier = Modifier.weight(1f).height(52.dp)) { Text(stringResource(R.string.action_new)) }
        }
    }
}

/** Keeps a long player list from pushing the action buttons off a small screen. */
@Composable
private fun Modifier.verticalScrollLimited(): Modifier = heightIn(max = 260.dp).verticalScroll(rememberScrollState())
