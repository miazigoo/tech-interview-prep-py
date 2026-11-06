package com.python.interviewcards
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random
class V5LogicTest {
    @Test fun scheduleIntervalsAndFailuresAreDeterministic() {
        val day=86_400_000L;var previous:ReviewSchedule?=null;var now=1234L
        for((i,d) in listOf(1,3,7,14,30,30).withIndex()) { previous=SpacedReview.next(previous,2,false,now);assertEquals(now+d*day,previous.dueAt);assertEquals(minOf(i+1,5),previous.streak);now=previous.dueAt }
        val failed=SpacedReview.next(previous,0,false,now);assertEquals(now+600000,failed.dueAt);assertEquals(0,failed.streak)
        val hinted=SpacedReview.next(previous,2,true,now);assertEquals(now+day,hinted.dueAt);assertEquals(0,hinted.streak)
    }
    @Test fun earlyRegradingDoesNotArtificiallyAdvanceInterval() {
        val first=SpacedReview.next(null,2,false,1000)
        assertEquals(first,SpacedReview.next(first,2,false,2000))
        val second=SpacedReview.next(first,2,false,first.dueAt);assertEquals(2,second.streak)
    }
    @Test fun learnedAreOptInAndListeningDoesNotReschedule() {
        val schedule=mapOf("a" to ReviewSchedule(2000,1,1000))
        assertEquals(listOf("b"),SpacedReview.due(listOf("a","b","c"),setOf("c"),emptySet(),schedule,false,1500))
        assertEquals(listOf("b","c"),SpacedReview.due(listOf("a","b","c"),setOf("c"),emptySet(),schedule,true,1500))
        assertEquals(2000,schedule.getValue("a").dueAt)
    }
    @Test fun queueRestoresRemainingQuotaAndFiltersLearned() {
        val q=LearningQueue(Random(4));q.begin(mapOf("a" to 3,"b" to 1),"a");q.next();val saved=q.state()
        val restored=LearningQueue(Random(4));assertTrue(restored.restore(saved,setOf("a","b")))
        assertEquals(q.current,restored.current);assertEquals(q.shown,restored.shown)
        var visits=restored.shown;while(restored.next()!=null) visits++
        assertEquals(4,visits)
        assertFalse(LearningQueue().restore(saved,emptySet()))
    }
    @Test fun sessionRestoresWithoutCountingClosedAppTime() {
        val s=LearningSession("10 minutes",listOf("a","b"),600000);s.start(1000);s.tick(4000);s.visit();s.answers["a"]="good";s.next()
        val restored=LearningSession(s.name,s.ids,s.durationMillis);restored.restore(s.index,s.visited,s.answers,s.activeMillis,s.learned,s.difficult)
        restored.tick(999999);assertEquals(3000,restored.activeMillis);assertEquals("b",restored.current())
        restored.start(999999);restored.tick(1000999);assertEquals(4000,restored.activeMillis)
    }
    @Test fun restoredPlanPreservesCurrentRepetitionAndPhase() {
        val p=PlaybackPlan(5);p.restore(3,Phase.ANSWER);assertEquals(Phase.BETWEEN,p.answerEnded());p.nextCycle();assertEquals(4,p.cycle)
    }
}
