package com.python.interviewcards

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import org.json.JSONObject

private val Context.interviewStore by preferencesDataStore(name = "interview_progress")
class ProgressStore(private val context: Context) {
    val exerciseDrafts=mutableMapOf<String,String>()
    val schedules=mutableMapOf<String,ReviewSchedule>()
    val checklistHints=mutableSetOf<String>()
    val checklistMarks=mutableMapOf<String,Set<Int>>()
    var includeLearnedInDue=false
    var answerLevel="short"
    private fun schedulesJson()=JSONObject().apply { schedules.forEach { (id,v)->put(id,JSONObject().put("due",v.dueAt).put("streak",v.streak).put("last",v.lastAt)) } }
    private fun checklistsJson()=JSONObject().apply { checklistMarks.forEach { (id,v)->put(id,org.json.JSONArray(v.sorted())) } }
    val notes=mutableMapOf<String,String>()
    val views=mutableMapOf<String,Int>()
    val evidence=mutableMapOf<String,AnswerEvidence>()
    val routeProgress=mutableMapOf<String,Int>()
    fun viewed(id:String) { views[id]=(views.getOrDefault(id,0)+1).coerceAtMost(1000000) }
    fun assess(id:String,score:Int,assisted:Boolean,source:String) { evidence[id]=AnswerEvidence(score,assisted,source);schedules[id]=SpacedReview.next(schedules[id],score,assisted,System.currentTimeMillis()) }
    private fun evidenceJson()=JSONObject().apply { evidence.forEach { (id,v) ->put(id,JSONObject().put("score",v.score).put("assisted",v.assisted).put("source",v.source).put("at",v.at)) } }
    val statuses = mutableMapOf<String, CardStatus>()
    val exerciseResults = mutableMapOf<String,Boolean>()
    val mistakes=mutableMapOf<String,String>()
    var includeUnderstanding=true
    var interviewDate=""
    var dailyMinutes=20
    var planDay=""
    var planInitialRemaining=0
    val planCompleted=linkedSetOf<String>()
    var repetitions = 1
    var thinkingSeconds = 3
    var speed = 1f
    var listening = false
    var autoNext = false
    var lastCard = ""
    var lastCategory = ""
    var lastFilter = "ACTIVE"
    private val stateKey = stringPreferencesKey("state_v1")
    suspend fun load() {
        val raw = context.interviewStore.data.first()[stateKey] ?: return
        val state = JSONObject(raw)
        exerciseDrafts.clear();state.optJSONObject("exerciseDrafts")?.let { o->o.keys().forEach { exerciseDrafts[it]=o.getString(it) } }
        checklistHints.clear();state.optJSONArray("checklistHints")?.let { a->for(i in 0 until a.length()) checklistHints.add(a.getString(i)) }
        schedules.clear();checklistMarks.clear()
        state.optJSONObject("schedules")?.let { o->o.keys().forEach { id->val v=o.getJSONObject(id);schedules[id]=ReviewSchedule(v.getLong("due"),v.getInt("streak"),v.getLong("last")) } }
        state.optJSONObject("checklists")?.let { o->o.keys().forEach { id->val a=o.getJSONArray(id);checklistMarks[id]=(0 until a.length()).map { a.getInt(it) }.toSet() } }
        includeLearnedInDue=state.optBoolean("includeLearnedInDue",false);answerLevel=state.optString("answerLevel","short").takeIf { it in setOf("short","detailed") } ?: "short"
        notes.clear();views.clear();evidence.clear();routeProgress.clear()
        state.optJSONObject("notes")?.let { o ->o.keys().forEach { notes[it]=o.getString(it) } }
        state.optJSONObject("views")?.let { o ->o.keys().forEach { views[it]=o.getInt(it) } }
        state.optJSONObject("evidence")?.let { o ->o.keys().forEach { id ->val e=o.getJSONObject(id);evidence[id]=AnswerEvidence(e.getInt("score"),e.getBoolean("assisted"),e.getString("source"),e.getLong("at")) } }
        state.optJSONObject("routes")?.let { o ->o.keys().forEach { routeProgress[it]=o.getInt(it) } }
        statuses.clear()
        exerciseResults.clear()
        mistakes.clear()
        state.optJSONObject("mistakes")?.let { items -> items.keys().forEach { mistakes[it]=items.getString(it) } }
        planDay=state.optString("planDay","");planInitialRemaining=state.optInt("planInitialRemaining",0);planCompleted.clear()
        state.optJSONArray("planCompleted")?.let { a -> for(i in 0 until a.length()) planCompleted.add(a.getString(i)) }
        includeUnderstanding=state.optBoolean("understanding",true)
        interviewDate=state.optString("interviewDate","")
        dailyMinutes=state.optInt("dailyMinutes",20).coerceIn(5,120)
        state.optJSONObject("exercises")?.let { items -> items.keys().forEach { exerciseResults[it]=items.getBoolean(it) } }
        if(!state.has("mistakes")) exerciseResults.filterValues { !it }.forEach { (id,_) -> mistakes[id]="Нужно повторить" }
        val values = state.optJSONObject("statuses") ?: JSONObject()
        values.keys().forEach { id -> runCatching { CardStatus.valueOf(values.getString(id)) }.getOrNull()?.let { statuses[id] = it } }
        repetitions = state.optInt("repetitions", 1).coerceIn(1,5)
        thinkingSeconds = state.optInt("thinking",3).coerceIn(0,30)
        speed = state.optDouble("speed",1.0).toFloat().coerceIn(0.75f,1.5f)
        listening = state.optBoolean("listening",false); autoNext = state.optBoolean("autoNext",false)
        lastCard = state.optString("lastCard"); lastCategory = state.optString("lastCategory"); lastFilter = state.optString("lastFilter","ACTIVE")
    }
    fun status(id: String) = statuses[id] ?: CardStatus.NEW
    fun snapshot(): String = JSONObject().apply {
        put("exerciseDrafts",JSONObject(exerciseDrafts as Map<*,*>));put("schedules",schedulesJson());put("checklists",checklistsJson());put("checklistHints",org.json.JSONArray(checklistHints.sorted()));put("includeLearnedInDue",includeLearnedInDue);put("answerLevel",answerLevel)
        put("notes",JSONObject(notes as Map<*,*>));put("views",JSONObject(views as Map<*,*>));put("evidence",evidenceJson());put("routes",JSONObject(routeProgress as Map<*,*>))
        put("planDay",planDay);put("planInitialRemaining",planInitialRemaining);put("planCompleted",org.json.JSONArray(planCompleted.toList()))
        put("mistakes",JSONObject(mistakes as Map<*,*>));put("understanding",includeUnderstanding);put("interviewDate",interviewDate);put("dailyMinutes",dailyMinutes)
        put("exercises",JSONObject(exerciseResults as Map<*,*>)); put("statuses",JSONObject(statuses.mapValues { it.value.name })); put("repetitions",repetitions)
        put("thinking",thinkingSeconds); put("speed",speed); put("listening",listening); put("autoNext",autoNext)
        put("lastCard",lastCard); put("lastCategory",lastCategory); put("lastFilter",lastFilter)
    }.toString()
    suspend fun persist(snapshot: String) { context.interviewStore.edit { it[stateKey] = snapshot } }
    fun export(): String = JSONObject().apply {
        put("exerciseDrafts",JSONObject(exerciseDrafts as Map<*,*>));put("schedules",schedulesJson());put("checklists",checklistsJson());put("checklistHints",org.json.JSONArray(checklistHints.sorted()))
        put("notes",JSONObject(notes as Map<*,*>));put("views",JSONObject(views as Map<*,*>));put("evidence",evidenceJson());put("routes",JSONObject(routeProgress as Map<*,*>))
        put("exercises",JSONObject(exerciseResults as Map<*,*>))
        put("mistakes",JSONObject(mistakes as Map<*,*>))
        put("schema_version",1); put("deck_id","python-middle-ru")
        put("statuses",JSONObject(statuses.mapValues { it.value.name }))
    }.toString(2)
    /** Parse first, then apply atomically to the in-memory map. Malformed input changes nothing. */
    fun import(raw: String, knownIds: Set<String>): Pair<Int,Int> {
        val json = JSONObject(raw)
        require(json.getInt("schema_version")==1 && json.getString("deck_id")=="python-middle-ru") { "Это файл другого набора или версии" }
        val items = json.getJSONObject("statuses")
        val parsed = mutableMapOf<String, CardStatus>(); var unknown = 0
        items.keys().forEach { id ->
            val value = CardStatus.valueOf(items.getString(id))
            if (id in knownIds) parsed[id] = value else unknown++
        }
        val exerciseItems=json.optJSONObject("exercises")
        val parsedExercises=mutableMapOf<String,Boolean>()
        val tasks=JSONObject(context.assets.open("exercises.json").bufferedReader().use { it.readText() })
        val knownExercises=listOf("code","sql","cases").flatMap { key -> val a=tasks.optJSONArray(key) ?: org.json.JSONArray();(0 until a.length()).map { a.getJSONObject(it).getString("id") } }.toSet()
        val extraTasks=JSONObject(context.assets.open("learning_v5.json").bufferedReader().use { it.readText() })
        val knownV5=listOf("whatifs","comparisons").flatMap { key->val a=extraTasks.getJSONArray(key);(0 until a.length()).map { a.getJSONObject(it).getString("id") } }.toSet()
        val allExercises=knownExercises+knownV5
        exerciseItems?.keys()?.forEach { id -> val value=exerciseItems.getBoolean(id);if(id in allExercises) parsedExercises[id]=value else unknown++ }
        val parsedMistakes=mutableMapOf<String,String>()
        json.optJSONObject("mistakes")?.let { entries -> entries.keys().forEach { id -> val reason=entries.getString(id);require(reason.length<=500);if(id in knownIds || id in allExercises) parsedMistakes[id]=reason else unknown++ } }
        val parsedNotes=mutableMapOf<String,String>();val parsedViews=mutableMapOf<String,Int>();val parsedEvidence=mutableMapOf<String,AnswerEvidence>();val parsedRoutes=mutableMapOf<String,Int>()
        json.optJSONObject("notes")?.let { o ->o.keys().forEach { id ->val v=o.getString(id);require(v.length<=10000);if(id in knownIds) parsedNotes[id]=v else unknown++ } }
        json.optJSONObject("views")?.let { o ->o.keys().forEach { id ->val v=o.getInt(id);require(v in 0..1000000);if(id in knownIds) parsedViews[id]=v else unknown++ } }
        json.optJSONObject("evidence")?.let { o ->o.keys().forEach { id ->val e=o.getJSONObject(id);val value=AnswerEvidence(e.getInt("score"),e.getBoolean("assisted"),e.getString("source"),e.getLong("at"));require(value.source.length in 1..60 && value.at>=0);if(id in knownIds) parsedEvidence[id]=value else unknown++ } }
        val learning=JSONObject(context.assets.open("learning_v4.json").bufferedReader().use { it.readText() }).getJSONArray("chains")
        val knownRoutes=(0 until learning.length()).associate { val chain=learning.getJSONObject(it);chain.getString("id") to chain.getJSONArray("steps").length() }
        json.optJSONObject("routes")?.let { o ->o.keys().forEach { id ->val v=o.getInt(id);if(id in knownRoutes) { require(v in 0..knownRoutes.getValue(id));parsedRoutes[id]=v } else unknown++ } }
        val parsedSchedules=mutableMapOf<String,ReviewSchedule>();val parsedChecklists=mutableMapOf<String,Set<Int>>()
        val catalogue=JSONObject(context.assets.open("learning_v5.json").bufferedReader().use { it.readText() }).getJSONObject("cards")
        json.optJSONObject("schedules")?.let { o->o.keys().forEach { id->val v=o.getJSONObject(id);val schedule=ReviewSchedule(v.getLong("due"),v.getInt("streak"),v.getLong("last"));if(id in knownIds) parsedSchedules[id]=schedule else unknown++ } }
        json.optJSONObject("checklists")?.let { o->o.keys().forEach { id->val a=o.getJSONArray(id);require(a.length()<=5);val marks=(0 until a.length()).map { a.getInt(it) }.toSet();require(marks.all { it in 0..4 });if(id in knownIds) { require(marks.all { it<catalogue.getJSONObject(id).getJSONArray("checklist").length() });parsedChecklists[id]=marks } else unknown++ } }
        val parsedChecklistHints=mutableSetOf<String>()
        json.optJSONArray("checklistHints")?.let { a->require(a.length()<=155);for(i in 0 until a.length()) {val id=a.getString(i);if(id in knownIds) parsedChecklistHints.add(id) else unknown++} }
        val parsedDrafts=mutableMapOf<String,String>()
        json.optJSONObject("exerciseDrafts")?.let { o->o.keys().forEach { id->val text=o.getString(id);require(text.length<=10000);if(id in allExercises) parsedDrafts[id]=text else unknown++ } }
        checklistHints.addAll(parsedChecklistHints)
        exerciseDrafts.putAll(parsedDrafts)
        schedules.putAll(parsedSchedules);checklistMarks.putAll(parsedChecklists)
        notes.putAll(parsedNotes);views.putAll(parsedViews);evidence.putAll(parsedEvidence);routeProgress.putAll(parsedRoutes)
        statuses.putAll(parsed);exerciseResults.putAll(parsedExercises);mistakes.putAll(parsedMistakes)
        return parsed.size to unknown
    }
}
