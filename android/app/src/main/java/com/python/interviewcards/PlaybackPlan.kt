package com.python.interviewcards

enum class Phase { QUESTION, THINKING, WAIT_REVEAL, ANSWER, BETWEEN, FINISHED }
class PlaybackPlan(val repetitions: Int) {
    init { require(repetitions in 1..5) }
    var cycle = 1; private set
    var phase = Phase.QUESTION; private set
    fun restore(savedCycle:Int,savedPhase:Phase) { require(savedCycle in 1..repetitions);cycle=savedCycle;phase=savedPhase }
    fun questionEnded(revealed: Boolean): Phase {
        check(phase == Phase.QUESTION)
        phase = if (revealed) Phase.THINKING else Phase.WAIT_REVEAL
        return phase
    }
    fun answerStarted() { check(phase == Phase.THINKING || phase == Phase.WAIT_REVEAL); phase = Phase.ANSWER }
    fun answerEnded(): Phase {
        check(phase == Phase.ANSWER)
        phase = if (cycle < repetitions) Phase.BETWEEN else Phase.FINISHED
        return phase
    }
    fun nextCycle() { check(phase == Phase.BETWEEN); cycle++; phase = Phase.QUESTION }
}
