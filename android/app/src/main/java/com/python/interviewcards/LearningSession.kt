package com.python.interviewcards

/** Finite sessions count unique visits and active foreground time. They never mutate card status. */
class LearningSession(val name: String,val ids: List<String>,val durationMillis: Long=0) {
    init { require(durationMillis>=0) }
    var index=0;private set
    val visited=linkedSetOf<String>()
    val answers=mutableMapOf<String,String>()
    var activeMillis=0L;private set
    var lastTick: Long?=null;private set
    var learned=0
    var difficult=0
    fun restore(savedIndex:Int,savedVisited:Set<String>,savedAnswers:Map<String,String>,savedActive:Long,savedLearned:Int,savedDifficult:Int) {
        require(savedIndex in 0..ids.size && savedActive>=0 && savedVisited.all { it in ids } && savedAnswers.keys.all { it in ids })
        require(savedAnswers.values.all { it in setOf("good","partial","bad","returned") } && savedLearned>=0 && savedDifficult>=0)
        index=savedIndex;visited.clear();visited.addAll(savedVisited);answers.clear();answers.putAll(savedAnswers);activeMillis=savedActive;learned=savedLearned;difficult=savedDifficult;lastTick=null
    }
    fun start(now: Long) { lastTick=now }
    fun pause(now: Long) { tick(now);lastTick=null }
    fun tick(now: Long) { lastTick?.let { activeMillis+=(now-it).coerceAtLeast(0) };if(lastTick!=null) lastTick=now }
    fun expired()=durationMillis>0 && activeMillis>=durationMillis
    fun current(): String?=ids.getOrNull(index)
    fun visit() { current()?.let { visited.add(it) } }
    fun next(): String? { index++;return current() }
}
