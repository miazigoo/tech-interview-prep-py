package com.python.interviewcards

data class ReviewSchedule(val dueAt:Long,val streak:Int,val lastAt:Long) {
    init { require(dueAt>=0 && lastAt>=0 && streak in 0..5) }
}
object SpacedReview {
    private const val DAY=86_400_000L
    fun next(previous:ReviewSchedule?,score:Int,assisted:Boolean,now:Long):ReviewSchedule {
        require(score in 0..2 && now>=0)
        // Regrading the same answer before it is due must not advance several learning intervals.
        if(score==2 && !assisted && previous!=null && previous.streak>0 && now<previous.dueAt) return previous
        val streak=if(score==2 && !assisted) ((previous?.streak ?: 0)+1).coerceAtMost(5) else 0
        val interval=when { score==0->600_000L;score<2 || assisted->DAY;else->listOf(1L,3L,7L,14L,30L)[streak-1]*DAY }
        return ReviewSchedule(now+interval,streak,now)
    }
    fun due(ids:List<String>,learned:Set<String>,hard:Set<String>,schedule:Map<String,ReviewSchedule>,includeLearned:Boolean,now:Long):List<String> =
        ids.filter { (includeLearned || it !in learned) && (schedule[it]?.dueAt ?: 0)<=now }
            .sortedWith(compareBy<String> { schedule[it]?.dueAt ?: 0 }.thenBy { if(it in hard) 0 else 1 }.thenBy { it })
}
