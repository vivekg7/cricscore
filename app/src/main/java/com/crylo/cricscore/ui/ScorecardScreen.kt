package com.crylo.cricscore.ui

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.crylo.cricscore.R
import com.crylo.cricscore.engine.Innings
import com.crylo.cricscore.engine.MatchConfig
import com.crylo.cricscore.engine.MatchState
import com.crylo.cricscore.engine.oversText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScorecardScreen(match: MatchItem, canResume: Boolean, onBack: () -> Unit, onResume: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.action_scorecard)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_back), stringResource(R.string.action_back)) }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            val bitmap = layer.toImageBitmap().asAndroidBitmap()
                            shareImage(context, bitmap, match.id)
                        }
                    }) { Icon(painterResource(R.drawable.ic_share), stringResource(R.string.share_image)) }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(painterResource(R.drawable.ic_more), stringResource(R.string.action_more)) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.share_text)) }, onClick = {
                                menu = false
                                shareText(context, scorecardText(context.resources, match))
                            })
                            if (canResume) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.home_resume)) }, onClick = { menu = false; onResume() })
                            }
                            DropdownMenuItem(text = { Text(stringResource(R.string.action_delete)) }, onClick = { menu = false; confirmDelete = true })
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            // Recorded into a graphics layer so "share image" gets the whole card, including
            // the part scrolled off screen, without laying it out a second time.
            Box(
                Modifier
                    .drawWithContent {
                        layer.record { this@drawWithContent.drawContent() }
                        drawLayer(layer)
                    }
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                ScorecardContent(match)
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_title)) },
            text = { Text(stringResource(R.string.delete_text)) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text(stringResource(R.string.action_delete)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun ScorecardContent(match: MatchItem) {
    val r = res()
    val c = match.config
    val state = match.state
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.live_title, c.teams[0], c.teams[1]), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(r.statusText(state), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text(
            stringResource(R.string.match_meta, DateFormat.getMediumDateFormat(LocalContext.current).format(match.createdAt), c.overs, c.playersPerSide),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.innings.filter { it.started }.forEach { InningsCard(c, it) }
        Text(
            stringResource(R.string.share_footer),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun battingHeader() = listOf(R.string.col_runs, R.string.col_balls, R.string.col_fours, R.string.col_sixes, R.string.col_sr).map { stringResource(it) }

@Composable
fun bowlingHeader() = listOf(R.string.col_overs, R.string.col_maidens, R.string.col_runs, R.string.col_wickets, R.string.col_econ).map { stringResource(it) }

@Composable
private fun InningsCard(c: MatchConfig, inn: Innings) {
    val r = res()
    val bat = c.battingTeam(inn.index)
    val field = c.fieldingTeam(inn.index)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            TableRow(c.teams[bat], emptyList(), bold = true)
            Text(
                r.scoreWithOvers(c, inn),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            TableRow(stringResource(R.string.col_batter), battingHeader(), header = true)
            inn.batters.forEach { b ->
                TableRow(
                    r.playerName(c, bat, b.player),
                    listOf(b.runs.toString(), b.balls.toString(), b.fours.toString(), b.sixes.toString(), oneDecimal(b.strikeRate)),
                    sub = r.dismissalText(c, inn, b),
                )
            }
            val extras = inn.extras
            Text(
                stringResource(R.string.extras_line, extras.total, extras.wides, extras.noBalls, extras.byes, extras.legByes),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            val yet = inn.available.filter { p -> inn.batters.none { it.player == p } }
            if (yet.isNotEmpty()) {
                Text(
                    stringResource(R.string.yet_to_bat, yet.joinToString(", ") { r.playerName(c, bat, it) }),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            if (inn.fallOfWickets.isNotEmpty()) {
                Text(
                    stringResource(R.string.fall_of_wickets, fowText(r, c, inn)),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            TableRow(stringResource(R.string.col_bowler), bowlingHeader(), header = true)
            inn.bowlers.forEach { b ->
                val sub = listOfNotNull(
                    b.wides.takeIf { it > 0 }?.let { stringResource(R.string.bowler_wides, it) },
                    b.noBalls.takeIf { it > 0 }?.let { stringResource(R.string.bowler_no_balls, it) },
                ).joinToString(", ").ifEmpty { null }
                TableRow(
                    r.playerName(c, field, b.player),
                    listOf(oversText(b.balls), b.maidens.toString(), b.runs.toString(), b.wickets.toString(), decimal(b.economy)),
                    sub = sub,
                )
            }
            if (inn.partnerships.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                Text(stringResource(R.string.partnerships), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 16.dp))
                inn.partnerships.forEach { p ->
                    TableRow(
                        p.batters.joinToString(" & ") { r.playerName(c, bat, it) },
                        listOf(stringResource(R.string.runs_balls, p.runs, p.balls)),
                    )
                }
            }
        }
    }
}

private fun fowText(r: Resources, c: MatchConfig, inn: Innings): String {
    val bat = c.battingTeam(inn.index)
    return inn.fallOfWickets.joinToString(", ") {
        r.getString(R.string.fow_item, it.wicket, it.score, r.playerName(c, bat, it.player), oversText(it.balls))
    }
}

/** Plain-text scorecard for chat apps: fixed layout, no tables that wrap badly. */
fun scorecardText(r: Resources, match: MatchItem): String = buildString {
    val c = match.config
    val state: MatchState = match.state
    appendLine(r.getString(R.string.live_title, c.teams[0], c.teams[1]))
    appendLine(r.statusText(state))
    for (inn in state.innings.filter { it.started }) {
        val bat = c.battingTeam(inn.index)
        val field = c.fieldingTeam(inn.index)
        appendLine()
        appendLine("${c.teams[bat]}: ${r.scoreWithOvers(c, inn)}")
        inn.batters.forEach { b ->
            appendLine(r.getString(R.string.text_batter_line, r.playerName(c, bat, b.player), b.runs, b.balls, r.dismissalText(c, inn, b)))
        }
        val e = inn.extras
        appendLine(r.getString(R.string.extras_line, e.total, e.wides, e.noBalls, e.byes, e.legByes))
        if (inn.fallOfWickets.isNotEmpty()) appendLine(r.getString(R.string.fall_of_wickets, fowText(r, c, inn)))
        inn.bowlers.forEach { b ->
            appendLine(r.getString(R.string.text_bowler_line, r.playerName(c, field, b.player), oversText(b.balls), b.maidens, b.runs, b.wickets))
        }
    }
    appendLine()
    append(r.getString(R.string.share_footer))
}

private fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, context.getString(R.string.share_text)))
}

/** Written to the cache and handed out through FileProvider, so no storage permission is needed. */
private suspend fun shareImage(context: Context, bitmap: Bitmap, id: Long) {
    val file = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        File(dir, "scorecard-$id.png").also { f -> f.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.share", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("image/png")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, context.getString(R.string.share_image)))
}
