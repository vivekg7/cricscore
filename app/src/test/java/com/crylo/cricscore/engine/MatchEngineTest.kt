package com.crylo.cricscore.engine

import com.crylo.cricscore.engine.DeliveryKind.BYE
import com.crylo.cricscore.engine.DeliveryKind.LEG_BYE
import com.crylo.cricscore.engine.DeliveryKind.NORMAL
import com.crylo.cricscore.engine.DeliveryKind.NO_BALL
import com.crylo.cricscore.engine.DeliveryKind.WIDE
import com.crylo.cricscore.engine.MatchEvent.Delivery
import com.crylo.cricscore.engine.MatchEvent.EndInnings
import com.crylo.cricscore.engine.MatchEvent.NewBatter
import com.crylo.cricscore.engine.MatchEvent.NewBowler
import com.crylo.cricscore.engine.MatchEvent.Retire
import com.crylo.cricscore.engine.MatchEvent.StartInnings
import com.crylo.cricscore.engine.MatchEvent.SwapStrike
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchEngineTest {

    private fun config(overs: Int = 2, players: Int = 4, rules: Rules = Rules()) =
        MatchConfig(listOf("A", "B"), overs, players, rules = rules)

    /** Small builder so each test reads like a scorebook. */
    private class Script(val config: MatchConfig) {
        val events = mutableListOf<MatchEvent>(StartInnings(0, 1, 0))
        fun runs(vararg r: Int) = apply { r.forEach { events += Delivery(NORMAL, it) } }
        fun dots(n: Int) = apply { repeat(n) { events += Delivery(NORMAL, 0) } }
        fun add(vararg e: MatchEvent) = apply { events += e }
        fun out(type: WicketType = WicketType.BOWLED) = apply { events += Delivery(NORMAL, 0, Wicket(type)) }
        fun state() = MatchEngine.replay(config, events)
    }

    private fun script(c: MatchConfig = config()) = Script(c)

    @Test fun newMatchAsksForOpeners() {
        val s = MatchEngine.replay(config(), emptyList())
        assertEquals(Pending.StartInnings(0), s.pending)
        assertFalse(s.current.started)
    }

    @Test fun oddRunsRotateStrikeEvenRunsDoNot() {
        val s = script().runs(1).state().current
        assertEquals(1, s.striker); assertEquals(0, s.nonStriker)
        val t = script().runs(2).state().current
        assertEquals(0, t.striker)
        val u = script().runs(3, 4).state().current
        assertEquals(1, u.striker)
        assertEquals(7, u.runs)
    }

    @Test fun overEndSwapsStrikeAndAsksForBowler() {
        val s = script().dots(6).state()
        assertEquals(1, s.current.striker)
        assertEquals(6, s.current.legalBalls)
        assertNull(s.current.bowler)
        assertTrue(s.pending is Pending.NewBowler)
        assertEquals(0, (s.pending as Pending.NewBowler).previous)
    }

    @Test fun singleOffLastBallKeepsStrike() {
        val s = script().dots(5).runs(1).state().current
        assertEquals(0, s.striker)
    }

    @Test fun maidenIsCounted() {
        val s = script().dots(6).state().current
        assertEquals(1, s.bowlers.single().maidens)
        val t = script().dots(5).add(Delivery(LEG_BYE, 1)).state().current
        assertEquals("leg byes don't spoil a maiden", 1, t.bowlers.single().maidens)
        val u = script().dots(5).add(Delivery(WIDE)).dots(1).state().current
        assertEquals(0, u.bowlers.single().maidens)
    }

    @Test fun bowlerSuggestionAlternatesEnds() {
        val s = script(config(overs = 5)).dots(6).add(NewBowler(2)).dots(6).state()
        assertEquals(Pending.NewBowler(suggested = 0, previous = 2), s.pending)
        val first = script(config(overs = 5)).dots(6).state()
        assertEquals(1, (first.pending as Pending.NewBowler).suggested)
    }

    @Test fun wideIsNotALegalBallAndAddsPenalty() {
        val s = script().add(Delivery(WIDE)).state().current
        assertEquals(1, s.runs)
        assertEquals(0, s.legalBalls)
        assertEquals(1, s.extras.wides)
        assertEquals(0, s.batters[0].balls)
        assertEquals(1, s.bowlers[0].runs)
        assertEquals(1, s.bowlers[0].wides)
    }

    @Test fun wideWithRunsRotatesStrike() {
        val s = script().add(Delivery(WIDE, 1)).state().current
        assertEquals(2, s.runs)
        assertEquals(2, s.extras.wides)
        assertEquals(1, s.striker)
    }

    @Test fun overNeedsSixLegalBallsDespiteExtras() {
        val s = script().dots(3).add(Delivery(WIDE), Delivery(NO_BALL, 2)).dots(2).state().current
        assertEquals(5, s.legalBalls)
        assertEquals(0, s.striker)
        val o = s.overs.single()
        assertEquals(7, o.balls.size)
    }

    @Test fun gullyRuleWideCountsAsBallWithNoRun() {
        val c = config(rules = Rules(wideRuns = 0, wideReball = false))
        val s = script(c).add(Delivery(WIDE)).state().current
        assertEquals(0, s.runs)
        assertEquals(1, s.legalBalls)
        assertEquals(0, s.batters[0].balls)
    }

    @Test fun noBallCreditsBatterAndBowlerAndGivesFreeHit() {
        val s = script().add(Delivery(NO_BALL, 4)).state().current
        assertEquals(5, s.runs)
        assertEquals(1, s.extras.noBalls)
        assertEquals(4, s.batters[0].runs)
        assertEquals(1, s.batters[0].fours)
        assertEquals(1, s.batters[0].balls)
        assertEquals(5, s.bowlers[0].runs)
        assertEquals(0, s.legalBalls)
        assertTrue(s.freeHit)
    }

    @Test fun freeHitOnlyAllowsRunOut() {
        val s = script().add(Delivery(NO_BALL)).out(WicketType.BOWLED).state().current
        assertEquals(0, s.wickets)
        assertFalse("free hit is used up by a legal ball", s.freeHit)
        val ro = script().add(Delivery(NO_BALL), Delivery(NORMAL, 0, Wicket(WicketType.RUN_OUT))).state().current
        assertEquals(1, ro.wickets)
    }

    @Test fun freeHitCarriesOverAWide() {
        val s = script().add(Delivery(NO_BALL), Delivery(WIDE)).state().current
        assertTrue(s.freeHit)
    }

    @Test fun freeHitCanBeTurnedOff() {
        val s = script(config(rules = Rules(freeHit = false))).add(Delivery(NO_BALL)).state().current
        assertFalse(s.freeHit)
    }

    @Test fun byesAndLegByesCountAsBallsFacedButNotBatterRuns() {
        val s = script().add(Delivery(BYE, 2), Delivery(LEG_BYE, 1)).state().current
        assertEquals(3, s.runs)
        assertEquals(2, s.extras.byes)
        assertEquals(1, s.extras.legByes)
        assertEquals(0, s.batters[0].runs)
        assertEquals(2, s.batters[0].balls)
        assertEquals(0, s.bowlers[0].runs)
        assertEquals(1, s.striker)
    }

    @Test fun bowledBringsNewBatterOnStrike() {
        val s = script().runs(1).out().state()
        assertEquals(1, s.current.wickets)
        assertEquals(Pending.NewBatter(Slot.STRIKER, listOf(2, 3)), s.pending)
        assertEquals(0, s.current.nonStriker)
        val b = s.current.batters.first { it.player == 1 }
        assertEquals(Dismissal(WicketType.BOWLED, 0), b.dismissal)
        assertEquals(1, s.current.bowlers[0].wickets)
        assertEquals(FallOfWicket(1, 1, 2, 1), s.current.fallOfWickets.single())
    }

    @Test fun wicketOnLastBallPutsNewBatterAtNonStrikerEnd() {
        val s = script().dots(5).out().state()
        assertEquals(Pending.NewBatter(Slot.NON_STRIKER, listOf(2, 3)), s.pending)
        assertEquals(1, s.current.striker)
        val next = MatchEngine.replay(s.config, script().dots(5).out().add(NewBatter(2)).events)
        assertTrue(next.pending is Pending.NewBowler)
    }

    @Test fun runOutOfNonStrikerAtBowlersEnd() {
        val s = script().add(Delivery(NORMAL, 1, Wicket(WicketType.RUN_OUT, Slot.NON_STRIKER, Slot.NON_STRIKER))).state()
        val inn = s.current
        assertEquals(1, inn.runs)
        assertEquals(1, inn.batters[0].runs)
        assertEquals(Dismissal(WicketType.RUN_OUT, null), inn.batters[1].dismissal)
        assertEquals(0, inn.bowlers[0].wickets)
        // Wicket broken at the bowler's end: survivor faces, newcomer goes to the other end.
        assertEquals(0, inn.striker)
        assertEquals(Pending.NewBatter(Slot.NON_STRIKER, listOf(2, 3)), s.pending)
    }

    @Test fun runOutOfStrikerGoingForSecondAtStrikersEnd() {
        val s = script().add(Delivery(NORMAL, 1, Wicket(WicketType.RUN_OUT, Slot.STRIKER, Slot.STRIKER))).state()
        assertEquals(1, s.current.nonStriker)
        assertEquals(Pending.NewBatter(Slot.STRIKER, listOf(2, 3)), s.pending)
        assertEquals(1, s.current.batters[0].runs)
    }

    @Test fun runOutOffAWide() {
        val w = Wicket(WicketType.RUN_OUT, Slot.STRIKER, Slot.NON_STRIKER)
        val s = script().add(Delivery(WIDE, 0, w)).state().current
        assertEquals(1, s.wickets)
        assertEquals(1, s.runs)
        assertEquals(0, s.legalBalls)
    }

    @Test fun stumpedOffAWideCountsButBowledOffANoBallDoesNot() {
        val st = script().add(Delivery(WIDE, 0, Wicket(WicketType.STUMPED))).state().current
        assertEquals(1, st.wickets)
        val nb = script(config(rules = Rules(freeHit = false))).add(Delivery(NO_BALL, 0, Wicket(WicketType.BOWLED))).state().current
        assertEquals(0, nb.wickets)
    }

    @Test fun allOutEndsInningsAndAsksForSecond() {
        val s = script().out().add(NewBatter(2)).out().add(NewBatter(3)).out().state()
        assertTrue(s.current.complete)
        assertEquals(3, s.current.wickets)
        assertEquals(Pending.StartInnings(1), s.pending)
        assertEquals(1, s.target)
    }

    @Test fun lastManStandingBatsAlone() {
        val c = config(players = 3, rules = Rules(lastManStanding = true))
        val s = script(c).out().add(NewBatter(2)).out().state()
        val inn = s.current
        assertFalse(inn.complete)
        assertTrue(inn.batterAlone)
        assertNull(s.pending)
        assertEquals(1, inn.striker)
        // Alone, he keeps the strike on a single and at the end of the over.
        val t = MatchEngine.replay(c, script(c).out().add(NewBatter(2)).out().runs(1).dots(3).events)
        assertEquals(1, t.current.striker)
        assertNull(t.current.nonStriker)
        assertEquals(1, t.current.partnerships.last().runs)
        val u = MatchEngine.replay(c, script(c).out().add(NewBatter(2)).out().out().events)
        assertTrue(u.current.complete)
        assertEquals(3, u.current.wickets)
    }

    @Test fun withoutLastManStandingOneWicketShortIsAllOut() {
        val c = config(players = 3)
        val s = script(c).out().add(NewBatter(2)).out().state()
        assertTrue(s.current.complete)
    }

    @Test fun retiredBatterIsNotOutAndCanReturn() {
        val c = config(players = 3)
        val s = script(c).runs(2).add(Retire(Slot.STRIKER)).state()
        assertEquals(0, s.current.wickets)
        assertEquals(Pending.NewBatter(Slot.STRIKER, listOf(0, 2)), s.pending)
        val back = MatchEngine.replay(c, script(c).runs(2).add(Retire(Slot.STRIKER), NewBatter(2)).out().add(NewBatter(0)).events)
        val b0 = back.current.batters.first { it.player == 0 }
        assertFalse(b0.retired)
        assertEquals(2, b0.runs)
        assertNull(back.pending)
    }

    @Test fun inningsEndsWhenOversAreUp() {
        val c = config(overs = 1)
        val s = script(c).runs(1, 1, 1, 1, 1, 1).state()
        assertTrue(s.current.complete)
        assertEquals(Pending.StartInnings(1), s.pending)
        assertEquals(7, s.target)
    }

    @Test fun chaseFinishingMidOverEndsMatch() {
        val c = config(overs = 1)
        val s = script(c).runs(4, 0, 0, 0, 0, 0).add(StartInnings(0, 1, 0)).runs(1, 4).state()
        assertTrue(s.isOver)
        assertNull(s.pending)
        assertEquals(MatchResult.WonByWickets(team = 1, wickets = 3), s.result)
        assertEquals(2, s.current.legalBalls)
        assertEquals(1, s.current.index)
    }

    @Test fun chaseWonOnAWide() {
        val c = config(overs = 1)
        val s = script(c).runs(1).dots(5).add(StartInnings(0, 1, 0), Delivery(WIDE, 1)).state()
        assertEquals(MatchResult.WonByWickets(1, 3), s.result)
    }

    @Test fun defendingSideWinsByRuns() {
        val c = config(overs = 1)
        val s = script(c).runs(6, 6, 0, 0, 0, 0).add(StartInnings(0, 1, 0)).runs(4, 0, 0, 0, 0, 0).state()
        assertEquals(MatchResult.WonByRuns(0, 8), s.result)
    }

    @Test fun tie() {
        val c = config(overs = 1)
        val s = script(c).runs(2).dots(5).add(StartInnings(0, 1, 0)).runs(2).dots(5).state()
        assertEquals(MatchResult.Tie, s.result)
    }

    @Test fun allOutInChaseLosesByRuns() {
        val c = config(overs = 2, players = 2)
        val s = script(c).runs(4).dots(5).add(NewBowler(1)).dots(6).add(StartInnings(0, 1, 0)).runs(2).out().state()
        assertEquals(MatchResult.WonByRuns(0, 2), s.result)
    }

    @Test fun requiredRateAndRunsNeeded() {
        val c = config(overs = 2)
        val s = script(c).runs(6, 6, 0, 0, 0, 0).add(NewBowler(1)).dots(6).add(StartInnings(0, 1, 0)).runs(1).dots(5).state()
        assertEquals(13, s.target)
        assertEquals(12, s.runsNeeded)
        assertEquals(6, s.ballsLeft)
        assertEquals(12.0, s.requiredRate!!, 1e-9)
    }

    @Test fun endInningsEarly() {
        val s = script().runs(4).add(EndInnings).state()
        assertTrue(s.current.complete)
        assertEquals(5, s.target)
    }

    @Test fun partnershipsSplitAtWickets() {
        val s = script().runs(1, 2).add(Delivery(WIDE)).out().add(NewBatter(2)).runs(4).state().current
        assertEquals(2, s.partnerships.size)
        assertEquals(Partnership(listOf(0, 1), 4, 3), s.partnerships[0])
        assertEquals(Partnership(listOf(2, 0), 4, 1), s.partnerships[1])
    }

    @Test fun swapStrike() {
        val s = script().add(SwapStrike).state().current
        assertEquals(1, s.striker)
    }

    @Test fun undoDropsLastBall() {
        val events = script().runs(1, 4).events
        assertEquals(script().runs(1).events, MatchEngine.undo(events))
    }

    @Test fun undoTakesTheFollowingPromptsWithTheBall() {
        val events = script().dots(5).out().add(NewBatter(2), NewBowler(1)).events
        val undone = MatchEngine.undo(events)
        assertEquals(script().dots(5).events, undone)
        val s = MatchEngine.replay(config(), undone)
        assertEquals(0, s.current.wickets)
        assertEquals(0, s.current.bowler)
    }

    @Test fun undoRepeatedlyBackToTheStart() {
        var events: List<MatchEvent> = script().runs(1, 2, 3).events
        repeat(3) { events = MatchEngine.undo(events) }
        assertEquals(listOf<MatchEvent>(StartInnings(0, 1, 0)), events)
        assertEquals(emptyList<MatchEvent>(), MatchEngine.undo(events))
    }

    @Test fun undoReopensFinishedMatch() {
        val c = config(overs = 1)
        val events = script(c).runs(1).dots(5).add(StartInnings(0, 1, 0)).runs(2).events
        assertTrue(MatchEngine.replay(c, events).isOver)
        val s = MatchEngine.replay(c, MatchEngine.undo(events))
        assertFalse(s.isOver)
        assertNull(s.pending)
    }

    @Test fun invalidEventsAreIgnored() {
        val s = MatchEngine.replay(config(), listOf(Delivery(NORMAL, 4), NewBatter(3), StartInnings(0, 0, 0)))
        assertFalse(s.current.started)
        assertEquals(0, s.current.runs)
    }

    @Test fun oversNotation() {
        assertEquals("0.0", oversText(0))
        assertEquals("2.1", oversText(13))
        assertEquals("5.0", oversText(30))
    }
}
