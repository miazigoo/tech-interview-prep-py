package com.python.interviewcards

import android.content.Context
import org.json.JSONObject
import kotlin.random.Random

enum class CardStatus { NEW, HARD, LEARNED }
data class Card(val id: String, val number: Int, val categoryId: String, val category: String,
    val question: String, val answer: String, val example: String, val note: String,
    val check: String, val questionAudio: String, val answerAudio: String,
    val plain: String="", val analogy: String="", val mistake: String="", val followUp: String="",val explanationAudio:String="",val analogyAudio:String="")

class CardRepository(context: Context) {
    val cards: List<Card>
    init {
        val array = JSONObject(context.assets.open("cards.json").bufferedReader().use { it.readText() }).getJSONArray("cards")
        cards = (0 until array.length()).map { i -> array.getJSONObject(i).let { c ->
            Card(c.getString("id"), c.getInt("source_number"), c.getString("category_id"), c.getString("category"),
                c.getString("question"), c.getString("answer"), c.getString("example"), c.getString("example_note"),
                c.getString("self_check"), c.getString("question_audio"), c.getString("answer_audio"),c.optString("plain_explanation"),c.optString("analogy"),c.optString("common_mistake"),c.optString("follow_up"),c.optString("explanation_audio"),c.optString("analogy_audio"))
        } }
    }
}

data class QueueState(val counts:Map<String,Int>,val consumed:Map<String,Int>,val past:List<String>,val current:String?,val shown:Int,val total:Int)

/** Exact round quotas; high remaining counts first avoid adjacent duplicates whenever possible. */
class LearningQueue(private val random: Random = Random.Default) {
    private val counts = linkedMapOf<String, Int>()
    private val consumed = mutableMapOf<String, Int>()
    private val past = mutableListOf<String>()
    var current: String? = null; private set
    var shown = 0; private set
    var total = 0; private set
    fun begin(weights: Map<String, Int>, preferred: String? = null, avoid: String? = null): String? {
        counts.clear(); counts.putAll(weights.filterValues { it > 0 }); consumed.clear(); past.clear()
        shown = 0; total = counts.values.sum(); current = null
        return take(preferred, avoid)
    }
    private fun take(preferred: String?, avoid: String?): String? {
        val possible = counts.filterValues { it > 0 }.keys.toList()
        if (possible.isEmpty()) { current = null; return null }
        val alternatives = possible.filter { it != avoid }.ifEmpty { possible }
        val id = preferred?.takeIf { it in possible } ?: alternatives.shuffled(random).maxBy { counts.getValue(it) }
        counts[id] = counts.getValue(id) - 1
        consumed[id] = consumed.getOrDefault(id, 0) + 1
        current = id; shown++
        return id
    }
    fun next(): String? { val previous = current; previous?.let { past.add(it) }; return take(null, previous) }
    fun previous(eligible: Set<String>): String? {
        val index = past.indexOfLast { it in eligible }
        if (index < 0) return current
        current?.takeIf { it in eligible }?.let { counts[it] = counts.getOrDefault(it, 0) + 1; consumed[it] = (consumed.getOrDefault(it, 1) - 1).coerceAtLeast(0) }
        val id = past[index]
        past.subList(index, past.size).clear()
        // A history visit is a visit, not an additional quota item.
        consumed[id] = (consumed.getOrDefault(id, 1) - 1).coerceAtLeast(0)
        counts[id] = counts.getOrDefault(id, 0) + 1
        shown = (shown - 2).coerceAtLeast(0)
        return take(id, null)
    }
    fun state()=QueueState(counts.toMap(),consumed.toMap(),past.toList(),current,shown,total)
    fun restore(s:QueueState,eligible:Set<String>):Boolean {
        if(s.current !in eligible || s.shown<1 || s.total<s.shown || s.counts.values.any { it<0 || it>3 } || s.consumed.values.any { it<0 || it>3 }) return false
        counts.clear();counts.putAll(s.counts.filterKeys { it in eligible });consumed.clear();consumed.putAll(s.consumed.filterKeys { it in eligible })
        past.clear();past.addAll(s.past.filter { it in eligible });current=s.current;shown=s.shown;total=shown+counts.values.sum();return true
    }
    fun update(id: String, weight: Int) {
        counts[id] = (weight - consumed.getOrDefault(id, 0)).coerceAtLeast(0)
        total = shown + counts.values.sum()
    }
}
