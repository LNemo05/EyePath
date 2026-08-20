package org.walkguard.app.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class PauseDeadlineTest {
    @Test fun positiveMinutesProduceExpectedDeadline() {
        assertEquals(
            PauseDeadlineResult.Valid(deadlineEpochMs = 1_300_000L),
            calculatePauseDeadline(input = "5", nowEpochMs = 1_000_000L)
        )
    }

    @Test fun blankInputIsInvalid() {
        assertEquals(PauseDeadlineResult.Invalid, calculatePauseDeadline("", 1_000L))
    }

    @Test fun malformedInputIsInvalid() {
        assertEquals(PauseDeadlineResult.Invalid, calculatePauseDeadline("1.5", 1_000L))
    }

    @Test fun zeroIsInvalid() {
        assertEquals(PauseDeadlineResult.Invalid, calculatePauseDeadline("0", 1_000L))
    }

    @Test fun negativeInputIsInvalid() {
        assertEquals(PauseDeadlineResult.Invalid, calculatePauseDeadline("-1", 1_000L))
    }

    @Test fun parseOverflowIsTooLarge() {
        assertEquals(
            PauseDeadlineResult.TooLarge,
            calculatePauseDeadline("92233720368547758070", 1_000L)
        )
    }

    @Test fun multiplicationOverflowIsTooLarge() {
        val overflowingMinutes = Long.MAX_VALUE / 60_000L + 1L

        assertEquals(
            PauseDeadlineResult.TooLarge,
            calculatePauseDeadline(overflowingMinutes.toString(), 1_000L)
        )
    }

    @Test fun epochAdditionOverflowIsTooLarge() {
        assertEquals(
            PauseDeadlineResult.TooLarge,
            calculatePauseDeadline("1", Long.MAX_VALUE - 59_999L)
        )
    }
}
