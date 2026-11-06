package com.python.interviewcards

import android.content.*
import android.app.DatePickerDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.*
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.json.JSONObject

class PracticeActivity: AppCompatActivity() {
    private val bg=Color.rgb(16,18,24);private val surface=Color.rgb(27,30,40);private val ink=Color.rgb(244,242,250)
    private val muted=Color.rgb(163,168,187);private val accent=Color.rgb(184,160,255);private val mint=Color.rgb(134,232,197)
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
    private val saves=Channel<String>(Channel.UNLIMITED)
    private lateinit var root: LinearLayout
    private lateinit var store: ProgressStore
    private lateinit var repo: CardRepository
    private lateinit var audio: AudioController
    private lateinit var recording:RecordingUi
    private lateinit var exercises: JSONObject
    private lateinit var learning:JSONObject
    private var route:JSONObject?=null
    private var routeIndex=0
    private var sqlEngine: SqlTrainer?=null
    private var session: LearningSession?=null
    private var mode="MENU"
    private var retryOnly=false
    private var assisted=false
    private var caseIndex=0
    private var ticker: Job?=null
    private var countdown: Job?=null
    private var remaining=0L
    private var countdownStarted=0L
    private var answerSeconds=60
    private var answerVisible=false
    private var paused=false
    private var timerText: TextView?=null
    private var answerPanel: LinearLayout?=null
    private var exerciseIndex=0
    private lateinit var checkpoint:Checkpoint
    private val checkpointTicks=CheckpointTicker { savePractice() }
    private var drafts=JSONObject()
    private val hintLevels=mutableMapOf<String,Int>()
    private val openedExercises=mutableSetOf<String>()
    private var restoringHints=false
    private var pageScroll:ScrollView?=null
    private var pageGeneration=0
    private var sqlJob: Job?=null
    private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
    private fun col()=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
    private fun row()=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL }
    private fun LinearLayout.add(v:View,top:Int=0) { addView(v,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(top) }) }
    private fun label(s:String,size:Int=16,color:Int=ink)=TextView(this).apply { text=s;textSize=size.toFloat();setTextColor(color);setLineSpacing(dp(3).toFloat(),1f) }
    private fun box()=col().apply { setPadding(dp(18),dp(18),dp(18),dp(18));background=GradientDrawable().apply { setColor(surface);cornerRadius=dp(20).toFloat() } }
    private fun button(s:String,primary:Boolean=false,action:()->Unit)=MaterialButton(this).apply {
        text=s;isAllCaps=false;textSize=14f;minHeight=dp(48);insetTop=0;insetBottom=0;cornerRadius=dp(14)
        backgroundTintList=ColorStateList.valueOf(if(primary) accent else surface);setTextColor(if(primary) bg else ink);setOnClickListener { action() }
    }
    private fun page(title:String,subtitle:String=""):LinearLayout {
        if(::recording.isInitialized) recording.stop()
        pageGeneration++;sqlJob?.cancel();root.removeAllViews();timerText=null;answerPanel=null
        val top=row().apply { setPadding(dp(20),dp(12),dp(20),dp(12)) }
        top.addView(button("‹") { back() }.apply { minWidth=0;minimumWidth=0;setPadding(0,0,0,0);textSize=24f;contentDescription="Назад" },LinearLayout.LayoutParams(dp(48),dp(48)))
        top.addView(col().apply { add(label(title,23));if(subtitle.isNotEmpty()) add(label(subtitle,12,muted)) },LinearLayout.LayoutParams(0,-2,1f).apply { marginStart=dp(12) });root.addView(top)
        val content=col().apply { setPadding(dp(20),dp(8),dp(20),dp(30)) }
        pageScroll=ScrollView(this).apply { addView(content) };root.addView(pageScroll,LinearLayout.LayoutParams(-1,0,1f));return content
    }
    private fun code(s:String)=HorizontalScrollView(this).apply { addView(label(s,15,mint).apply { typeface=Typeface.MONOSPACE;setTextIsSelectable(true);setPadding(dp(16),dp(16),dp(16),dp(16)) });background=GradientDrawable().apply { setColor(bg);cornerRadius=dp(12).toFloat() } }
    private fun persist() { saves.trySend(store.snapshot()) }
    override fun onCreate(saved:Bundle?) {
        super.onCreate(saved)
        onBackPressedDispatcher.addCallback(this,object: androidx.activity.OnBackPressedCallback(true) { override fun handleOnBackPressed() { back() } })
        WindowCompat.setDecorFitsSystemWindows(window,false)
        root=col().apply { setBackgroundColor(bg) };setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v,i -> val b=i.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime());v.setPadding(b.left,b.top,b.right,b.bottom);i }
        PlaybackService.command(this,PlaybackService.STOP)
        checkpoint=Checkpoint(this);store=ProgressStore(applicationContext)
        val writer=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        writer.launch { for(s in saves) runCatching { store.persist(s) };writer.cancel() }
        audio=AudioController(this,{ _,_-> },{}, { toast(it);if(mode=="INTERVIEW" && !answerVisible) startCountdown() },trackCompleted={ path ->
            if(mode=="INTERVIEW" && !answerVisible && path==currentCard()?.questionAudio) startCountdown()
        })
        recording=RecordingUi(this) { audio.stop();if(mode=="INTERVIEW" && !answerVisible) startCountdown() }
        scope.launch {
            withContext(Dispatchers.IO) {
                repo=CardRepository(this@PracticeActivity);store.load()
                exercises=JSONObject(assets.open("exercises.json").bufferedReader().use { it.readText() })
                learning=JSONObject(assets.open("learning_v4.json").bufferedReader().use { it.readText() })
            }
            val savedCheckpoint=checkpoint.read()?.takeIf { it.optString("owner")=="practice" }?.optJSONObject("payload")
            if((intent.getBooleanExtra("resume",false) || saved!=null) && savedCheckpoint!=null) restorePractice(savedCheckpoint)
            else when(intent.getStringExtra("page")) { "STATS"->readinessPage();"CHAINS"->chainsPage();else->menu() }
            checkpointTicks.start()
        }
    }
    private fun menu() {
        stopTimers();audio.stop();session=null;mode="MENU";retryOnly=false
        val c=page("Режимы и тренажёры","Практика, самопроверка и короткие занятия")
        checkpoint.read()?.takeIf { it.optString("owner")=="practice" }?.optJSONObject("payload")?.let { saved->c.add(button("Продолжить сохранённое занятие",true) { restorePractice(saved) }) }
        c.add(button("Повторить сегодня") { openAdvanced("DUE") })
        c.add(button("А если изменить условие?") { openAdvanced("WHATIF") })
        c.add(button("Сравнить понятия") { openAdvanced("COMPARE") })
        c.add(button("Мои ошибки · ${store.mistakes.size}",true) { mistakesPage() })
        c.add(button("Готовность по темам") { readinessPage() },10)
        c.add(button("Учебные цепочки") { chainsPage() },10)
        c.add(button("План подготовки к интервью") { planPage() },10)
        c.add(button("Практические кейсы Middle") { mode="CASES";caseIndex=0;casePage() },10)
        c.add(box().apply { add(label("Собеседование",23));add(label("10 вопросов. Сначала ответьте вслух, затем сравните с образцом. Самооценка не меняет отметки карточек.",15,muted),10)
            add(button("Начать собеседование",true) {
                MaterialAlertDialogBuilder(this@PracticeActivity).setTitle("Время на ответ").setItems(arrayOf("30 секунд","60 секунд","90 секунд")) { _,i -> answerSeconds=listOf(30,60,90)[i];begin("INTERVIEW") }.show()
            },14) })
        c.add(box().apply { add(label("Короткое занятие",23));add(button("10 минут",true) { begin("TIMED") },14);add(button("20 карточек") { begin("TWENTY") },10);add(button("Только сложные") { begin("HARD") },10) },14)
        c.add(box().apply { add(label("Проверка запомненного",23));add(label("Отдельная проверка. Выученные карточки останутся выученными, пока вы сами не вернёте их.",15,muted),10);add(button("Проверить запомненное") { begin("REVIEW") },14) },14)
        c.add(box().apply { add(label("Что выведет код?",23));add(label("16 задач · результат и объяснение после вашего предположения",15,muted),10);add(button("Решать Python-задачи",true) { mode="CODE";exerciseIndex=0;codePage() },14) },14)
        c.add(box().apply { add(label("SQL тренажёр",23));add(label("15 задач · SQLite · настоящие запросы к учебным таблицам",15,muted),10);add(button("Тренироваться в SQL",true) {
            mode="SQL";exerciseIndex=0
            scope.launch { sqlEngine=withContext(Dispatchers.IO) { SqlTrainer(this@PracticeActivity) };sqlPage() }
        },14) },14)
        c.add(label("Решено задач: ${store.exerciseResults.count { it.value }}. Результаты сохраняются на устройстве.",13,muted),18)
    }
    private fun begin(kind:String) {
        mode=kind;paused=false
        val candidates=repo.cards.filter { when(kind) { "REVIEW"->store.status(it.id)==CardStatus.LEARNED;"HARD"->store.status(it.id)==CardStatus.HARD;else->store.status(it.id)!=CardStatus.LEARNED } }.shuffled()
        val ids=(if(kind=="INTERVIEW") candidates.take(10) else if(kind=="TIMED") candidates else candidates.take(20)).map { it.id }
        session=LearningSession(when(kind) { "INTERVIEW"->"Собеседование";"REVIEW"->"Проверка запомненного";"TIMED"->"10 минут";"HARD"->"Только сложные";else->"20 карточек" },ids,if(kind=="TIMED") 600000 else 0)
        session!!.start(SystemClock.elapsedRealtime());startTicker();sessionPage()
    }
    private fun currentCard()=session?.current()?.let { id -> repo.cards.find { it.id==id } }
    private fun sessionPage(restoring:Boolean=false) {
        countdown?.cancel();countdownStarted=0;audio.stop();answerVisible=false
        val s=session ?: return
        while(mode!="REVIEW" && s.current()?.let { store.status(it)==CardStatus.LEARNED }==true) s.next()
        val card=currentCard()
        if(card==null || s.expired()) { summary();return }
        s.visit();store.viewed(card.id);persist()
        val c=page(s.name,"${s.index+1} из ${s.ids.size} · ${card.category}")
        timerText=label("",16,accent);c.add(timerText!!)
        c.add(button("Продолжить после паузы") { resumeSession() },8)
        c.add(label(card.question,18),14)
        c.add(recording.panel(card.id),10)
        c.add(button("Моя заметка") { NoteEditor.show(this,card.id,store) { persist() } },8)
        c.add(AudioSeekView(this,audio,true),8)
        c.add(button("▷ Озвучить вопрос") { recording.stop();audio.single(card,false,store.speed) },14)
        val revealButton=button(if(mode=="INTERVIEW") "Открыть ответ досрочно" else "Показать ответ и пример",true) { revealAnswer() };c.add(revealButton,16)
        answerPanel=col().apply {
            visibility=View.GONE
            add(box().apply { add(label("Как ответить",13,mint));add(V5CardView(this@PracticeActivity,card,store,audio,{persist()},{recording.stop()}),12) },18)
            add(box().apply { add(label("Пример и разбор",13,accent));add(code(card.example),12);add(label(card.note,16,muted),12);add(label(card.check,16),12) },12)
            add(box().apply {
                add(label("Простыми словами",13,mint));add(label(card.plain.ifBlank { card.note },16),10)
                add(button("▷ Озвучить объяснение") { recording.stop();audio.extra(card.explanationAudio,"Объяснение",store.speed) },10)
                add(label(if(card.analogy.isBlank()) "Аналогия раздела" else "Аналогия",13,accent),12)
                add(label(card.analogy.ifBlank { LearningHints.analogy(card.categoryId) },15,muted),8)
                add(button("▷ Озвучить аналогию") { recording.stop();audio.extra(card.analogyAudio,"Аналогия",store.speed) },10)
                add(label("Типичная ошибка",13,accent),12);add(label(card.mistake.ifBlank { LearningHints.mistake(card.categoryId) },15,muted),8)
            },12)
            if(mode=="INTERVIEW") add(followUpPanel(card),14)
            if(mode=="INTERVIEW" || mode=="REVIEW") {
                add(label("Оцените свой ответ. Учебный статус не изменится.",14,muted),14)
                listOf("Ответил" to "good","Частично" to "partial","Не ответил" to "bad").forEach { (name,value) -> add(button(name,value=="good") { assess(card.id,value) },10) }
                if(mode=="REVIEW") add(button("Вернуть в обычное обучение") {
                    MaterialAlertDialogBuilder(this@PracticeActivity).setTitle("Вернуть карточку?").setMessage("Она снова появится среди новых. Остальные запомненные останутся без изменений.").setNegativeButton("Отмена",null).setPositiveButton("Вернуть") { _,_-> store.statuses[card.id]=CardStatus.NEW;persist();assess(card.id,"returned") }.show()
                },12)
            } else {
                add(button("✓ Запомнил",true) { store.statuses[card.id]=CardStatus.LEARNED;s.learned++;persist();nextSessionCard() },14)
                val hard=button(if(store.status(card.id)==CardStatus.HARD) "Уже не сложно" else "Сложно") {}
                hard.setOnClickListener { val status=if(store.status(card.id)==CardStatus.HARD) CardStatus.NEW else CardStatus.HARD;store.statuses[card.id]=status;if(status==CardStatus.HARD) s.difficult++;hard.text=if(status==CardStatus.HARD) "Уже не сложно" else "Сложно";persist();toast("Отметка сохранена") };add(hard,10)
                add(button("Далее без оценки") { nextSessionCard() },10)
            }
        };c.add(answerPanel!!)
        c.add(button("Завершить занятие") { summary() },22)
        if(mode=="INTERVIEW" && !restoring) { remaining=answerSeconds*1000L;timerText?.text="Слушаем вопрос, затем $answerSeconds секунд на ответ";audio.single(card,false,store.speed) }
    }
    private fun assess(id:String,value:String) { val s=session ?: return;if(s.current()!=id || s.answers.containsKey(id)) return;s.answers[id]=value;if(value in listOf("good","partial","bad")) store.assess(id,if(value=="good") 2 else if(value=="partial") 1 else 0,false,if(mode=="INTERVIEW") "interview" else "review");if(value=="partial" || value=="bad") store.mistakes[id]=if(value=="partial") "Частичный ответ на собеседовании" else "Не удалось ответить" else if(value=="good") store.mistakes.remove(id);persist();nextSessionCard() }
    private fun nextSessionCard() { if(mode=="DAILY") { session?.current()?.let { store.planCompleted.add(it) };persist() };audio.stop();countdown?.cancel();session?.next();sessionPage() }
    private fun revealAnswer() { recording.stop();answerVisible=true;countdown?.cancel();countdownStarted=0;audio.stop();answerPanel?.visibility=View.VISIBLE;timerText?.text="Сравните свой ответ с образцом" }
    private fun startCountdown() {
        if(paused || answerVisible || countdown?.isActive==true) return
        countdownStarted=SystemClock.elapsedRealtime()
        countdown=scope.launch {
            val deadline=SystemClock.elapsedRealtime()+remaining
            while(isActive && !answerVisible) {
                remaining=(deadline-SystemClock.elapsedRealtime()).coerceAtLeast(0);countdownStarted=SystemClock.elapsedRealtime()
                timerText?.text="На ответ: ${(remaining+999)/1000} с"
                if(remaining==0L) { revealAnswer();break };delay(200)
            }
        }
    }
    private fun startTicker() {
        ticker?.cancel();ticker=scope.launch {
            while(isActive && session!=null) {
                session?.tick(SystemClock.elapsedRealtime())
                if(session?.expired()==true) { summary();break }
                if(mode!="INTERVIEW") session?.let { timerText?.text=if(it.durationMillis>0) "Осталось: ${((it.durationMillis-it.activeMillis).coerceAtLeast(0)+999)/1000} с" else "Активное время: ${it.activeMillis/1000} с" }
                delay(250)
            }
        }
    }
    private fun resumeSession() {
        if(!paused) { toast("Занятие уже идёт");return }
        paused=false;session?.start(SystemClock.elapsedRealtime());startTicker()
        if(audio.active && audio.paused) audio.toggle() else if(mode=="INTERVIEW" && !answerVisible) startCountdown()
    }
    private fun stopTimers() { ticker?.cancel();countdown?.cancel();ticker=null;countdown=null }
    private fun summary() {
        checkpoint.clear("practice")
        val s=session ?: return;s.pause(SystemClock.elapsedRealtime());stopTimers();audio.stop();mode="SUMMARY"
        val c=page("Итог занятия",s.name)
        c.add(box().apply {
            add(label("Просмотрено: ${s.visited.size} из ${s.ids.size}",24));add(label("Активное время: ${s.activeMillis/60_000} мин ${s.activeMillis/1000%60} с",16,muted),12)
            if(s.answers.isNotEmpty()) { add(label("Ответил: ${s.answers.values.count { it=="good" }}\nЧастично: ${s.answers.values.count { it=="partial" }}\nНе ответил: ${s.answers.values.count { it=="bad" }}\nВозвращено в обучение: ${s.answers.values.count { it=="returned" }}",18),16) }
            else add(label("Новых отметок «Запомнил»: ${s.learned}\nОтметок сложности: ${s.difficult}",17),16)
            if(s.ids.isEmpty()) add(label("В этом режиме пока нет доступных карточек.",16,muted),16)
            add(label("Просмотр и прослушивание сами по себе не подтверждают знание.",14,muted),16)
        });c.add(button("Выбрать другой режим",true) { menu() },20)
    }
    private fun codePage() {
        val tasks=exercises.getJSONArray("code");if(exerciseIndex>=tasks.length()) { exerciseSummary("Python",tasks.length());return }
        val base=tasks.getJSONObject(exerciseIndex);val t=JSONObject(base.toString())
        if(retryOnly) base.optJSONObject("variant")?.let { v -> listOf("code","answer","explanation").forEach { t.put(it,v.getString(it)) } }
        val id=t.getString("id");assisted=hintLevels.getOrDefault(taskKey(),0)>0
        val c=page("Что выведет код?","${exerciseIndex+1} из ${tasks.length()} · Python")
        c.add(code(t.getString("code")))
        val guess=EditText(this).apply { hint="Ваше предположение";setTextColor(ink);setHintTextColor(muted);minLines=2 };retainEditor(guess);c.add(guess,14)
        val answer=box().apply { visibility=if(taskKey() in openedExercises) View.VISIBLE else View.GONE;add(label("Результат",13,mint));add(code(t.getString("answer")),10);add(label(t.getString("explanation"),17),12)
            add(button("Я ответил верно",true) { store.exerciseResults[id]=true;if(!assisted) store.mistakes.remove(id);persist();if(retryOnly) mistakesPage() else { exerciseIndex++;codePage() } },14)
            add(button("Нужно повторить") { store.exerciseResults[id]=false;store.mistakes[id]="Ошибка в предсказании вывода";persist();if(retryOnly) mistakesPage() else { exerciseIndex++;codePage() } },10)
        }
        c.add(hintScaffold(t.getJSONArray("hints"),{ stage ->assisted=true;store.mistakes[id]=if(stage<2) "Использована подсказка Python" else "Просмотрен результат Python";persist() },{answer.visibility=View.VISIBLE}),12)
        c.add(button("Проверить предположение",true) {
            if(guess.text.isBlank()) { toast("Введите предположение или откройте подсказку");return@button }
            openedExercises.add(taskKey());answer.visibility=View.VISIBLE
            if(guess.text.toString().trimEnd()!=t.getString("answer").trimEnd()) {store.mistakes[id]="Неверный вывод Python";persist()}
            toast(if(guess.text.toString().trimEnd()==t.getString("answer").trimEnd()) "Результат совпал" else "Сравните результат и объяснение")
        },14)
        c.add(answer,14)
    }
    private fun sqlPage() {
        val engine=sqlEngine ?: return;val tasks=engine.tasks
        if(exerciseIndex>=tasks.length()) { exerciseSummary("SQL",tasks.length());return }
        val task=tasks.getJSONObject(exerciseIndex);val id=task.getString("id");assisted=hintLevels.getOrDefault(taskKey(),0)>0
        val dataset=if(retryOnly) 2 else 0
        val c=page("SQL тренажёр","SQLite · ${exerciseIndex+1} из ${tasks.length()}")
        c.add(label(task.getString("title"),23))
        c.add(label("Это учебная SQLite-база. Синтаксис и функции могут отличаться от PostgreSQL. Только чтение, до 200 строк.",14,muted),14)
        val schema=code(engine.schema(dataset)).apply { visibility=View.GONE }
        c.add(button("Показать схему и данные") { schema.visibility=if(schema.visibility==View.VISIBLE) View.GONE else View.VISIBLE },12);c.add(schema,10)
        val editor=EditText(this).apply { hint="SELECT ...";setTextColor(ink);setHintTextColor(muted);typeface=Typeface.MONOSPACE;minLines=4;maxLines=10;gravity=Gravity.TOP;inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS };retainEditor(editor);c.add(editor,14)
        val result=label("",15);c.add(result,12)
        val token=pageGeneration
        val run=button("Выполнить и проверить",true) {}
        run.setOnClickListener {
            val sql=editor.text.toString();run.isEnabled=false;result.text="Выполняем…"
            sqlJob=scope.launch {
                try {
                    val r=withContext(Dispatchers.IO) { engine.execute(sql,task,dataset) }
                    if(token==pageGeneration) {
                        result.text=(if(r.correct) if(assisted) "✓ Верно с подсказкой — повторите самостоятельно\n" else "✓ Верно на четырёх наборах\n" else "Не совпадает на всех проверочных наборах\n")+r.columns.joinToString(" · ")+"\n"+r.rows.joinToString("\n") { it.joinToString(" · ") }
                        if(r.correct) { store.exerciseResults[id]=true;if(!assisted) store.mistakes.remove(id) } else store.mistakes[id]="SQL не прошёл проверку на четырёх наборах";persist()
                    }
                } catch(e: Exception) { if(token==pageGeneration) { store.mistakes[id]="Ошибка выполнения SQL";persist();result.text="Ошибка: "+(if(e is android.os.OperationCanceledException) "Запрос превысил лимит времени" else (e.message ?: "Проверьте SQL")) } }
                finally { run.isEnabled=true }
            }
        };c.add(run,12)
        c.add(hintScaffold(task.getJSONArray("hints"),{ stage -> assisted=true;store.mistakes[id]=if(stage<2) "Использована подсказка SQL" else "Просмотрено решение SQL";persist() },{if(!restoringHints) editor.setText(task.getString("solution"));result.text=task.getString("explanation")}),12)
        c.add(button("Следующее задание") { if(retryOnly) mistakesPage() else { exerciseIndex++;sqlPage() } },18)
    }
    private fun exerciseSummary(name:String,total:Int) {
        checkpoint.clear("practice")
        mode="SUMMARY";val prefix=if(name=="SQL") "sql-" else "code-"
        val n=store.exerciseResults.count { it.key.startsWith(prefix) && it.value }
        val c=page("Практика завершена",name);c.add(label("Решено по сохранённым отметкам: $n из $total",24));c.add(button("К режимам",true) { menu() },20)
    }
    private fun mistakesPage() {
        stopTimers();audio.stop();session=null;mode="MISTAKES"
        val c=page("Мои ошибки","Повторная попытка помогает закрепить понимание")
        if(store.mistakes.isEmpty()) c.add(label("Здесь пока пусто. Ошибки, подсказки и частичные ответы будут появляться автоматически.",18))
        store.mistakes.toSortedMap().forEach { (id,reason) ->
            val card=repo.cards.find { it.id==id }
            val extras=LearningV5(this).data
            val extraTitle=listOf("whatifs","comparisons").flatMap { key->val a=extras.getJSONArray(key);(0 until a.length()).map { a.getJSONObject(it) } }.firstOrNull { it.getString("id")==id }?.getString("title")
            val title=card?.question ?: extraTitle ?: listOf("code","sql","cases").asSequence().mapNotNull { key -> val array=exercises.optJSONArray(key) ?: return@mapNotNull null;(0 until array.length()).map { array.getJSONObject(it) }.find { it.getString("id")==id } }.firstOrNull()?.optString("title") ?: id
            c.add(box().apply {
                add(label(title,20));add(label(reason,14,muted),10)
                add(button("Попробовать ещё раз",true) {
                    if(id.startsWith("whatif-") || id.startsWith("compare-")) { startActivity(Intent(this@PracticeActivity,V5Activity::class.java).putExtra("task",id).putExtra("page",if(id.startsWith("whatif-")) "WHATIF" else "COMPARE"));finish();return@button }
                    retryOnly=true
                    if(card!=null) { mode="REVIEW";paused=false;session=LearningSession("Повторение ошибки",listOf(id));session!!.start(SystemClock.elapsedRealtime());startTicker();sessionPage() }
                    else {
                        val key=when { id.startsWith("code-")->"code";id.startsWith("sql-")->"sql";else->"cases" }
                        val array=exercises.getJSONArray(key);val index=(0 until array.length()).first { array.getJSONObject(it).getString("id")==id }
                        val retryKey="${when(key) { "code"->"CODE";"sql"->"SQL";else->"CASES" }}:$index:true"
                        hintLevels.remove(retryKey);openedExercises.remove(retryKey);drafts.remove(retryKey)
                        when(key) { "code"->{mode="CODE";exerciseIndex=index;codePage()};"sql"->{mode="SQL";exerciseIndex=index;scope.launch { sqlEngine=withContext(Dispatchers.IO) { SqlTrainer(this@PracticeActivity) };sqlPage() }};else->{mode="CASES";caseIndex=index;casePage()} }
                    }
                },12)
            },14)
        }
        c.add(button("К режимам") { menu() },20)
    }
    private fun casePage() {
        val cases=exercises.getJSONArray("cases")
        if(caseIndex>=cases.length()) { val c=page("Кейсы завершены");c.add(button("К режимам",true) { menu() });return }
        val t=cases.getJSONObject(caseIndex);val id=t.getString("id");assisted=hintLevels.getOrDefault(taskKey(),0)>0
        val c=page("Кейс Middle","${caseIndex+1} из ${cases.length()}")
        c.add(label(t.getString("title"),25));c.add(label(t.getString("problem"),18),16)
        val guess=EditText(this).apply { hint="Предложите решение своими словами";minLines=3;setTextColor(ink);setHintTextColor(muted) };retainEditor(guess);c.add(guess,16)
        val answer=box().apply {
            visibility=if(taskKey() in openedExercises) View.VISIBLE else View.GONE;add(label("Разбор решения",13,mint));add(label(t.getString("answer"),18),12)
            add(label("Типичная ошибка: "+t.getString("mistake"),16,muted),14);add(label(t.getString("follow_up"),17),14)
            fun next() { if(retryOnly) mistakesPage() else { caseIndex++;casePage() } }
            add(button("Могу объяснить решение",true) { store.exerciseResults[id]=true;if(!assisted) store.mistakes.remove(id);persist();next() },16)
            add(button("Нужно разобрать ещё раз") { store.exerciseResults[id]=false;store.mistakes[id]="Неуверенный ответ на практический кейс";persist();next() },10)
        }
        c.add(hintScaffold(t.getJSONArray("hints"),{ stage ->assisted=true;store.mistakes[id]=if(stage<2) "Использована подсказка кейса" else "Просмотрен разбор кейса";persist() },{answer.visibility=View.VISIBLE}),12)
        c.add(button("Сравнить с разбором",true) { if(guess.text.isBlank()) toast("Сначала предложите решение или откройте подсказку") else {openedExercises.add(taskKey());answer.visibility=View.VISIBLE} },18);c.add(answer,14)
    }
    private fun planPage() {
        stopTimers();audio.stop();session=null;mode="PLAN"
        val today=java.time.LocalDate.now()
        val stored=runCatching { java.time.LocalDate.parse(store.interviewDate) }.getOrNull()
        val overdue=stored!=null && stored.isBefore(today)
        val target=if(overdue) today else stored ?: today.plusDays(14)
        if(store.interviewDate.isBlank()) { store.interviewDate=target.toString();persist() }
        val remaining=repo.cards.filter { store.status(it.id)!=CardStatus.LEARNED }
        if(store.planDay!=today.toString()) { store.planDay=today.toString();store.planInitialRemaining=remaining.size;store.planCompleted.clear();persist() }
        val plan=PreparationPlan.calculate(today,target,store.dailyMinutes,store.planInitialRemaining)
        val c=page("План подготовки","Дата интервью и доступное время")
        if(overdue) c.add(label("Дата интервью прошла. Ниже план на сегодня; выберите новую дату.",16,accent))
        c.add(button("Дата: ${stored ?: target}") {
            DatePickerDialog(this,{ _,year,month,day -> val date=java.time.LocalDate.of(year,month+1,day);if(date.isBefore(java.time.LocalDate.now())) toast("Выберите сегодня или будущую дату") else {store.interviewDate=date.toString();persist();planPage()} },target.year,target.monthValue-1,target.dayOfMonth).show()
        })
        c.add(button("В день: ${store.dailyMinutes} минут") {
            val minutes=listOf(5,10,15,20,30,45,60,90,120)
            MaterialAlertDialogBuilder(this).setTitle("Сколько времени есть в день?").setItems(minutes.map { "$it минут" }.toTypedArray()) { _,i ->store.dailyMinutes=minutes[i];persist();planPage() }.show()
        },10)
        c.add(box().apply {
            add(label("Осталось ${plan.days} дней · ${remaining.size} карточек",22))
            add(label("Чтобы пройти материал: ${plan.needed} карточек в день.\nДневной минимум: ${plan.goal} карточек и ${plan.practiceMinutes} минут практики.\nЗавершено сегодня: ${store.planCompleted.size}.",17),12)
            add(label("Оценка: около 2 минут на карточку. Глубокий разбор может занять больше времени.",14,muted),12)
            if(plan.tight) add(label("В выбранное время план не помещается. Увеличьте время или перенесите дату; дневная цель сейчас ограничена доступным временем.",16,accent),12)
            add(label("Ошибок для повторения: ${store.mistakes.size}. Выученные остаются в отдельной добровольной проверке.",15,muted),12)
        },16)
        val groups=remaining.groupBy { it.categoryId }.toSortedMap().values.toList()
        val offset=if(groups.isEmpty()) 0 else (today.toEpochDay()%groups.size).toInt()
        val ordered=if(groups.isEmpty()) emptyList() else (groups.drop(offset)+groups.take(offset)).flatten()
        val selected=ordered.filter { it.id !in store.planCompleted }.take((plan.goal-store.planCompleted.size).coerceAtLeast(0))
        if(selected.isNotEmpty()) {
            c.add(label("Темы сегодня: "+selected.map { it.category }.distinct().joinToString(", "),16),16)
            c.add(button("Начать дневное занятие",true) {
                mode="DAILY";paused=false;session=LearningSession("План на сегодня",selected.map { it.id },((store.dailyMinutes-plan.practiceMinutes)*60_000).toLong());session!!.start(SystemClock.elapsedRealtime());startTicker();sessionPage()
            },14)
        } else c.add(label("Дневной минимум выполнен или нет новых карточек. Можно проверить себя или повторить ошибки.",18),16)
        c.add(button("Повторить ошибки") { mistakesPage() },12)
    }

    private fun hintScaffold(hints:org.json.JSONArray,used:(Int)->Unit,solution:()->Unit):View {
        val c=col();val text=label("",15,muted);var level=hintLevels.getOrDefault(taskKey(),0).coerceIn(0,hints.length()+1)
        text.text=(0 until minOf(level,hints.length())).joinToString("\n\n") { hints.getString(it) }
        val b=button(when(level) {0->"Подсказка 1 — намёк";1->"Подсказка 2 — подход";else->"Показать решение"}) {}
        if(level>hints.length()) { restoringHints=true;solution();restoringHints=false;b.isEnabled=false;b.text="Решение открыто" }
        b.setOnClickListener {
            used(level)
            if(level<hints.length()) { text.text=text.text.toString()+(if(level==0) "" else "\n\n")+hints.getString(level);level++;b.text=if(level<hints.length()) "Подсказка 2 — подход" else "Показать решение" }
            else { solution();level++;b.isEnabled=false;b.text="Решение открыто" }
            hintLevels[taskKey()]=level;savePractice()
        }
        c.add(b);c.add(text,8);return c
    }
    private fun graded(id:String,score:Int,assisted:Boolean,source:String) {
        store.assess(id,score,assisted,source)
        if(score<2) store.mistakes[id]=if(assisted) "Потребовалась подсказка" else "Нужно повторить ответ" else store.mistakes.remove(id)
        persist();toast("Самооценка сохранена. Учебный статус не изменён.")
    }
    private fun followUpPanel(card:Card):View {
        val c=box();c.add(label("Уточняющие вопросы",19));c.add(label("Необязательно. Ответьте самостоятельно, затем откройте ориентир. Это связанные вопросы по теме.",14,muted),10)
        val items=learning.getJSONObject("followups").getJSONArray(card.id)
        for(i in 0 until items.length()) {
            val t=items.getJSONObject(i);val id=t.getString("card_id")
            val related=repo.cards.first { it.id==id }
            c.add(label("${i+1}. ${t.getString("question")}",17),16)
            c.add(button("▷ Уточнение ${i+1}") { recording.stop();audio.single(related,false,store.speed) },8)
            val answer=col().apply { visibility=View.GONE;add(label(t.getString("answer"),16,muted))
                add(button("▷ Озвучить ориентир ${i+1}") { recording.stop();audio.single(related,true,store.speed) },8)
                listOf(Triple("Самостоятельно",2,false),Triple("Частично",1,false),Triple("С подсказкой",1,true),Triple("Не ответил",0,false)).forEach { (title,score,assist) ->add(button(title) { graded(id,score,assist,"followup") },8) }
            }
            c.add(button("Открыть ориентир ${i+1}") { store.viewed(id);answer.visibility=View.VISIBLE;persist() },10);c.add(answer,10)
        }
        return c
    }
    private fun readinessPage() {
        stopTimers();audio.stop();session=null;mode="STATS"
        val c=page("Готовность по темам","Самопроверка отдельно от просмотров")
        c.add(label("Процент относится только к оценённым ответам; охват темы показан отдельно. Это ваши последние самооценки, а не прогноз интервью. Просмотр, прослушивание и «Запомнил» сами по себе не повышают готовность.",15,muted))
        repo.cards.groupBy { it.categoryId }.forEach { (_,cards) ->
            val ids=cards.map { it.id }.toSet();val r=Readiness.calculate(ids,store.evidence)
            c.add(box().apply {
                add(label(cards.first().category,20))
                add(label(if(r.percent==null) "Пока нет самопроверок" else "Самооценка ответов: ${r.percent}%",21,accent),12)
                add(label("Оценено ${r.assessed} из ${r.total} · самостоятельно ${r.independent}\nЧастично ${r.partial} · с подсказкой ${r.assisted} · не ответил ${r.incorrect}",15,muted),10)
                add(label("Открыто карточек: ${ids.count { store.views.getOrDefault(it,0)>0 }}\nОтметка «Запомнил»: ${cards.count { store.status(it.id)==CardStatus.LEARNED }}",14,muted),10)
                add(label("Охват самопроверки: ${r.assessed*100/r.total}%",13,muted),12)
                add(ProgressBar(this@PracticeActivity,null,android.R.attr.progressBarStyleHorizontal).apply { max=r.total;progress=r.assessed;progressTintList=ColorStateList.valueOf(mint) },6)
            },14)
        }
    }
    private fun chainsPage() {
        stopTimers();audio.stop();session=null;mode="CHAINS"
        val c=page("Учебные цепочки","Свяжите отдельные ответы в понятную систему")
        c.add(label("Это отдельный просмотр. Выученные карточки не возвращаются в обычную очередь.",14,muted))
        val a=learning.getJSONArray("chains")
        for(i in 0 until a.length()) {
            val chain=a.getJSONObject(i);val n=chain.getJSONArray("steps").length();val completed=store.routeProgress.getOrDefault(chain.getString("id"),0)
            c.add(box().apply { add(label(chain.getString("title"),22));add(label("Пройдено $completed из $n",14,muted),8)
                add(button(if(completed in 1 until n) "Продолжить цепочку" else "Открыть цепочку",true) {
                    route=chain;routeIndex=if(completed<n) completed else 0;chainPage()
                },12)
            },14)
        }
    }
    private fun chainPage() {
        val chain=route ?: return;mode="CHAIN"
        val steps=chain.getJSONArray("steps");val step=steps.getJSONObject(routeIndex)
        val card=repo.cards.first { it.id==step.getString("card_id") };store.viewed(card.id);persist()
        val c=page(chain.getString("title"),"Шаг ${routeIndex+1} из ${steps.length()}")
        c.add(label(step.getString("connection"),16,accent));c.add(label(card.question,18),16)
        c.add(button("▷ Озвучить вопрос") { recording.stop();audio.single(card,false,store.speed) },12)
        c.add(AudioSeekView(this,audio,true),8)
        val answer=box().apply { visibility=if(taskKey() in openedExercises) View.VISIBLE else View.GONE;add(V5CardView(this@PracticeActivity,card,store,audio,{persist()},{recording.stop()}));add(code(card.example),14);add(label(card.note,16,muted),12)
            add(button("▷ Озвучить объяснение") { recording.stop();audio.extra(card.explanationAudio,"Объяснение",store.speed) },12)
            add(button("▷ Озвучить аналогию") { recording.stop();audio.extra(card.analogyAudio,"Аналогия",store.speed) },8)
            add(label("Оцените, смогли ли вы объяснить это самостоятельно. Учебный статус не изменится.",14,muted),14)
            listOf(Triple("Самостоятельно",2,false),Triple("С подсказкой",1,true),Triple("Не ответил",0,false)).forEach { (title,score,assist) ->add(button(title) { graded(card.id,score,assist,"chain") },8) }
        }
        c.add(button("Показать ответ и связь с примером",true) { openedExercises.add(taskKey());answer.visibility=View.VISIBLE;savePractice() },14);c.add(answer,14)
        c.add(button("Моя заметка") { NoteEditor.show(this,card.id,store) { persist() } },10)
        if(routeIndex>0) c.add(button("Предыдущий шаг") { audio.stop();routeIndex--;chainPage() },14)
        c.add(button(if(routeIndex+1<steps.length()) "Следующий шаг" else "Завершить цепочку",true) {
            audio.stop();store.routeProgress[chain.getString("id")]=maxOf(store.routeProgress.getOrDefault(chain.getString("id"),0),routeIndex+1);persist()
            if(routeIndex+1<steps.length()) { routeIndex++;chainPage() } else {checkpoint.clear("practice");chainsPage()}
        },10)
        c.add(button("К списку цепочек") { audio.stop();chainsPage() },10)
    }

    private fun toast(s:String) { Toast.makeText(this,s,Toast.LENGTH_LONG).show() }
    private fun back() { when { mode=="MENU"->finish();session!=null && mode!="SUMMARY"->summary();else->menu() } }
    private fun taskKey()=when(mode) { "CODE","SQL"->"$mode:$exerciseIndex:$retryOnly";"CASES"->"$mode:$caseIndex:$retryOnly";"CHAIN"->"CHAIN:${route?.optString("id")}:$routeIndex";else->mode }
    private fun retainEditor(field:EditText) {
        val key=taskKey();if(drafts.has(key)) field.setText(drafts.getString(key));field.filters=arrayOf(android.text.InputFilter.LengthFilter(10000))
        field.addTextChangedListener(object:android.text.TextWatcher {
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {};override fun afterTextChanged(s:android.text.Editable?) {}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) { drafts.put(key,s.toString());savePractice() }
        })
    }
    private fun openAdvanced(page:String) { savePractice();audio.stop();startActivity(Intent(this,V5Activity::class.java).putExtra("page",page));finish() }
    private fun savePractice() {
        if(!::checkpoint.isInitialized || !::audio.isInitialized || mode !in setOf("INTERVIEW","REVIEW","TIMED","TWENTY","HARD","DAILY","CODE","SQL","CASES","CHAIN")) return
        val thinking=if(countdownStarted>0 && !paused) (remaining-(SystemClock.elapsedRealtime()-countdownStarted)).coerceAtLeast(0) else remaining
        val p=JSONObject().put("mode",mode).put("session",session?.toJson()).put("remaining",thinking).put("seconds",answerSeconds).put("answerVisible",answerVisible).put("audio",audio.snapshot())
            .put("exerciseIndex",exerciseIndex).put("caseIndex",caseIndex).put("retry",retryOnly).put("drafts",drafts).put("hints",JSONObject(hintLevels as Map<*,*>)).put("opened",org.json.JSONArray(openedExercises.toList()))
            .put("route",route?.optString("id")).put("routeIndex",routeIndex).put("scroll",pageScroll?.scrollY ?: 0)
        checkpoint.save("practice",session?.name ?: when(mode) {"CODE"->"Python-задача";"SQL"->"SQL-задача";"CASES"->"Кейс Middle";else->"Учебная цепочка"},p)
    }
    private fun restorePractice(j:JSONObject) {
        runCatching {
            mode=j.getString("mode");require(mode in setOf("INTERVIEW","REVIEW","TIMED","TWENTY","HARD","DAILY","CODE","SQL","CASES","CHAIN"))
            session=j.optJSONObject("session")?.let { restoreSession(it,repo.cards.map { c->c.id }.toSet()) };paused=true
            remaining=j.getLong("remaining").coerceIn(0,90000);answerSeconds=j.getInt("seconds").coerceIn(30,90);val visible=j.optBoolean("answerVisible")
            exerciseIndex=j.optInt("exerciseIndex");caseIndex=j.optInt("caseIndex");require(exerciseIndex in 0..16 && caseIndex in 0..8);retryOnly=j.optBoolean("retry")
            drafts=j.optJSONObject("drafts") ?: JSONObject();hintLevels.clear();j.optJSONObject("hints")?.let { h->h.keys().forEach { hintLevels[it]=h.getInt(it) } }
            openedExercises.clear();j.optJSONArray("opened")?.let { a->for(i in 0 until a.length()) openedExercises.add(a.getString(i)) }
            when(mode) {
                "CODE"->codePage();"CASES"->casePage();"SQL"->{sqlEngine=SqlTrainer(this);sqlPage()}
                "CHAIN"->{ val a=learning.getJSONArray("chains");route=(0 until a.length()).map { a.getJSONObject(it) }.first { it.getString("id")==j.getString("route") };routeIndex=j.getInt("routeIndex");chainPage() }
                else->{ require(session!=null);sessionPage(true);if(visible) revealAnswer();timerText?.text="Восстановлено на паузе · продолжите занятие" }
            }
            j.optJSONObject("audio")?.let { audio.restore(it,repo.cards) };pageScroll?.post { pageScroll?.scrollTo(0,j.optInt("scroll")) };savePractice()
        }.onFailure { checkpoint.clear("practice");toast("Не удалось восстановить занятие");menu() }
    }
    override fun onStart() { super.onStart();if(::checkpoint.isInitialized) checkpointTicks.start() }
    override fun onStop() {
        super.onStop();if(::recording.isInitialized) recording.stop();if(::audio.isInitialized) audio.pause()
        if(session!=null && mode!="SUMMARY") { paused=true;session?.pause(SystemClock.elapsedRealtime());if(countdownStarted>0) remaining=(remaining-(SystemClock.elapsedRealtime()-countdownStarted)).coerceAtLeast(0);stopTimers();timerText?.text="На паузе · нажмите «Продолжить после паузы»" }
        savePractice();checkpointTicks.stop()
    }
    override fun onDestroy() { checkpointTicks.stop();if(::recording.isInitialized) recording.close(); stopTimers();sqlJob?.cancel();if(::audio.isInitialized) audio.close();saves.close();scope.cancel();super.onDestroy() }
}
