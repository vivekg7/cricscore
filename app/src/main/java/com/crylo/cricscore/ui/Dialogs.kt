package com.crylo.cricscore.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.crylo.cricscore.R
import com.crylo.cricscore.engine.DeliveryKind
import com.crylo.cricscore.engine.MatchEvent
import com.crylo.cricscore.engine.MatchState
import com.crylo.cricscore.engine.Slot
import com.crylo.cricscore.engine.Wicket
import com.crylo.cricscore.engine.WicketType

/** One tap: pick how many runs came with the extra. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExtraDialog(kind: DeliveryKind, onDismiss: () -> Unit, onRuns: (Int) -> Unit) {
    val (title, hint, range) = when (kind) {
        DeliveryKind.WIDE -> Triple(R.string.extra_wide_title, R.string.extra_wide_hint, 0..4)
        DeliveryKind.NO_BALL -> Triple(R.string.extra_no_ball_title, R.string.extra_no_ball_hint, 0..6)
        DeliveryKind.BYE -> Triple(R.string.extra_bye_title, R.string.extra_runs_hint, 1..4)
        else -> Triple(R.string.extra_leg_bye_title, R.string.extra_runs_hint, 1..4)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(hint))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    range.forEach { n ->
                        FilledTonalButton(onClick = { onRuns(n) }, modifier = Modifier.size(64.dp)) {
                            Text(if (n == 0) "0" else "+$n", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
fun OutDialog(freeHit: Boolean, onDismiss: () -> Unit, onType: (WicketType) -> Unit) {
    val r = res()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.out_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (freeHit) Text(stringResource(R.string.out_free_hit_note), color = MaterialTheme.colorScheme.tertiary)
                WicketType.entries.forEach { t ->
                    FilledTonalButton(
                        onClick = { onType(t) },
                        enabled = !freeHit || t == WicketType.RUN_OUT,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(r.wicketTypeName(t)) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/**
 * A run out needs three facts: who is out, how many runs were completed first, and at
 * which end the wicket was broken (that decides who faces next). The end defaults to
 * the one the out batter was running towards, which is right most of the time.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RunOutDialog(state: MatchState, onDismiss: () -> Unit, onConfirm: (MatchEvent) -> Unit) {
    val r = res()
    val c = state.config
    val inn = state.current
    val team = c.battingTeam(inn.index)
    val alone = inn.nonStriker == null
    var who by remember { mutableStateOf(Slot.STRIKER) }
    var runs by remember { mutableStateOf(0) }
    var end by remember { mutableStateOf<Slot?>(null) }
    var kind by remember { mutableStateOf(DeliveryKind.NORMAL) }
    val towards = if ((who == Slot.STRIKER) == (runs % 2 == 0)) Slot.NON_STRIKER else Slot.STRIKER
    val chosenEnd = end ?: towards

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.wicket_run_out)) },
        text = {
            Column(
                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (!alone) {
                    Label(R.string.run_out_who)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(Slot.STRIKER to inn.striker, Slot.NON_STRIKER to inn.nonStriker).forEach { (slot, p) ->
                            if (p != null) {
                                FilterChip(selected = who == slot, onClick = { who = slot }, label = { Text(r.playerName(c, team, p)) })
                            }
                        }
                    }
                }
                Label(R.string.run_out_runs)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (0..3).forEach { n -> FilterChip(selected = runs == n, onClick = { runs = n }, label = { Text(n.toString()) }) }
                }
                if (!alone) {
                    Label(R.string.run_out_end)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = chosenEnd == Slot.STRIKER, onClick = { end = Slot.STRIKER }, label = { Text(stringResource(R.string.end_striker)) })
                        FilterChip(selected = chosenEnd == Slot.NON_STRIKER, onClick = { end = Slot.NON_STRIKER }, label = { Text(stringResource(R.string.end_bowler)) })
                    }
                }
                Label(R.string.run_out_delivery)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        DeliveryKind.NORMAL to R.string.delivery_legal,
                        DeliveryKind.WIDE to R.string.ball_wide,
                        DeliveryKind.NO_BALL to R.string.ball_no_ball,
                        DeliveryKind.BYE to R.string.key_bye,
                        DeliveryKind.LEG_BYE to R.string.key_leg_bye,
                    ).forEach { (k, label) ->
                        FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(stringResource(label)) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val batter = if (alone) Slot.STRIKER else who
                onConfirm(MatchEvent.Delivery(kind, runs, Wicket(WicketType.RUN_OUT, batter, if (alone) Slot.STRIKER else chosenEnd)))
            }) { Text(stringResource(R.string.action_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun Label(text: Int) = Text(stringResource(text), style = MaterialTheme.typography.labelLarge)

@Composable
fun RenameDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename_title)) },
        text = { NameField(name, stringResource(R.string.rename_label)) { name = it } },
        confirmButton = { TextButton(onClick = { onSave(name) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
fun PlayersDialog(state: MatchState, onDismiss: () -> Unit, onSave: (List<List<String>>) -> Unit) {
    var players by remember { mutableStateOf(state.config.players) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.menu_edit_players)) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                PlayerNameFields(state.config.teams, players) { team, i, v ->
                    players = players.mapIndexed { t, list -> if (t == team) list.mapIndexed { j, n -> if (j == i) v else n } else list }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(players) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
