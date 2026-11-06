package com.python.interviewcards

import android.content.*
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.*
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.json.JSONObject
import org.json.JSONArray

class V5Activity:AppCompatActivity() {
    private val bg=Color.rgb(16,18,24);private val ink=Color.rgb(244,242,250);private val muted=Color.rgb(163,168,187);private val accent=Color.rgb(184,160,255);private val surface=Color.rgb(27,30,40)
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main);private val saves=Channel<String>(Channel.UNLIMITED)
    private lateinit var root:LinearLayout;private lateinit var store:ProgressStore;private lateinit var repo:CardRepository;private lateinit var audio:AudioController;private lateinit var content:LearningV5
    private lateinit var checkpoint:Checkpoint;private var draftDirty=false;private val ticks=CheckpointTicker { saveCheckpoint();flushDrafts() }
    private var mode="MENU";private var index=0;private var draft="";private var opened=false;private var assisted=false;private var ids=listOf<String>();private var completed=0
    private var drafts=JSONObject();private var draftField:EditText?=null;private var scroll:ScrollView?=null
    private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
    private fun col()=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
    private fun LinearLayout.add(v:View) { addView(v,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(12) }) }
    private fun label(s:String,size:Int=16,color:Int=ink)=TextView(this).apply { text=s;textSize=size.toFloat();setTextColor(color);setLineSpacing(dp(3).toFloat(),1f) }
    private fun button(s:String,primary:Boolean=false,action:()->Unit)=MaterialButton(this).apply { text=s;isAllCaps=false;minHeight=dp(48);setPadding(dp(12),dp(8),dp(12),dp(8));backgroundTintList=ColorStateList.valueOf(if(primary) accent else surface);setTextColor(if(primary) bg else ink);setOnClickListener { action() } }
    private fun box()=col().apply { setPadding(dp(16),dp(12),dp(16),dp(16));background=GradientDrawable().apply { setColor(surface);cornerRadius=dp(20).toFloat() } }
    private fun code(s:String)=HorizontalScrollView(this).apply { addView(label(s,14,Color.rgb(134,232,197)).apply { typeface=Typeface.MONOSPACE;setTextIsSelectable(true);setPadding(dp(12),dp(12),dp(12),dp(12)) }) }
    private fun page(title:String,subtitle:String=""):LinearLayout {
        draftField=null;root.removeAllViews()
        val bar=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL;setPadding(dp(20),dp(8),dp(20),dp(8)) }
        bar.addView(button("‹") { back() }.apply { minWidth=0;minimumWidth=0;setPadding(0,0,0,0);textSize=24f;contentDescription="Назад" },LinearLayout.LayoutParams(dp(48),dp(48)))
        bar.addView(col().apply { add(label(title,22));if(subtitle.isNotBlank()) add(label(subtitle,13,muted)) },LinearLayout.LayoutParams(0,-2,1f).apply { marginStart=dp(12) });root.addView(bar)
        val c=col().apply { setPadding(dp(20),0,dp(20),dp(28)) };scroll=ScrollView(this).apply { addView(c) };root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f));return c
    }
    private fun persist() { saves.trySend(store.snapshot());saveCheckpoint() }
    override fun onCreate(saved:Bundle?) {
        super.onCreate(saved);WindowCompat.setDecorFitsSystemWindows(window,false);root=col().apply { setBackgroundColor(bg) };setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v,i->val b=i.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime());v.setPadding(b.left,b.top,b.right,b.bottom);i }
        checkpoint=Checkpoint(this);PlaybackService.command(this,PlaybackService.STOP)
        store=ProgressStore(applicationContext);val writer=CoroutineScope(SupervisorJob()+Dispatchers.IO);writer.launch { for(s in saves) runCatching { store.persist(s) };writer.cancel() }
        audio=AudioController(this,{_,_->},{},{Toast.makeText(this,it,Toast.LENGTH_LONG).show()})
        onBackPressedDispatcher.addCallback(this,object:OnBackPressedCallback(true) { override fun handleOnBackPressed() { back() } })
        scope.launch {
            withContext(Dispatchers.IO) { store.load();repo=CardRepository(this@V5Activity);content=LearningV5(this@V5Activity) }
            val savedState=checkpoint.read()?.takeIf { it.optString("owner")=="advanced" }?.optJSONObject("payload")
            if((intent.getBooleanExtra("resume",false) || saved!=null) && savedState!=null) restore(savedState) else when(intent.getStringExtra("page")) { "DUE"->duePage();"WHATIF"->{mode="WHATIF";render()};"COMPARE"->{mode="COMPARE_LIST";comparisonList()};else->menu() }
            intent.getStringExtra("task")?.let { id->
                val key=if(id.startsWith("whatif-")) "whatifs" else "comparisons";val a=content.data.getJSONArray(key)
                index=(0 until a.length()).firstOrNull { a.getJSONObject(it).getString("id")==id } ?: 0;mode=if(key=="whatifs") "WHATIF" else "COMPARE";opened=false;assisted=false;draft="";drafts.remove(id);store.exerciseDrafts.remove(id);persist();render()
            }
            ticks.start()
        }
    }
    private fun menu() { audio.stop();mode="MENU";val c=page("Понять и закрепить");c.add(button("Повторить сегодня",true) { duePage() });c.add(button("А если изменить условие?") { mode="WHATIF";index=0;draft="";opened=false;render() });c.add(button("Сравнить понятия") { mode="COMPARE_LIST";comparisonList() }) }
    private fun duePage() {
        audio.stop();mode="DUE_LIST";val now=System.currentTimeMillis();val learned=store.statuses.filterValues { it==CardStatus.LEARNED }.keys;val hard=store.statuses.filterValues { it==CardStatus.HARD }.keys
        val due=SpacedReview.due(repo.cards.map { it.id },learned,hard,store.schedules,store.includeLearnedInDue,now)
        val c=page("Повторить сегодня","Самостоятельный ответ определяет следующий срок")
        c.add(label("Готово к повторению: ${due.size}",24));c.add(label("До 20 вопросов за занятие. Ошибка → 10 минут; частично → день; самостоятельный ответ → 1, 3, 7, 14, 30 дней.",15,muted))
        val toggle=SwitchMaterial(this).apply { text="Включить запомненные в добровольную проверку";setTextColor(ink);isChecked=store.includeLearnedInDue }
        toggle.setOnCheckedChangeListener { _,value->
            if(value) MaterialAlertDialogBuilder(this).setTitle("Проверять запомненные по расписанию?").setMessage("Только в этом режиме. Учебные отметки и обычная очередь не изменятся.").setPositiveButton("Включить") { _,_->store.includeLearnedInDue=true;persist();duePage() }.setNegativeButton("Отмена") { _,_->toggle.setOnCheckedChangeListener(null);toggle.isChecked=false;duePage() }.setOnCancelListener { duePage() }.show()
            else { store.includeLearnedInDue=false;persist();duePage() }
        };c.add(toggle)
        if(due.isNotEmpty()) c.add(button("Начать повторение · ${minOf(20,due.size)}",true) { ids=due.take(20);index=0;completed=0;opened=false;mode="DUE";render() })
        else c.add(label("На сегодня всё. Можно выбрать другой тренажёр.",18))
        val next=repo.cards.filter { store.includeLearnedInDue || store.status(it.id)!=CardStatus.LEARNED }.mapNotNull { store.schedules[it.id]?.dueAt }.filter { it>now }.minOrNull()
        if(next!=null) c.add(label("Ближайший повтор: "+java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT,java.text.DateFormat.SHORT).format(java.util.Date(next)),15,muted))
        c.add(button("Сравнить понятия") { mode="COMPARE_LIST";comparisonList() });c.add(button("А если изменить условие?") { mode="WHATIF";index=0;opened=false;draft="";render() })
    }
    private fun render() {
        if(mode=="DUE") dueCard() else if(mode=="WHATIF") whatif() else if(mode=="COMPARE") comparison() else menu()
        saveCheckpoint()
    }
    private fun dueCard() {
        while(!store.includeLearnedInDue && ids.getOrNull(index)?.let { store.status(it)==CardStatus.LEARNED }==true) index++
        val id=ids.getOrNull(index)
        if(id==null) { checkpoint.clear("advanced");mode="DUE_SUMMARY";val c=page("Повторение завершено");c.add(label("Проверено: $completed из ${ids.size}",24));c.add(button("К расписанию",true) { duePage() });return }
        val card=repo.cards.first { it.id==id };val c=page("Повторение","${index+1} из ${ids.size} · ${card.category}");c.add(label(card.question,18))
        c.add(button("▷ Озвучить вопрос") { audio.single(card,false,store.speed) });c.add(AudioSeekView(this,audio,true))
        if(!opened) c.add(button("Я ответил — открыть образец",true) { audio.stop();opened=true;render() })
        else {
            c.add(box().apply { add(V5CardView(this@V5Activity,card,store,audio,{persist()},{audio.stop()}));add(code(card.example)) })
            c.add(label("Оцените ответ до открытия образца. Учебная отметка останется прежней.",14,muted))
            listOf(Triple("Ответил самостоятельно",2,false),Triple("Частично",1,false),Triple("С подсказкой",1,true),Triple("Не ответил",0,false)).forEach { (title,score,hint)->c.add(button(title,score==2) { store.assess(id,score,hint,"scheduled");if(score<2) store.mistakes[id]=title else store.mistakes.remove(id);completed++;audio.stop();index++;opened=false;persist();render() }) }
        }
        c.add(button("К расписанию") { audio.stop();duePage() })
    }
    private fun draftEditor(c:LinearLayout,hint:String,key:String) {
        draft=store.exerciseDrafts[key] ?: drafts.optString(key,draft)
        draftField=EditText(this).apply { this.hint=hint;setTextColor(ink);setHintTextColor(muted);minLines=2;setText(draft);filters=arrayOf(android.text.InputFilter.LengthFilter(10000))
            addTextChangedListener(object:android.text.TextWatcher { override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {};override fun afterTextChanged(s:android.text.Editable?) {};override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) { draft=s.toString();drafts.put(key,draft);store.exerciseDrafts[key]=draft;draftDirty=true;saveCheckpoint() } }) }
        c.add(draftField!!)
    }
    private fun whatif() {
        val items=content.data.getJSONArray("whatifs");index=index.coerceIn(0,items.length()-1);val t=items.getJSONObject(index);val id=t.getString("id")
        val c=page("А если изменить условие?","${index+1} из ${items.length()}");c.add(label(t.getString("title"),20));c.add(label("Исходное условие",13,accent));c.add(label(t.getString("base")));c.add(label("Что изменилось",13,accent));c.add(label(t.getString("change"),18))
        if(t.getString("code").isNotBlank()) c.add(code(t.getString("code")))
        draftEditor(c,"Ваш прогноз и почему",id)
        c.add(button("Проверить прогноз",true) { if(draft.isBlank()) Toast.makeText(this,"Введите прогноз или выберите «Не знаю»",Toast.LENGTH_SHORT).show() else { opened=true;render() } })
        c.add(button("Не знаю — разобрать") { assisted=true;opened=true;store.mistakes[id]="Потребовался разбор изменённого условия";persist();render() })
        if(opened) c.add(box().apply {
            add(label("Результат",13,accent));add(code(t.getString("answer").ifBlank { "Пустой результат: 0 строк" }));add(label(t.getString("explanation")))
            add(button("▷ Озвучить разбор") { audio.extra(t.getString("audio"),"Изменение условия",store.speed) });add(AudioSeekView(this@V5Activity,audio,true))
            add(button("Предсказал и объяснил верно",true) { store.exerciseResults[id]=true;if(!assisted) store.mistakes.remove(id);persist();nextExercise() })
            add(button("Нужно повторить") { store.exerciseResults[id]=false;store.mistakes[id]="Ошибка при изменении условия";persist();nextExercise() })
        })
        c.add(button("Следующий пример") { nextExercise() })
    }
    private fun nextExercise() { audio.stop();draft="";opened=false;assisted=false;index++;if(index>=content.data.getJSONArray("whatifs").length()) { checkpoint.clear("advanced");mode="MENU";val c=page("Все изменения разобраны");c.add(button("К тренажёрам",true) { menu() }) } else render() }
    private fun comparisonList() {
        audio.stop();mode="COMPARE_LIST";val c=page("Сравнить понятия","Различие → выбор → ловушка");val a=content.data.getJSONArray("comparisons")
        for(i in 0 until a.length()) c.add(button(a.getJSONObject(i).getString("title")) { index=i;mode="COMPARE";draft="";opened=false;render() })
    }
    private fun comparison() {
        val a=content.data.getJSONArray("comparisons");index=index.coerceIn(0,a.length()-1);val t=a.getJSONObject(index);val id=t.getString("id");val c=page(t.getString("title"),"${index+1} из ${a.length()}")
        c.add(box().apply { add(label(t.getString("left"),18));add(label(t.getString("right"),18)) });c.add(label("Как выбрать",13,accent));c.add(label(t.getString("choice")));c.add(code(t.getString("example")));c.add(label("Ловушка: "+t.getString("trap"),16,accent))
        c.add(button("▷ Озвучить сравнение") { audio.extra(t.getString("audio"),"Сравнение понятий",store.speed) });c.add(AudioSeekView(this,audio,true));c.add(label(t.getString("question"),18));draftEditor(c,"Какой вариант и почему?",id)
        c.add(button("Сверить выбор",true) { if(draft.isBlank()) Toast.makeText(this,"Сначала сформулируйте выбор",Toast.LENGTH_SHORT).show() else {opened=true;render()} })
        if(opened) { c.add(label(t.getString("answer")));c.add(button("Объяснил верно") { store.exerciseResults[id]=true;store.mistakes.remove(id);persist();Toast.makeText(this,"Отметка сохранена",Toast.LENGTH_SHORT).show() });c.add(button("Нужно повторить") { store.exerciseResults[id]=false;store.mistakes[id]="Нужно различать: "+t.getString("title");persist() }) }
        val related=t.getJSONArray("card_ids")
        for(i in 0 until related.length()) { val card=repo.cards.first { it.id==related.getString(i) };c.add(button("Карточка: "+card.question) { audio.stop();startActivity(Intent(this,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtra("viewCard",card.id)) }) }
        c.add(button("Завершить разбор") { audio.stop();checkpoint.clear("advanced");comparisonList() })
        c.add(button("К списку сравнений") { audio.stop();comparisonList() })
    }
    private fun flushDrafts() { if(draftDirty && ::store.isInitialized) { saves.trySend(store.snapshot());draftDirty=false } }
    private fun saveCheckpoint() {
        if(!::checkpoint.isInitialized || !::audio.isInitialized || mode !in setOf("DUE","WHATIF","COMPARE")) return
        val p=JSONObject().put("mode",mode).put("index",index).put("draft",draft).put("drafts",drafts).put("opened",opened).put("assisted",assisted).put("completed",completed).put("ids",JSONArray(ids)).put("scroll",scroll?.scrollY ?: 0).put("audio",audio.snapshot())
        checkpoint.save("advanced",when(mode) { "DUE"->"Повторение по расписанию";"WHATIF"->"Изменение условия";else->"Сравнение понятий" },p)
    }
    private fun restore(j:JSONObject) {
        runCatching {
            mode=j.getString("mode");require(mode in setOf("DUE","WHATIF","COMPARE"));index=j.getInt("index");draft=j.optString("draft");drafts=j.optJSONObject("drafts") ?: JSONObject();opened=j.optBoolean("opened");assisted=j.optBoolean("assisted");completed=j.optInt("completed")
            val a=j.getJSONArray("ids");ids=(0 until a.length()).map { a.getString(it) };require(ids.all { id->repo.cards.any { it.id==id } })
            render();j.optJSONObject("audio")?.let { audio.restore(it,repo.cards) };scroll?.post { scroll?.scrollTo(0,j.optInt("scroll")) }
        }.onFailure { checkpoint.clear("advanced");menu() }
    }
    private fun back() { saveCheckpoint();if(mode=="MENU" || mode=="DUE_LIST" || mode=="COMPARE_LIST") finish() else { audio.stop();menu() } }
    override fun onStart() { super.onStart();if(::audio.isInitialized) ticks.start() }
    override fun onStop() { if(::audio.isInitialized) {audio.pause();saveCheckpoint();flushDrafts()};ticks.stop();super.onStop() }
    override fun onDestroy() { ticks.stop();if(::audio.isInitialized) audio.close();saves.close();scope.cancel();super.onDestroy() }
}
