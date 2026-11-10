package com.crylo.cricscore.ui

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.crylo.cricscore.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(history: List<MatchItem>?, onNewMatch: () -> Unit, onResume: (Long) -> Unit, onOpen: (Long) -> Unit) {
    val inProgress = history?.firstOrNull { !it.state.isOver }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (inProgress != null) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.home_in_progress), style = MaterialTheme.typography.labelLarge)
                            MatchSummary(inProgress)
                            Button(onClick = { onResume(inProgress.id) }, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.home_resume))
                            }
                        }
                    }
                }
            }
            item {
                val button: @Composable (Modifier) -> Unit = { m ->
                    if (inProgress == null) {
                        Button(onClick = onNewMatch, modifier = m) { Text(stringResource(R.string.home_new_match), style = MaterialTheme.typography.titleMedium) }
                    } else {
                        FilledTonalButton(onClick = onNewMatch, modifier = m) { Text(stringResource(R.string.home_new_match)) }
                    }
                }
                button(Modifier.fillMaxWidth().height(56.dp))
            }
            item {
                Text(
                    stringResource(R.string.home_history),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (history != null && history.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.home_history_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(history.orEmpty(), key = { it.id }) { m ->
                Card(Modifier.fillMaxWidth().clickable { onOpen(m.id) }) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        MatchSummary(m)
                        Text(
                            DateUtils.getRelativeTimeSpanString(m.updatedAt).toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MatchSummary(m: MatchItem) {
    val r = res()
    val c = m.config
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for (team in 0..1) {
            val inn = m.state.innings.getOrNull(team)?.takeIf { it.started }
            androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth()) {
                Text(c.teams[team], Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                Text(inn?.let { r.scoreWithOvers(c, it) } ?: stringResource(R.string.yet_to_bat_short))
            }
        }
        Text(r.statusText(m.state), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
    }
}
