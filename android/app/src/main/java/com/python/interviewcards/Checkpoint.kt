package com.python.interviewcards

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject

/** One last unfinished activity, independent of the progress export. apply() is flushed on normal lifecycle exit. */
class Checkpoint(context:Context) {
    private val prefs=context.applicationContext.getSharedPreferences("resume_v5",Context.MODE_PRIVATE)
    fun read():JSONObject?=runCatching { JSONObject(prefs.getString("state",null) ?: return null).takeIf { it.optInt("version")==1 } }.getOrNull()
    fun save(owner:String,title:String,payload:JSONObject) { prefs.edit().putString("state",JSONObject().put("version",1).put("owner",owner).put("title",title).put("payload",payload).put("at",System.currentTimeMillis()).toString()).apply() }
    fun clear(owner:String) { if(read()?.optString("owner")==owner) prefs.edit().remove("state").apply() }
}
class CheckpointTicker(private val action:()->Unit) {
    private val h=Handler(Looper.getMainLooper())
    private val tick=object:Runnable { override fun run() { action();h.postDelayed(this,1000) } }
    fun start() { h.removeCallbacks(tick);h.postDelayed(tick,1000) }
    fun stop() { h.removeCallbacks(tick) }
}
fun LearningQueue.toJson():JSONObject=state().let { s -> JSONObject().put("counts",JSONObject(s.counts as Map<*,*>)).put("consumed",JSONObject(s.consumed as Map<*,*>)).put("past",JSONArray(s.past)).put("current",s.current).put("shown",s.shown).put("total",s.total) }
fun LearningQueue.restoreJson(j:JSONObject,eligible:Set<String>):Boolean=runCatching {
    fun map(key:String):Map<String,Int> { val o=j.getJSONObject(key);return o.keys().asSequence().associateWith { o.getInt(it) } }
    val a=j.getJSONArray("past");restore(QueueState(map("counts"),map("consumed"),(0 until a.length()).map { a.getString(it) },j.optString("current"),j.getInt("shown"),j.getInt("total")),eligible)
}.getOrDefault(false)
fun LearningSession.toJson():JSONObject=JSONObject().put("name",name).put("ids",JSONArray(ids)).put("duration",durationMillis).put("index",index).put("visited",JSONArray(visited.toList())).put("answers",JSONObject(answers as Map<*,*>)).put("active",activeMillis).put("learned",learned).put("difficult",difficult)
fun restoreSession(j:JSONObject,known:Set<String>):LearningSession {
    fun strings(key:String):List<String> { val a=j.getJSONArray(key);return (0 until a.length()).map { a.getString(it) } }
    val ids=strings("ids");require(ids.size<=155 && ids.toSet().size==ids.size && ids.all { it in known })
    val answers=j.getJSONObject("answers");return LearningSession(j.getString("name"),ids,j.getLong("duration")).apply {
        restore(j.getInt("index"),strings("visited").toSet(),answers.keys().asSequence().associateWith { answers.getString(it) },j.getLong("active"),j.getInt("learned"),j.getInt("difficult"))
    }
}
