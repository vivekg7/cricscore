package com.crylo.cricscore

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.crylo.cricscore.ui.AppViewModel
import com.crylo.cricscore.ui.CricTheme
import com.crylo.cricscore.ui.HomeScreen
import com.crylo.cricscore.ui.LiveScreen
import com.crylo.cricscore.ui.ScorecardScreen
import com.crylo.cricscore.ui.Screen
import com.crylo.cricscore.ui.SetupScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            CricTheme {
                Surface(Modifier.fillMaxSize()) { App() }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun App(vm: AppViewModel = viewModel()) {
    val screen by vm.screen.collectAsStateWithLifecycle()
    val live by vm.live.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val draft by vm.draft.collectAsStateWithLifecycle()

    BackHandler(enabled = screen != Screen.Home) { vm.back() }

    when (val s = screen) {
        Screen.Home -> HomeScreen(history, vm::newMatch, vm::resume, vm::openScorecard)
        Screen.Setup -> SetupScreen(draft, vm::updateDraft, vm::back, vm::startMatch)
        Screen.Live -> live?.let { m ->
            LiveScreen(
                match = m,
                onBack = vm::back,
                onRecord = vm::record,
                onUndo = vm::undo,
                onScorecard = { vm.openScorecard(m.id) },
                onRename = vm::renamePlayer,
                onPlayers = vm::setPlayers,
                onNewMatch = vm::newMatch,
            )
        } ?: Loading()
        is Screen.Scorecard -> {
            // The live match is fresher than the history query, which lags a write behind.
            val m = live?.takeIf { it.id == s.matchId } ?: history?.firstOrNull { it.id == s.matchId }
            if (m == null) {
                Loading()
            } else {
                ScorecardScreen(
                    match = m,
                    canResume = !m.state.isOver && !s.fromLive,
                    onBack = vm::back,
                    onResume = { vm.resume(m.id) },
                    onDelete = { vm.delete(m.id) },
                )
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun Loading() = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
