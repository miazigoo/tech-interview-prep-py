package com.python.interviewcards
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random
class LearningQueueTest {
    @Test fun hardCardAppearsThreeTimesAndNewCardsOnceWithoutAdjacentRepeats() {
        repeat(100) { seed ->
            val q=LearningQueue(Random(seed));val weights=mapOf("hard" to 3,"a" to 1,"b" to 1)
            val result=mutableListOf<String>();q.begin(weights)
            while(q.current!=null) { result.add(q.current!!);q.next() }
            assertEquals(mapOf("hard" to 3,"a" to 1,"b" to 1),result.groupingBy { it }.eachCount())
            assertTrue(result.zipWithNext().all { it.first!=it.second })
        }
    }
    @Test fun learnedCardIsRemovedFromRestOfRound() {
        val q=LearningQueue(Random(1));q.begin(mapOf("hard" to 3,"a" to 1,"b" to 1),"hard");q.update("hard",0)
        val rest=mutableListOf<String>();while(q.next()!=null) rest.add(q.current!!)
        assertFalse("hard" in rest);assertEquals(2,rest.size)
    }
    @Test fun unmarkingHardRemovesRemainingExtraCopies() {
        val q=LearningQueue(Random(1));q.begin(mapOf("h" to 3,"a" to 1),"h");q.update("h",1)
        assertEquals("a",q.next());assertNull(q.next())
    }
    @Test fun markingHardAddsCopiesDuringRound() {
        val q=LearningQueue(Random(1));q.begin(mapOf("a" to 1,"b" to 1),"a");q.update("a",3)
        val rest=mutableListOf<String>();while(q.next()!=null) rest.add(q.current!!)
        assertEquals(2,rest.count { it=="a" });assertEquals(1,rest.count { it=="b" })
    }
    @Test fun emptySingleCardAndBoundaryAreHandled() {
        val q=LearningQueue(Random(2));assertNull(q.begin(emptyMap()));assertNull(q.next())
        assertEquals("x",q.begin(mapOf("x" to 1)));assertNull(q.next())
        repeat(50) { assertEquals("b",q.begin(mapOf("a" to 1,"b" to 1),avoid="a")) }
    }
    @Test fun historyNeverReturnsExcludedCard() {
        val q=LearningQueue(Random(3));q.begin(mapOf("a" to 1,"b" to 1,"c" to 1),"a");q.next()
        assertNotEquals("a",q.previous(setOf("b","c")))
    }
}
