package com.python.interviewcards

import android.content.Context
import org.json.JSONObject
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.View
import android.widget.*
import com.google.android.material.button.MaterialButton

class LearningV5(context:Context) {
    companion object { private var cached:JSONObject?=null }
    val data:JSONObject=cached ?: JSONObject(context.assets.open("learning_v5.json").bufferedReader().use { it.readText() }).also { cached=it }
    fun card(id:String):JSONObject=data.getJSONObject("cards").getJSONObject(id)
}
/** Shared answer levels and explicit checklist self-assessment. No status changes or automatic grading. */
class V5CardView(context:Context,card:Card,store:ProgressStore,audio:AudioController,persist:()->Unit,beforeAudio:()->Unit):LinearLayout(context) {
    private val ink=Color.rgb(244,242,250);private val accent=Color.rgb(184,160,255);private val muted=Color.rgb(163,168,187)
    private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
    private fun label(s:String,size:Int=16)=TextView(context).apply { text=s;textSize=size.toFloat();setTextColor(ink);setLineSpacing(dp(3).toFloat(),1f) }
    private fun button(s:String,action:()->Unit)=MaterialButton(context).apply { text=s;isAllCaps=false;textSize=14f;minHeight=dp(48);setPadding(dp(8),dp(8),dp(8),dp(8));backgroundTintList=ColorStateList.valueOf(Color.rgb(39,32,58));setTextColor(accent);setOnClickListener { action() } }
    private fun add(v:View) { addView(v,LayoutParams(-1,-2).apply { topMargin=dp(8) }) }
    init {
        orientation=VERTICAL
        val item=LearningV5(context).card(card.id)
        val body=label("",18);val duration=label("",12).apply { setTextColor(muted) }
        val cues=JSONObject(context.assets.open("audio_cues.json").bufferedReader().use { it.readText() })
        fun refresh() {
            body.text=item.getString(if(store.answerLevel=="detailed") "detailed" else "short")
            val path=item.getString(if(store.answerLevel=="detailed") "detailed_audio" else "short_audio")
            val seconds=(cues.optJSONObject(path)?.optInt("duration_ms",0) ?: 0)/1000
            duration.text=(if(store.answerLevel=="detailed") "Подробный разбор" else "Короткий ответ")+(if(seconds>0) " · ${seconds/60}:${(seconds%60).toString().padStart(2,'0')}" else "")
        }
        val levels=LinearLayout(context)
        listOf("short" to "Коротко","detailed" to "Подробно").forEach { (key,title)->levels.addView(button(title) { store.answerLevel=key;refresh();persist() },LayoutParams(0,-2,1f)) }
        add(levels);add(duration);add(body)
        add(button("▷ Озвучить ответ") { beforeAudio();audio.extra(item.getString(if(store.answerLevel=="detailed") "detailed_audio" else "short_audio"),if(store.answerLevel=="detailed") "Подробный ответ" else "Короткий ответ",store.speed) })
        val checklist=LinearLayout(context).apply { orientation=VERTICAL;visibility=GONE }
        val criteria=item.getJSONArray("checklist");val marks=(store.checklistMarks[card.id] ?: emptySet()).toMutableSet()
        checklist.addView(label("Отметьте, что сказали сами, затем сохраните самооценку. Галочки сами по себе не меняют готовность.",14))
        for(i in 0 until criteria.length()) {
            checklist.addView(CheckBox(context).apply { text=criteria.getString(i);textSize=15f;setTextColor(ink);isChecked=i in marks;buttonTintList=ColorStateList.valueOf(accent);setOnCheckedChangeListener { _,checked->if(checked) marks.add(i) else marks.remove(i);store.checklistMarks[card.id]=marks.toSet();persist() } })
        }
        val hint=CheckBox(context).apply { text="Понадобилась подсказка";isChecked=card.id in store.checklistHints;setOnCheckedChangeListener { _,checked->if(checked) store.checklistHints.add(card.id) else store.checklistHints.remove(card.id);persist() };setTextColor(ink);buttonTintList=ColorStateList.valueOf(accent) };checklist.addView(hint)
        checklist.addView(button("Сохранить самооценку по чек-листу") {
            val score=when { marks.isEmpty()->0;marks.size==criteria.length() && !hint.isChecked->2;else->1 }
            store.assess(card.id,score,hint.isChecked,"checklist")
            if(score==2) store.mistakes.remove(card.id) else store.mistakes[card.id]=(if(hint.isChecked) "Потребовалась подсказка. " else "")+(0 until criteria.length()).filter { it !in marks }.joinToString("; ") { criteria.getString(it) }.take(430)
            persist();Toast.makeText(context,"Самооценка и дата повторения сохранены",Toast.LENGTH_SHORT).show()
        })
        add(button("Чек-лист хорошего ответа") { checklist.visibility=if(checklist.visibility==VISIBLE) GONE else VISIBLE });add(checklist);refresh()
    }
}
