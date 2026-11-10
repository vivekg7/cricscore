package com.crylo.cricscore.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.crylo.cricscore.R
import com.crylo.cricscore.engine.Rules

private const val MAX_OVERS = 50
private const val MAX_PLAYERS = 20

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(draft: SetupDraft, onChange: ((SetupDraft) -> SetupDraft) -> Unit, onBack: () -> Unit, onStart: () -> Unit) {
    var showNames by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.setup_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_back), stringResource(R.string.action_back)) }
                },
            )
        },
        bottomBar = {
            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth().padding(16.dp).imePadding().height(56.dp),
            ) { Text(stringResource(R.string.setup_start), style = MaterialTheme.typography.titleMedium) }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NameField(draft.teamA, stringResource(R.string.setup_team_a)) { v -> onChange { it.copy(teamA = v) } }
            NameField(draft.teamB, stringResource(R.string.setup_team_b)) { v -> onChange { it.copy(teamB = v) } }

            Text(stringResource(R.string.setup_bats_first), style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(draft.teamA, draft.teamB).forEachIndexed { i, name ->
                    SegmentedButton(
                        selected = draft.battingFirst == i,
                        onClick = { onChange { it.copy(battingFirst = i) } },
                        shape = SegmentedButtonDefaults.itemShape(i, 2),
                    ) { Text(name.ifBlank { stringResource(if (i == 0) R.string.default_team_a else R.string.default_team_b) }, maxLines = 1) }
                }
            }

            Stepper(stringResource(R.string.setup_overs), draft.overs, 1..MAX_OVERS) { v -> onChange { it.copy(overs = v) } }
            Stepper(stringResource(R.string.setup_players), draft.playersPerSide, 2..MAX_PLAYERS) { v ->
                onChange { it.copy(playersPerSide = v, playersA = it.playersA.resized(v), playersB = it.playersB.resized(v)) }
            }

            TextButton(onClick = { showNames = !showNames }) {
                Text(stringResource(if (showNames) R.string.setup_hide_names else R.string.setup_edit_names))
            }
            if (showNames) {
                PlayerNameFields(
                    teams = listOf(draft.teamA, draft.teamB),
                    players = listOf(draft.playersA, draft.playersB),
                ) { team, i, v ->
                    onChange {
                        if (team == 0) it.copy(playersA = it.playersA.replaced(i, v)) else it.copy(playersB = it.playersB.replaced(i, v))
                    }
                }
            }

            HorizontalDivider()
            Text(stringResource(R.string.setup_rules), style = MaterialTheme.typography.titleMedium)
            RulesEditor(draft.rules) { r -> onChange { it.copy(rules = r) } }
            Spacer8()
        }
    }
}

@Composable
private fun Spacer8() = androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))

private fun List<String>.resized(n: Int) = List(n) { getOrElse(it) { "" } }
private fun List<String>.replaced(i: Int, v: String) = mapIndexed { j, s -> if (j == i) v else s }

@Composable
fun NameField(value: String, label: String, modifier: Modifier = Modifier, onValue: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValue(it.replace('\n', ' ')) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
        modifier = modifier.fillMaxWidth(),
    )
}

/** Name fields for both sides; blank fields fall back to "Player n". */
@Composable
fun PlayerNameFields(teams: List<String>, players: List<List<String>>, onValue: (team: Int, player: Int, name: String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (team in 0..1) {
            Text(teams[team], style = MaterialTheme.typography.labelLarge)
            players[team].forEachIndexed { i, name ->
                NameField(name, stringResource(R.string.player_n, i + 1)) { onValue(team, i, it) }
            }
        }
    }
}

@Composable
private fun Stepper(label: String, value: Int, range: IntRange, onValue: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        FilledTonalIconButton(onClick = { onValue(value - 1) }, enabled = value > range.first) {
            Icon(painterResource(R.drawable.ic_remove), stringResource(R.string.action_decrease))
        }
        Text(
            value.toString(),
            Modifier.width(48.dp),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        FilledTonalIconButton(onClick = { onValue(value + 1) }, enabled = value < range.last) {
            Icon(painterResource(R.drawable.ic_add), stringResource(R.string.action_increase))
        }
    }
}

@Composable
private fun RulesEditor(rules: Rules, onChange: (Rules) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        RunChoice(stringResource(R.string.rule_wide_runs), rules.wideRuns) { onChange(rules.copy(wideRuns = it)) }
        SwitchRow(stringResource(R.string.rule_wide_reball), rules.wideReball) { onChange(rules.copy(wideReball = it)) }
        RunChoice(stringResource(R.string.rule_no_ball_runs), rules.noBallRuns) { onChange(rules.copy(noBallRuns = it)) }
        SwitchRow(stringResource(R.string.rule_no_ball_reball), rules.noBallReball) { onChange(rules.copy(noBallReball = it)) }
        SwitchRow(stringResource(R.string.rule_free_hit), rules.freeHit) { onChange(rules.copy(freeHit = it)) }
        SwitchRow(stringResource(R.string.rule_last_man), rules.lastManStanding, stringResource(R.string.rule_last_man_hint)) {
            onChange(rules.copy(lastManStanding = it))
        }
        SwitchRow(stringResource(R.string.rule_retire), rules.allowRetire) { onChange(rules.copy(allowRetire = it)) }
    }
}

@Composable
private fun RunChoice(label: String, value: Int, onValue: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        SingleChoiceSegmentedButtonRow {
            (0..2).forEach { n ->
                SegmentedButton(selected = value == n, onClick = { onValue(n) }, shape = SegmentedButtonDefaults.itemShape(n, 3)) {
                    Text(n.toString())
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, hint: String? = null, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (hint != null) {
                Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
