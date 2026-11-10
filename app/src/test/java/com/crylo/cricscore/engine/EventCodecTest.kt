package com.crylo.cricscore.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class EventCodecTest {

    @Test fun roundTripsEveryEventType() {
        val events = listOf(
            MatchEvent.StartInnings(0, 1, 3),
            MatchEvent.Delivery(DeliveryKind.NORMAL, 4),
            MatchEvent.Delivery(DeliveryKind.WIDE, 1),
            MatchEvent.Delivery(DeliveryKind.NO_BALL, 6),
            MatchEvent.Delivery(DeliveryKind.BYE, 2),
            MatchEvent.Delivery(DeliveryKind.LEG_BYE, 1),
            MatchEvent.Delivery(DeliveryKind.NORMAL, 0, Wicket(WicketType.CAUGHT)),
            MatchEvent.Delivery(DeliveryKind.NORMAL, 1, Wicket(WicketType.RUN_OUT, Slot.NON_STRIKER, Slot.STRIKER)),
            MatchEvent.NewBatter(2),
            MatchEvent.NewBowler(4),
            MatchEvent.Retire(Slot.NON_STRIKER),
            MatchEvent.SwapStrike,
            MatchEvent.EndInnings,
        )
        assertEquals(events, EventCodec.decode(EventCodec.encode(events)))
    }

    @Test fun emptyTextIsNoEvents() {
        assertEquals(emptyList<MatchEvent>(), EventCodec.decode(""))
    }

    @Test fun skipsGarbageLines() {
        val decoded = EventCodec.decode("S 0 1 0\nD BOGUS 1\nZ\nD NORMAL x\nD NORMAL 2")
        assertEquals(listOf(MatchEvent.StartInnings(0, 1, 0), MatchEvent.Delivery(DeliveryKind.NORMAL, 2)), decoded)
    }
}
