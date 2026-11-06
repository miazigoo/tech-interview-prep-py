package com.python.interviewcards
import org.junit.Assert.*
import org.junit.Test
class PlaybackPlanTest {
    @Test fun exactNumberOfFullQuestionAnswerCyclesForEveryRepetitionSetting() {
        for(n in 1..5) {
            val plan=PlaybackPlan(n);var questions=0;var answers=0
            while(true) {
                assertEquals(Phase.QUESTION,plan.phase);questions++
                assertEquals(Phase.THINKING,plan.questionEnded(true));plan.answerStarted()
                assertEquals(Phase.ANSWER,plan.phase);answers++
                if(plan.answerEnded()==Phase.FINISHED) break
                plan.nextCycle()
            }
            assertEquals(n,questions);assertEquals(n,answers);assertEquals(n,plan.cycle)
        }
    }
    @Test fun understandingModeWaitsForExplicitReveal() {
        val plan=PlaybackPlan(2);assertEquals(Phase.WAIT_REVEAL,plan.questionEnded(false))
        plan.answerStarted();assertEquals(Phase.BETWEEN,plan.answerEnded())
        plan.nextCycle();assertEquals(Phase.THINKING,plan.questionEnded(true))
    }
    @Test(expected=IllegalArgumentException::class) fun invalidRepeatCountRejected() { PlaybackPlan(6) }
    @Test(expected=IllegalStateException::class) fun answerCannotFinishBeforeItStarted() { PlaybackPlan(1).answerEnded() }
}
