package com.crylo.cricscore.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import com.crylo.cricscore.engine.EventCodec
import com.crylo.cricscore.engine.MatchConfig
import com.crylo.cricscore.engine.MatchEvent
import com.crylo.cricscore.engine.Rules
import kotlinx.coroutines.flow.Flow

/**
 * One row per match: the setup plus the encoded event list. The whole row is rewritten
 * after every ball; a match is a few kilobytes at most, and a single row write is atomic,
 * so a process kill can never leave the setup and the events out of step.
 */
@Entity(tableName = "matches")
data class MatchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val teamA: String,
    val teamB: String,
    val overs: Int,
    val playersPerSide: Int,
    /** Newline-separated; names are single-line text fields, so no escaping is needed. */
    val playersA: String,
    val playersB: String,
    val wideRuns: Int,
    val wideReball: Boolean,
    val noBallRuns: Int,
    val noBallReball: Boolean,
    val freeHit: Boolean,
    val lastManStanding: Boolean,
    val allowRetire: Boolean,
    val events: String,
    val finished: Boolean,
) {
    fun config(): MatchConfig {
        fun names(s: String) = s.split('\n').let { list -> List(playersPerSide) { list.getOrElse(it) { "" } } }
        return MatchConfig(
            teams = listOf(teamA, teamB),
            overs = overs,
            playersPerSide = playersPerSide,
            players = listOf(names(playersA), names(playersB)),
            rules = Rules(wideRuns, wideReball, noBallRuns, noBallReball, freeHit, lastManStanding, allowRetire),
        )
    }

    fun eventList(): List<MatchEvent> = EventCodec.decode(events)

    companion object {
        fun from(id: Long, createdAt: Long, config: MatchConfig, events: List<MatchEvent>, finished: Boolean) = MatchEntity(
            id = id,
            createdAt = createdAt,
            updatedAt = System.currentTimeMillis(),
            teamA = config.teams[0],
            teamB = config.teams[1],
            overs = config.overs,
            playersPerSide = config.playersPerSide,
            playersA = config.players[0].joinToString("\n") { it.replace('\n', ' ') },
            playersB = config.players[1].joinToString("\n") { it.replace('\n', ' ') },
            wideRuns = config.rules.wideRuns,
            wideReball = config.rules.wideReball,
            noBallRuns = config.rules.noBallRuns,
            noBallReball = config.rules.noBallReball,
            freeHit = config.rules.freeHit,
            lastManStanding = config.rules.lastManStanding,
            allowRetire = config.rules.allowRetire,
            events = EventCodec.encode(events),
            finished = finished,
        )
    }
}

@Dao
interface MatchDao {
    @Insert suspend fun insert(match: MatchEntity): Long
    @Update suspend fun update(match: MatchEntity)

    @Query("SELECT * FROM matches WHERE id = :id")
    suspend fun get(id: Long): MatchEntity?

    @Query("SELECT * FROM matches ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<MatchEntity>>

    @Query("SELECT * FROM matches ORDER BY createdAt DESC LIMIT 1")
    suspend fun latest(): MatchEntity?

    @Query("DELETE FROM matches WHERE id = :id")
    suspend fun delete(id: Long)
}

@Database(entities = [MatchEntity::class], version = 1, exportSchema = true)
abstract class CricDatabase : RoomDatabase() {
    abstract fun matches(): MatchDao

    companion object {
        @Volatile private var instance: CricDatabase? = null

        fun get(context: Context): CricDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, CricDatabase::class.java, "cricscore.db")
                .build().also { instance = it }
        }
    }
}
