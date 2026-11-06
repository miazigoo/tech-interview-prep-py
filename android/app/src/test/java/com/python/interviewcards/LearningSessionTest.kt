package com.python.interviewcards
import org.junit.Assert.*
import org.junit.Test
class LearningSessionTest {
    @Test fun backgroundTimeDoesNotConsumeSessionBudget() {
        val s=LearningSession("10 minutes",listOf("a","b"),600000)
        s.start(1000);s.tick(101000);s.pause(111000)
        assertEquals(110000,s.activeMillis)
        s.tick(999000);assertEquals(110000,s.activeMillis)
        s.start(1000000);s.tick(1490000);assertTrue(s.expired())
    }
    @Test fun visitsAreUniqueAndSessionsTerminate() {
        val s=LearningSession("review",listOf("a","b"))
        s.visit();s.visit();assertEquals(1,s.visited.size)
        assertEquals("b",s.next());s.visit();assertNull(s.next());assertEquals(2,s.visited.size)
    }
    @Test fun twentyMeansFiniteAtMostTwentyIds() {
        val s=LearningSession("20",(1..20).map { "$it" })
        repeat(20) { assertNotNull(s.current());s.visit();s.next() }
        assertNull(s.current());assertEquals(20,s.visited.size)
    }
}
