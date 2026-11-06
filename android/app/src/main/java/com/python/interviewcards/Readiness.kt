package com.python.interviewcards

data class AnswerEvidence(val score:Int,val assisted:Boolean,val source:String,val at:Long=System.currentTimeMillis()) {
    init { require(score in 0..2);require(!assisted || score<=1) }
}
data class TopicReadiness(val total:Int,val assessed:Int,val independent:Int,val assisted:Int,val partial:Int,val incorrect:Int,val percent:Int?)
object Readiness {
    fun calculate(ids:Set<String>,evidence:Map<String,AnswerEvidence>):TopicReadiness {
        val values=evidence.filterKeys { it in ids }.values
        return TopicReadiness(ids.size,values.size,values.count { it.score==2 && !it.assisted },values.count { it.assisted },values.count { it.score==1 && !it.assisted },values.count { it.score==0 },if(values.isEmpty()) null else kotlin.math.round(values.sumOf { it.score }*50.0/values.size).toInt())
    }
}
object AudioNavigation {
    fun bounded(target:Long,duration:Int):Int=target.coerceIn(0,(duration-1).coerceAtLeast(0).toLong()).toInt()
    fun phrase(position:Int,starts:List<Int>):Int {
        val before=starts.filter { it<=position }.sorted()
        val last=before.lastOrNull() ?: return 0
        return if(position-last<700) before.dropLast(1).lastOrNull() ?: 0 else last
    }
}
