package com.python.interviewcards
import org.junit.Assert.*
import org.junit.Test
class V4LogicTest {
    @Test fun unassessedTopicHasNoInventedPercent() {
        val r=Readiness.calculate(setOf("a","b"),emptyMap())
        assertNull(r.percent);assertEquals(0,r.assessed)
    }
    @Test fun readinessUsesLatestAssessmentAndSeparatesAssisted() {
        val map=mutableMapOf("a" to AnswerEvidence(2,false,"self"),"b" to AnswerEvidence(1,true,"self"),"other" to AnswerEvidence(2,false,"self"))
        val r=Readiness.calculate(setOf("a","b","c"),map)
        assertEquals(75,r.percent);assertEquals(2,r.assessed);assertEquals(1,r.independent);assertEquals(1,r.assisted)
        map["a"]=AnswerEvidence(0,false,"interview")
        assertEquals(25,Readiness.calculate(setOf("a","b","c"),map).percent)
    }
    @Test(expected=IllegalArgumentException::class) fun assistedCannotBeIndependentSuccess() { AnswerEvidence(2,true,"self") }
    @Test fun seekBoundsAndNaturalPauseNavigation() {
        assertEquals(0,AudioNavigation.bounded(-10000,20000));assertEquals(19999,AudioNavigation.bounded(99999,20000));assertEquals(0,AudioNavigation.bounded(9,0))
        assertEquals(8000,AudioNavigation.phrase(10000,listOf(0,3000,8000)))
        assertEquals(3000,AudioNavigation.phrase(8200,listOf(0,3000,8000)))
        assertEquals(0,AudioNavigation.phrase(100,listOf(0,3000)))
    }
}
