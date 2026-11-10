package com.crylo.cricscore.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.crylo.cricscore.R
import com.crylo.cricscore.data.CricDatabase
import com.crylo.cricscore.data.MatchEntity
import com.crylo.cricscore.engine.MatchConfig
import com.crylo.cricscore.engine.MatchEngine
import com.crylo.cricscore.engine.MatchEvent
import com.crylo.cricscore.engine.MatchState
import com.crylo.cricscore.engine.Rules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface Screen {
    data object Home : Screen
    data object Setup : Screen
    data object Live : Screen
    data class Scorecard(val matchId: Long, val fromLive: Boolean) : Screen
}

/** A stored match together with its replayed state. */
data class MatchItem(
    val id: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val config: MatchConfig,
    val events: List<MatchEvent>,
    val state: MatchState,
)

/** What the setup form holds before a match exists. */
data class SetupDraft(
    val teamA: String,
    val teamB: String,
    val overs: Int = 5,
    val playersPerSide: Int = 6,
    /** 0 if [teamA] bats first. */
    val battingFirst: Int = 0,
    val playersA: List<String> = List(6) { "" },
    val playersB: List<String> = List(6) { "" },
    val rules: Rules = Rules(),
)

class AppViewModel(app: Application, private val saved: SavedStateHandle) : AndroidViewModel(app) {
    private val dao = CricDatabase.get(app).matches()
    private val saveLock = Mutex()

    private val _screen = MutableStateFlow(restoreScreen())
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _live = MutableStateFlow<MatchItem?>(null)
    val live: StateFlow<MatchItem?> = _live.asStateFlow()

    private val _draft = MutableStateFlow(
        SetupDraft(app.getString(R.string.default_team_a), app.getString(R.string.default_team_b)),
    )
    val draft: StateFlow<SetupDraft> = _draft.asStateFlow()

    val history: StateFlow<List<MatchItem>?> = dao.observeAll()
        .map { rows -> rows.map { it.toItem() } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        // After process death the screen comes back from the saved state; the match it
        // was showing comes back from the database.
        saved.get<Long>(KEY_LIVE)?.let { id -> viewModelScope.launch { dao.get(id)?.let { _live.value = it.toItem() } } }
    }

    private fun MatchEntity.toItem(): MatchItem {
        val config = config()
        val events = eventList()
        return MatchItem(id, createdAt, updatedAt, config, events, MatchEngine.replay(config, events))
    }

    private fun restoreScreen(): Screen = when (val s = saved.get<String>(KEY_SCREEN)) {
        "setup" -> Screen.Setup
        "live" -> Screen.Live
        null, "home" -> Screen.Home
        else -> s.split(':').let { Screen.Scorecard(it[1].toLong(), it[2] == "1") }
    }

    private fun go(s: Screen) {
        _screen.value = s
        saved[KEY_SCREEN] = when (s) {
            Screen.Home -> "home"
            Screen.Setup -> "setup"
            Screen.Live -> "live"
            is Screen.Scorecard -> "card:${s.matchId}:${if (s.fromLive) 1 else 0}"
        }
    }

    fun back() {
        when (val s = _screen.value) {
            is Screen.Scorecard -> go(if (s.fromLive && _live.value != null) Screen.Live else Screen.Home)
            else -> go(Screen.Home)
        }
    }

    fun openScorecard(id: Long) = go(Screen.Scorecard(id, fromLive = _screen.value == Screen.Live))

    /** Prefills the form from the last match: the same friends tend to play by the same rules. */
    fun newMatch() {
        viewModelScope.launch {
            dao.latest()?.config()?.let { c ->
                _draft.value = SetupDraft(c.teams[0], c.teams[1], c.overs, c.playersPerSide, 0, c.players[0], c.players[1], c.rules)
            }
            go(Screen.Setup)
        }
    }

    fun updateDraft(transform: (SetupDraft) -> SetupDraft) {
        _draft.value = transform(_draft.value)
    }

    fun startMatch() {
        val d = _draft.value
        val res = getApplication<Application>()
        val a = d.teamA.trim().ifEmpty { res.getString(R.string.default_team_a) } to d.playersA
        val b = d.teamB.trim().ifEmpty { res.getString(R.string.default_team_b) } to d.playersB
        val (first, second) = if (d.battingFirst == 0) a to b else b to a
        val config = MatchConfig(
            teams = listOf(first.first, second.first),
            overs = d.overs,
            playersPerSide = d.playersPerSide,
            players = listOf(first.second.map { it.trim() }, second.second.map { it.trim() }),
            rules = d.rules,
        )
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val id = dao.insert(MatchEntity.from(0, now, config, emptyList(), finished = false))
            _live.value = MatchItem(id, now, now, config, emptyList(), MatchEngine.replay(config, emptyList()))
            saved[KEY_LIVE] = id
            go(Screen.Live)
        }
    }

    fun resume(id: Long) {
        viewModelScope.launch {
            val row = dao.get(id) ?: return@launch
            _live.value = row.toItem()
            saved[KEY_LIVE] = id
            go(Screen.Live)
        }
    }

    fun record(event: MatchEvent) = setEvents { it + event }

    fun undo() = setEvents { MatchEngine.undo(it) }

    fun renamePlayer(team: Int, player: Int, name: String) {
        val m = _live.value ?: return
        val players = m.config.players.mapIndexed { t, list ->
            if (t == team) list.mapIndexed { i, n -> if (i == player) name.trim() else n } else list
        }
        setConfig(m.config.copy(players = players))
    }

    fun setPlayers(players: List<List<String>>) {
        val m = _live.value ?: return
        setConfig(m.config.copy(players = players.map { list -> list.map { it.trim() } }))
    }

    private fun setConfig(config: MatchConfig) {
        val m = _live.value ?: return
        _live.value = m.copy(config = config, state = m.state.copy(config = config))
        persist()
    }

    private fun setEvents(transform: (List<MatchEvent>) -> List<MatchEvent>) {
        val m = _live.value ?: return
        val events = transform(m.events)
        _live.value = m.copy(events = events, state = MatchEngine.replay(m.config, events))
        persist()
    }

    /**
     * Saves after every change, so a killed process loses nothing. The fair mutex keeps
     * writes in tap order, and each write takes the newest state rather than the one
     * that triggered it.
     */
    private fun persist() {
        viewModelScope.launch {
            saveLock.withLock {
                val m = _live.value ?: return@withLock
                dao.update(MatchEntity.from(m.id, m.createdAt, m.config, m.events, finished = m.state.isOver))
            }
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            dao.delete(id)
            if (_live.value?.id == id) {
                _live.value = null
                saved.remove<Long>(KEY_LIVE)
            }
            go(Screen.Home)
        }
    }

    private companion object {
        const val KEY_SCREEN = "screen"
        const val KEY_LIVE = "live"
    }
}
