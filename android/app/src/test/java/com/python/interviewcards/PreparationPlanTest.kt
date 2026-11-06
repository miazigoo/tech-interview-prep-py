package com.python.interviewcards
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
class PreparationPlanTest {
    private val today=LocalDate.of(2026,11,2)
    @Test fun deadlineIncludesTodayAndCapacityIsExplicit() {
        val p=PreparationPlan.calculate(today,today.plusDays(9),20,155)
        assertEquals(10L,p.days);assertEquals(16,p.needed);assertEquals(7,p.goal);assertTrue(p.tight)
    }
    @Test fun completedDeckDoesNotReturnLearnedCards() { val p=PreparationPlan.calculate(today,today,20,0);assertEquals(0,p.goal);assertFalse(p.tight) }
    @Test(expected=IllegalArgumentException::class) fun pastDateRejected() { PreparationPlan.calculate(today,today.minusDays(1),20,5) }
}
