package com.python.interviewcards

import android.app.Activity
import android.content.*
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.json.JSONObject
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private val bg = Color.rgb(16,18,24)
    private val surface = Color.rgb(27,30,40)
    private val muted = Color.rgb(163,168,187)
    private val ink = Color.rgb(244,242,250)
    private val accent = Color.rgb(184,160,255)
    private val mint = Color.rgb(134,232,197)
    private val amber = Color.rgb(255,200,128)
    private val scope = CoroutineScope(SupervisorJob()+Dispatchers.Main)
    private val saves = Channel<String>(Channel.UNLIMITED)
    private lateinit var store: ProgressStore
    private lateinit var repo: CardRepository
    private lateinit var audio: AudioController
    private lateinit var recording:RecordingUi
    private lateinit var root: LinearLayout
    private var radioLabel: TextView?=null
    private var returningFromPractice=false
    private var statusText: TextView? = null
    private var positionText: TextView? = null
    private var phaseText: TextView? = null
    private var playButton: MaterialButton? = null
    private var answerBox: LinearLayout? = null
    private var revealButton: MaterialButton? = null
    private var hintText: TextView? = null
    private var screen = "HOME"
    private var category = ""
    private var filter = "ACTIVE"
    private var catalogCategory = ""
    private var catalogStatus = "ALL"
    private var query = ""
    private lateinit var checkpoint:Checkpoint
    private val checkpointTicks=CheckpointTicker { saveStudy() }
    private var restoringStudy:JSONObject?=null
    private val queue = LearningQueue()
    private var revealed = false
    private var current: Card? = null
    private var studyScroll: ScrollView? = null
    private val titles = listOf("Python и модель данных","Коллекции и итераторы","Функции и ООП","Потоки и asyncio","SQL, PostgreSQL, Redis","Django и FastAPI","HTTP и безопасность","Тестирование и качество","Docker и эксплуатация","Практические задачи","Django дополнительно","FastAPI дополнительно","Alembic и миграции")
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        WindowCompat.setDecorFitsSystemWindows(window,false)
        root = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(bg) }
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v,insets ->
            val b=insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            v.setPadding(b.left,b.top,b.right,b.bottom); insets
        }
        root.addView(text("Готовим карточки…",20,ink).apply { setPadding(dp(24),dp(40),dp(24),dp(24)) })
        checkpoint=Checkpoint(this)
        store=ProgressStore(applicationContext)
        val writeScope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        writeScope.launch { for (snapshot in saves) runCatching { store.persist(snapshot) }.onFailure { scope.launch { toast("Не удалось сохранить прогресс. Попробуйте ещё раз.") } }; writeScope.cancel() }
        audio=AudioController(this,{ label,playing -> phaseText?.text=label; playButton?.text=if(playing) "Ⅱ  Пауза" else if(audio.active) "▶  Продолжить" else idlePlayLabel() },{ nextCard() },{ toast(it) })
        recording=RecordingUi(this) { audio.stop();PlaybackService.command(this,PlaybackService.STOP) }
        scope.launch {
            try {
                withContext(Dispatchers.IO) { repo=CardRepository(this@MainActivity); store.load() }
                val target=intent.getStringExtra("viewCard")?.let { id->repo.cards.find { it.id==id } }
                if(target!=null) { openSession("",if(store.status(target.id)==CardStatus.LEARNED) "LEARNED" else "ACTIVE",target.id,false);return@launch }
                val restore=state?.getString("card")
                if (restore!=null) {
                    category=state.getString("category","");filter=state.getString("filter","ACTIVE")
                    if(!resumeLast()) openSession(category,filter,restore,false)
                } else home()
            } catch (_: Exception) { root.removeAllViews(); root.addView(text("Не удалось загрузить карточки. Переустановите приложение.",20,ink)) }
        }
    }
    private fun save() { saves.trySend(store.snapshot()) }
    private fun compact()=resources.configuration.screenHeightDp<700 || resources.configuration.fontScale>=1.3f
    private fun idlePlayLabel()=if(compact()) "▶  Слушать" else "▶  Слушать карточку"
    private fun dp(n: Int)=(n*resources.displayMetrics.density).toInt()
    private fun shape(color: Int, radius: Int=20, stroke: Int?=null)=GradientDrawable().apply { setColor(color); cornerRadius=dp(radius).toFloat(); stroke?.let { setStroke(dp(1),it) } }
    private fun text(value: String,size: Int=16,color: Int=ink,bold: Boolean=false)=TextView(this).apply {
        text=value; textSize=size.toFloat(); setTextColor(color); setLineSpacing(dp(3).toFloat(),1f)
        if (bold) typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)
    }
    private fun column()=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
    private fun row()=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
    private fun LinearLayout.add(v: View, height: Int=ViewGroup.LayoutParams.WRAP_CONTENT, top: Int=0) { addView(v,LinearLayout.LayoutParams(-1,if(height>=0) dp(height) else height).apply { topMargin=dp(top) }) }
    private fun LinearLayout.weight(v: View, margin: Int=0) { addView(v,LinearLayout.LayoutParams(0,-2,1f).apply { marginStart=dp(margin) }) }
    private fun button(label: String,primary: Boolean=false,click: ()->Unit)=MaterialButton(this).apply {
        text=label; isAllCaps=false; textSize=14f; minHeight=dp(48); minimumHeight=dp(48)
        insetTop=0;insetBottom=0;cornerRadius=dp(14)
        setPadding(dp(12),dp(8),dp(12),dp(8))
        backgroundTintList=ColorStateList.valueOf(if(primary) accent else surface)
        setTextColor(if(primary) Color.rgb(32,22,58) else ink)
        setOnClickListener { click() }
    }
    private fun panel(color: Int=surface)=column().apply { background=shape(color); setPadding(dp(20),dp(20),dp(20),dp(20)) }
    private fun clear(name: String) { screen=name; statusText=null;positionText=null;phaseText=null;playButton=null;answerBox=null;revealButton=null;root.removeAllViews() }
    private fun body(): LinearLayout {
        val content=column().apply { setPadding(dp(20),dp(8),dp(20),dp(24)) }
        root.addView(ScrollView(this).apply { isFillViewport=true;clipToPadding=false; addView(content) },LinearLayout.LayoutParams(-1,0,1f))
        return content
    }
    private fun header(title: String,subtitle: String?=null,back: Boolean=false) {
        val bar=row().apply { setPadding(dp(20),dp(12),dp(20),dp(12)) }
        if(!back && screen=="HOME") bar.addView(ImageView(this).apply { setImageResource(R.mipmap.ic_launcher);contentDescription="Иконка Middle" },LinearLayout.LayoutParams(dp(48),dp(48)).apply { marginEnd=dp(12) })
        if(back) bar.addView(button("‹") { home() },LinearLayout.LayoutParams(dp(48),dp(48)).apply { marginEnd=dp(12) })
        val titleBox=column().apply { add(text(title,if(back) 20 else 24,ink,true));subtitle?.let { add(text(it,12,muted),top=2) } }
        bar.addView(titleBox,LinearLayout.LayoutParams(0,-2,1f))
        if(screen!="SETTINGS") bar.addView(button("⚙") { settings() }.apply { contentDescription="Настройки" },LinearLayout.LayoutParams(dp(48),dp(48)))
        root.addView(bar)
    }
    private fun nav(selected: String) {
        val n=row().apply { setPadding(dp(16),dp(8),dp(16),dp(8));background=shape(bg,0) }
        listOf("HOME" to "Главная","CATALOG" to "Карточки","SETTINGS" to "Настройки").forEachIndexed { i,(key,label) ->
            n.weight(button(label,key==selected) { saveStudy();audio.stop(); when(key) { "HOME"->home();"CATALOG"->catalog();else->settings() } }.apply { textSize=12f;setPadding(dp(4),dp(8),dp(4),dp(8)) },if(i>0) 6 else 0)
        };root.addView(n)
    }
    private fun statusName(s: CardStatus)=when(s) { CardStatus.NEW->"Новое";CardStatus.HARD->"Сложно";CardStatus.LEARNED->"Запомнил" }
    private fun statusColor(s: CardStatus)=when(s) { CardStatus.NEW->muted;CardStatus.HARD->amber;CardStatus.LEARNED->mint }
    private fun count(status: CardStatus, cards: List<Card> = repo.cards)=cards.count { store.status(it.id)==status }
    private fun home() {
        if(!::repo.isInitialized) return
        saveStudy()
        audio.stop();current=null; clear("HOME"); header("Middle", "PYTHON · ПОДГОТОВКА К ИНТЕРВЬЮ")
        val content=body()
        val hero=panel(Color.rgb(39,32,58)).apply {
            background=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(Color.rgb(52,39,79),Color.rgb(29,31,43))).apply { cornerRadius=dp(24).toFloat() }
            add(text("ЗНАНИЯ, КОТОРЫЕ ОСТАЮТСЯ",11,accent,true))
            add(text("Понимать.\nОтвечать уверенно.",30,ink,true),top=12)
            add(text("Вопрос → свой ответ → разбор.\n${repo.cards.size} карточек для Python Middle.",15,Color.rgb(204,198,220)),top=12)
            add(button(if(checkpoint.read()!=null) "Продолжить с места остановки" else if(store.lastCard.isNotEmpty()) "Продолжить обучение  →" else "Начать обучение  →",true) {
                if(!resumeLast()) openSession(store.lastCategory,store.lastFilter.takeIf { it in listOf("ACTIVE","HARD","NEW") } ?: "ACTIVE",store.lastCard,true)
            },top=20)
        };content.add(hero)
        checkpoint.read()?.let { content.add(text("Сохранено: "+it.optString("title"),13,muted),top=8);content.add(button("Новая тренировка") { openSession("","ACTIVE") },top=8) }
        val due=SpacedReview.due(repo.cards.map { it.id },store.statuses.filterValues { it==CardStatus.LEARNED }.keys,store.statuses.filterValues { it==CardStatus.HARD }.keys,store.schedules,store.includeLearnedInDue,System.currentTimeMillis()).size
        content.add(button("Повторить сегодня · $due",true) { openAdvanced("DUE") },top=12)
        content.add(button("Понять и закрепить") { openAdvanced("") },top=8)
        content.add(button("▶  Слушать без остановки",true) { startRadio() },14)
        radioLabel=text(RadioState.label,13,muted);content.add(radioLabel!!,top=8)
        val radioControls=row()
        radioControls.weight(button("Пауза / пуск") { if(RadioState.running) PlaybackService.command(this,PlaybackService.TOGGLE) else startRadio() })
        radioControls.weight(button("Далее") { PlaybackService.command(this,PlaybackService.NEXT) },6)
        radioControls.weight(button("Стоп") { PlaybackService.command(this,PlaybackService.STOP) },6);content.add(radioControls,top=8)
        content.add(AudioSeekView(this,{RadioState.controller?.positionMillis() ?: 0},{RadioState.controller?.durationMillis() ?: 0},{RadioState.controller?.canSeek()==true},{RadioState.controller?.seekTo(it)},{RadioState.controller?.seekBy(it)},{RadioState.controller?.repeatPhrase()}),top=6)
        content.add(button("Режимы и тренажёры  →",true) {
            audio.stop();PlaybackService.command(this,PlaybackService.STOP);returningFromPractice=true
            startActivity(Intent(this,PracticeActivity::class.java))
        },top=14)
        content.add(text("Собеседование · 10 минут · Python-задачи · SQL",13,muted),top=8)
        val learned=count(CardStatus.LEARNED);val hard=count(CardStatus.HARD)
        val stats=row().apply { setPadding(0,dp(16),0,dp(16)) }
        listOf(Triple("$learned","Запомнил",mint),Triple("$hard","Сложные",amber),Triple("${repo.cards.size-learned-hard}","Новые",accent)).forEachIndexed { i,(n,label,color) ->
            stats.weight(panel().apply { setPadding(dp(14),dp(14),dp(10),dp(14));add(text(n,26,color,true));add(text(label,12,muted),top=4) },if(i>0) 8 else 0)
        };content.add(stats)
        content.add(ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply { max=repo.cards.size;progress=learned;progressTintList=ColorStateList.valueOf(mint);progressBackgroundTintList=ColorStateList.valueOf(surface) },4)
        content.add(text("${learned*100/repo.cards.size}% отмечено «Запомнил» · ${repo.cards.size-learned} в обучении",12,muted),top=8)
        val quick=row();quick.weight(button("Сложные · $hard") { openSession("","HARD") });quick.weight(button("Запомненные · $learned") { catalogStatus="LEARNED";catalogCategory="";query="";catalog() },8);content.add(quick,top=16)
        content.add(text("Учиться по темам",22,ink,true),top=28)
        content.add(text("Выберите одну тему или повторяйте всё",13,muted),top=4)
        content.add(button("Все темы  ·  ${repo.cards.size} карточек") { openSession("","ACTIVE") },top=14)
        repo.cards.groupBy { it.categoryId }.forEach { (id,cards) ->
            val done=count(CardStatus.LEARNED,cards)
            val box=panel().apply {
                val r=row();r.addView(text(id,15,accent,true).apply { background=shape(Color.rgb(45,39,64),12);gravity=Gravity.CENTER },LinearLayout.LayoutParams(dp(44),dp(44)))
                r.addView(column().apply { add(text(titles[id.toInt()-1],16,ink,true));add(text("${cards.size} карточек · $done запомнено",12,muted),top=4) },LinearLayout.LayoutParams(0,-2,1f).apply { marginStart=dp(14) })
                r.addView(text("›",26,muted));add(r)
                add(ProgressBar(this@MainActivity,null,android.R.attr.progressBarStyleHorizontal).apply { max=cards.size;progress=done;progressTintList=ColorStateList.valueOf(mint);progressBackgroundTintList=ColorStateList.valueOf(Color.rgb(48,52,65)) },3,14)
                isClickable=true;isFocusable=true;contentDescription="${titles[id.toInt()-1]}, ${cards.size} карточек"
                setOnClickListener { openSession(id,"ACTIVE") }
            }; content.add(box,top=10)
        }
        content.add(text("Всё работает без интернета. Прогресс хранится на устройстве.",12,muted),top=24);nav("HOME")
    }
    private fun eligible()=repo.cards.filter { (category.isEmpty() || it.categoryId==category) && when(filter) {
        "LEARNED"->store.status(it.id)==CardStatus.LEARNED;"HARD"->store.status(it.id)==CardStatus.HARD;"NEW"->store.status(it.id)==CardStatus.NEW;else->store.status(it.id)!=CardStatus.LEARNED
    } }
    private fun weight(c: Card)=if(filter=="ACTIVE" && store.status(c.id)==CardStatus.HARD) 3 else 1
    private fun openSession(cat: String,mode: String,preferred: String?=null,autoplay: Boolean=true) {
        PlaybackService.command(this,PlaybackService.STOP);audio.stop();category=cat;filter=mode
        val allowed=eligible();queue.begin(allowed.associate { it.id to weight(it) },preferred)
        displayCurrent(autoplay)
    }
    private fun displayCurrent(autoplay: Boolean) {
        recording.stop();audio.stop()
        current=repo.cards.find { it.id==queue.current }
        val c=current
        val restored=restoringStudy;restoringStudy=null
        if(c==null) { completed();return }
        revealed=restored?.optBoolean("revealed") ?: (store.listening || filter=="LEARNED")
        store.viewed(c.id)
        if(filter!="LEARNED") { store.lastCard=c.id;store.lastCategory=category;store.lastFilter=filter }
        save()
        clear("STUDY");header(if(filter=="LEARNED") "Запомненные" else "Тренировка",titles[c.categoryId.toInt()-1],true)
        val content=column().apply { setPadding(dp(20),dp(4),dp(20),dp(20)) }
        studyScroll=SwipeScrollView(this,{nextCard()},{previousCard()}).apply { isFillViewport=true;addView(content) }
        root.addView(studyScroll,LinearLayout.LayoutParams(-1,0,1f))
        val label=row()
        label.addView(text("ВОПРОС ${c.number.toString().padStart(3,'0')}",11,accent,true),LinearLayout.LayoutParams(0,-2,1f))
        statusText=text(statusName(store.status(c.id)),12,statusColor(store.status(c.id)),true);label.addView(statusText);content.add(label,top=4)
        content.add(text(c.question,18,ink),top=8)
        val actions=column().apply { visibility=View.GONE }
        val modes=row();modes.weight(button("Вспомнить",!store.listening) { switchMode(false) });modes.weight(button("Слушать",store.listening) { switchMode(true) },8);actions.add(modes,top=8)
        val repeats=row();(1..5).forEach { n ->repeats.weight(button("×$n",store.repetitions==n) { store.repetitions=n;save();refreshRepeatButtons(repeats) }.apply { minWidth=0;minimumWidth=0;setPadding(0,0,0,0);tag=n;isCheckable=true;isChecked=store.repetitions==n;contentDescription="Повторить $n раз" },4) };actions.add(repeats,top=8)
        actions.add(recording.panel(c.id),top=8)
        actions.add(button("Моя заметка") { NoteEditor.show(this,c.id,store) { save() } },top=8)
        actions.add(button("Повторить карточку сначала") { startAudio() },top=8)
        actions.add(button("Сбросить прогресс карточки") { reset(listOf(c),"этой карточки") { openSession(category,filter,c.id,false) } },top=8)
        val small=row();small.weight(button("▷ Вопрос") { recording.stop();audio.single(c,false,store.speed) })
        small.weight(button("Действия и заметки") { actions.visibility=if(actions.visibility==View.VISIBLE) View.GONE else View.VISIBLE },8);content.add(small,top=12);content.add(actions)
        revealButton=button("Показать ответ и пример",true) { reveal() }.apply { visibility=if(revealed) View.GONE else View.VISIBLE };content.add(revealButton!!,top=12)
        answerBox=column().apply {
            visibility=if(revealed) View.VISIBLE else View.GONE
            add(panel().apply {
                add(text("КАК ОТВЕТИТЬ",11,mint,true));add(V5CardView(this@MainActivity,c,store,audio,{save()},{recording.stop()}),top=12)
            },top=12)
            add(panel(Color.rgb(23,28,34)).apply {
                add(text("ПРИМЕР И РАЗБОР",11,accent,true))
                add(HorizontalScrollView(this@MainActivity).apply { addView(text(c.example,14,mint).apply { typeface=Typeface.MONOSPACE;setTextIsSelectable(true);setPadding(dp(12),dp(12),dp(12),dp(12)) }) },top=10)
                add(text(c.note,16,muted),top=10)
                add(button("Скопировать пример") { getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Пример",c.example));toast("Пример скопирован") },top=12)
            },top=12)
            add(panel().apply {
                add(text("ПРОСТЫМИ СЛОВАМИ",11,mint,true));add(text(c.plain.ifBlank {c.note},16),top=10)
                add(button("▷ Озвучить объяснение") { recording.stop();audio.extra(c.explanationAudio,"Объяснение",store.speed) },top=10)
                add(text(if(c.analogy.isBlank()) "Аналогия раздела" else "Аналогия",12,accent,true),top=14)
                add(text(c.analogy.ifBlank {LearningHints.analogy(c.categoryId)},16,muted),top=8)
                add(button("▷ Озвучить аналогию") { recording.stop();audio.extra(c.analogyAudio,"Аналогия",store.speed) },top=10)
                add(text("Типичная ошибка",12,accent,true),top=14);add(text(c.mistake.ifBlank {LearningHints.mistake(c.categoryId)},16,muted),top=8)
                add(text(c.check,16),top=14)
            },top=12)
            val assessment=column().apply { visibility=View.GONE }
            listOf(Triple("Ответил самостоятельно",2,false),Triple("Частично",1,false),Triple("Нужна подсказка",1,true),Triple("Не получилось",0,false)).forEach { (title,score,assisted) ->
                assessment.add(button(title) { store.assess(c.id,score,assisted,"self");if(score<2) store.mistakes[c.id]=title else store.mistakes.remove(c.id);save();toast("Самооценка сохранена. Учебная отметка не изменена.") },top=8)
            }
            add(button("Самопроверка ответа") { assessment.visibility=if(assessment.visibility==View.VISIBLE) View.GONE else View.VISIBLE },top=12);add(assessment)
            add(button("Моя заметка") { NoteEditor.show(this@MainActivity,c.id,store) { save() } },top=8)
        };content.add(answerBox!!)
        val footer=column().apply { setPadding(dp(20),dp(8),dp(20),dp(10));setBackgroundColor(Color.rgb(20,23,31)) }
        val phase=row();phaseText=text("Готово к прослушиванию",12,muted);phase.addView(phaseText,LinearLayout.LayoutParams(0,-2,1f));positionText=text("${queue.shown} / ${queue.total}",12,muted);phase.addView(positionText);footer.add(phase)
        val navigation=row()
        navigation.addView(button("‹") { previousCard() }.apply { contentDescription="Предыдущая карточка" },LinearLayout.LayoutParams(dp(48),dp(48)))
        playButton=button("▶ Слушать",true) { if(audio.active) audio.toggle() else startAudio() }
        navigation.addView(playButton,LinearLayout.LayoutParams(0,-2,1f).apply {marginStart=dp(8);marginEnd=dp(8)})
        navigation.addView(button("›") { nextCard() }.apply {contentDescription="Следующая карточка"},LinearLayout.LayoutParams(dp(48),dp(48)));footer.add(navigation,top=6)
        footer.add(AudioSeekView(this@MainActivity,audio),top=2)
        val mark=if(compact()) column() else row()
        if(filter=="LEARNED") mark.action(button("Вернуть в обучение",true) {setStatus(c,CardStatus.NEW);nextCard()})
        else {
            mark.action(button(if(store.status(c.id)==CardStatus.HARD) "Уже не сложно" else "Сложно") { val value=if(store.status(c.id)==CardStatus.HARD) CardStatus.NEW else CardStatus.HARD;setStatus(c,value);if(filter=="HARD" && value==CardStatus.NEW) nextCard() else refreshMarkButton(mark,c) })
            mark.action(button("✓ Запомнил",true) {setStatus(c,CardStatus.LEARNED);nextCard()},8)
        };footer.add(mark,top=6)
        if(compact()) content.add(footer,top=14) else root.addView(footer)
        if(restored!=null) {
            restored.optJSONObject("audio")?.let { audio.restore(it,repo.cards) }
            studyScroll?.post { studyScroll?.scrollTo(0,restored.optInt("scroll")) }
        } else if(autoplay && store.listening) startAudio()
        saveStudy()
    }
    private fun refreshRepeatButtons(row: LinearLayout) { for(i in 0 until row.childCount) (row.getChildAt(i) as MaterialButton).apply { val chosen=tag==store.repetitions;isChecked=chosen;backgroundTintList=ColorStateList.valueOf(if(chosen) accent else surface);setTextColor(if(chosen) Color.rgb(32,22,58) else ink) } }
    private fun LinearLayout.action(v: View, margin: Int=0) { if(orientation==LinearLayout.VERTICAL) add(v,top=margin) else weight(v,margin) }
    private fun refreshMarkButton(row: LinearLayout,c: Card) { (row.getChildAt(0) as MaterialButton).text=if(store.status(c.id)==CardStatus.HARD) "Уже не сложно" else "Сложно" }
    private fun switchMode(listening: Boolean) { audio.stop();store.listening=listening;save();displayCurrent(listening) }
    private fun reveal() { recording.stop();revealed=true;answerBox?.visibility=View.VISIBLE;revealButton?.visibility=View.GONE;hintText?.visibility=View.GONE;audio.reveal();studyScroll?.post { studyScroll?.smoothScrollTo(0,(answerBox?.top ?: 0)-dp(8)) } }
    private fun startAudio() { recording.stop();current?.let { audio.start(it,store.repetitions,revealed,store.thinkingSeconds,store.speed,store.autoNext && filter!="LEARNED",store.includeUnderstanding,store.answerLevel=="detailed") } }
    private fun setStatus(c: Card,status: CardStatus) {
        store.statuses[c.id]=status;if(status==CardStatus.LEARNED) store.mistakes.remove(c.id);save()
        val remains=eligible().any { it.id==c.id };queue.update(c.id,if(remains) weight(c) else 0)
        statusText?.text=statusName(status);statusText?.setTextColor(statusColor(status));positionText?.text="${queue.shown} / ${queue.total}"
    }
    private fun nextCard() {
        audio.stop();val previous=queue.current
        if(queue.next()==null) {
            val remaining=eligible()
            if(remaining.isEmpty()) { completed();return }
            queue.begin(remaining.associate { it.id to weight(it) },avoid=previous)
        };displayCurrent(true)
    }
    private fun previousCard() { audio.stop();queue.previous(eligible().map { it.id }.toSet());displayCurrent(false) }
    private fun completed() {
        checkpoint.clear("study");audio.stop();current=null;clear("COMPLETE");header("Тренировка",back=true)
        val content=body();content.add(panel(Color.rgb(34,47,46)).apply {
            add(text("✓",48,mint,true));add(text(if(filter=="HARD") "Сложных карточек нет" else if(filter=="LEARNED") "Здесь пока пусто" else "Отличная работа!",26,ink,true),top=12)
            add(text(if(filter=="ACTIVE" || filter=="NEW") "Все карточки в выбранной теме запомнены. Можно выбрать другую тему или вернуться к изученному." else "Выберите другой раздел или продолжите обычное обучение.",16,muted),top=12)
            add(button("Выбрать тему",true) { home() },top=20)
            add(button("Просмотреть запомненные") { catalogStatus="LEARNED";catalogCategory="";query="";catalog() },top=10)
        },top=20)
    }
    private fun catalog() {
        saveStudy();audio.stop();current=null;clear("CATALOG");header("Все карточки","Найдите вопрос или вернитесь к изученному")
        val content=body()
        val search=EditText(this).apply { setSingleLine();hint="Поиск по вопросам и ответам";textSize=15f;setTextColor(ink);setHintTextColor(muted);background=shape(surface,14);setPadding(dp(16),dp(10),dp(16),dp(10));setText(query) };content.add(search,56)
        val catButton=button(if(catalogCategory.isEmpty()) "Тема: все" else titles[catalogCategory.toInt()-1]) {
            val choices=listOf("Все темы")+titles
            MaterialAlertDialogBuilder(this).setTitle("Тема").setItems(choices.toTypedArray()) { _,index -> catalogCategory=if(index==0) "" else index.toString().padStart(2,'0');catalog() }.show()
        };content.add(catButton,top=12)
        val chips=row();listOf("ALL" to "Все","NEW" to "Новые","HARD" to "Сложные","LEARNED" to "Запомнил").forEachIndexed { i,(key,title) -> chips.weight(button(title,catalogStatus==key) { catalogStatus=key;catalog() }.apply { textSize=12f;minWidth=0;minimumWidth=0;setPadding(dp(4),0,dp(4),0) },if(i>0)4 else 0) };content.add(chips,top=10)
        val results=column();content.add(results,top=12)
        fun updateResults() {
            results.removeAllViews()
            val found=repo.cards.filter { c -> (catalogCategory.isEmpty() || c.categoryId==catalogCategory) && (catalogStatus=="ALL" || store.status(c.id).name==catalogStatus) && (c.question.contains(query,true) || c.answer.contains(query,true)) }
            results.add(text("Найдено: ${found.size}",13,muted),top=4)
            if(found.isEmpty()) results.add(text("Ничего не найдено. Измените запрос или фильтры.",17,ink),top=24)
            found.forEach { c -> results.add(panel().apply {
                add(text("${c.number.toString().padStart(3,'0')}  ·  ${statusName(store.status(c.id))}",11,statusColor(store.status(c.id)),true))
                add(text(c.question,17,ink,true),top=8)
                add(text(titles[c.categoryId.toInt()-1],12,muted),top=8)
                isFocusable=true;isClickable=true;setOnClickListener {
                    getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(search.windowToken,0)
                    openSession(catalogCategory,if(store.status(c.id)==CardStatus.LEARNED) "LEARNED" else if(catalogStatus=="ALL") "ACTIVE" else catalogStatus,c.id,false)
                }
            },top=10) }
        }
        search.addTextChangedListener(object: android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?,start: Int,count: Int,after: Int) {}
            override fun onTextChanged(s: CharSequence?,start: Int,before: Int,count: Int) { query=s.toString();updateResults() }
            override fun afterTextChanged(e: android.text.Editable?) {}
        });updateResults();nav("CATALOG")
    }
    private fun settings() {
        saveStudy();audio.stop();clear("SETTINGS");header("Настройки","Учитесь в своём темпе")
        val content=body()
        val sound=panel().apply {
            add(text("Обучение и озвучка",20,ink,true))
            add(text("Мужской голос Andrew · весь звук офлайн",13,muted),top=6)
            add(button("Ответ при прослушивании: "+if(store.answerLevel=="detailed") "подробный" else "короткий") {
                MaterialAlertDialogBuilder(this@MainActivity).setTitle("Уровень ответа").setItems(arrayOf("Короткий ответ","Подробный разбор")) { _,i->store.answerLevel=if(i==0) "short" else "detailed";save();settings() }.show()
            },top=10)
            add(text("После изменения настроек остановите и заново запустите непрерывное прослушивание.",13,muted),top=6)
            add(SwitchMaterial(this@MainActivity).apply { text="Читать объяснение и аналогию после ответа";isChecked=store.includeUnderstanding;setTextColor(ink);setOnCheckedChangeListener { _,v -> store.includeUnderstanding=v;save() } },top=14)
            add(SwitchMaterial(this@MainActivity).apply { text="Режим прослушивания";isChecked=store.listening;setTextColor(ink);setOnCheckedChangeListener { _,value -> store.listening=value;save() } },top=14)
            add(text("Выключен — сначала отвечаете сами, затем открываете ответ.",13,muted),top=2)
            add(SwitchMaterial(this@MainActivity).apply { text="Автоматически следующая карточка";isChecked=store.autoNext;setTextColor(ink);setOnCheckedChangeListener { _,value -> store.autoNext=value;save() } },top=10)
            add(button("Повторов карточки: ×${store.repetitions}") {
                MaterialAlertDialogBuilder(this@MainActivity).setTitle("Повторять вопрос и ответ").setItems((1..5).map { "×$it" }.toTypedArray()) { _,i -> store.repetitions=i+1;save();settings() }.show()
            },top=12)
            add(button("Пауза перед ответом: ${store.thinkingSeconds} с") {
                val options=listOf(0,3,5,10,15,20,30)
                MaterialAlertDialogBuilder(this@MainActivity).setTitle("Время подумать").setItems(options.map { "$it секунд" }.toTypedArray()) { _,i -> store.thinkingSeconds=options[i];save();settings() }.show()
            },top=10)
            add(button("Скорость голоса: ${String.format(Locale.US,"%.2f",store.speed)}×") {
                val values=listOf(.75f,1f,1.25f,1.5f)
                MaterialAlertDialogBuilder(this@MainActivity).setTitle("Скорость").setItems(values.map { "${it}×" }.toTypedArray()) { _,i -> store.speed=values[i];save();settings() }.show()
            },top=10)
        };content.add(sound)
        content.add(panel().apply {
            add(text("Ваш прогресс",20,ink,true));add(text("Сохраните копию перед переустановкой. Импорт добавляет статусы из файла; остальные карточки сохраняются.",14,muted),top=8)
            add(button("Экспортировать прогресс") { startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE);type="application/json";putExtra(Intent.EXTRA_TITLE,"python-middle-progress.json") },701) },top=14)
            add(button("Импортировать прогресс") { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE);type="application/json" },702) },top=10)
            add(button("Сбросить прогресс темы") {
                MaterialAlertDialogBuilder(this@MainActivity).setTitle("Выберите тему").setItems(titles.toTypedArray()) { _,i -> reset(repo.cards.filter { it.categoryId.toInt()==i+1 },titles[i]) { settings() } }.show()
            },top=10)
            add(button("Сбросить весь прогресс") { reset(repo.cards,"всего набора") { settings() } },top=10)
        },top=14)
        content.add(panel().apply {
            add(text("Как запоминать с пониманием",20,ink,true))
            add(text("1. Услышьте вопрос и сформулируйте ответ.\n2. Сравните с образцом, разберите пример.\n3. Ответьте на вопрос самопроверки.\n4. Отметьте «Сложно» или «Запомнил».",15,muted),top=12)
            add(text("Сложные появляются трижды за учебный раунд. Запомненные исключаются, пока вы сами не вернёте их в обучение.",14,muted),top=12)
            add(text("${repo.cards.size} карточек · версия 5.0\nБез интернета, рекламы и аккаунта.\nНепрерывное прослушивание работает с выключенным экраном.",13,muted),top=16)
        },top=14);nav("SETTINGS")
    }
    private fun reset(cards: List<Card>,label: String,after: ()->Unit) {
        audio.pause()
        MaterialAlertDialogBuilder(this).setTitle("Сбросить прогресс?").setMessage("Для $label: ${cards.size} карточек. Отметки «Запомнил» и «Сложно» будут удалены. Настройки голоса сохранятся.")
            .setNegativeButton("Отмена",null).setPositiveButton("Сбросить") { _,_ -> cards.forEach { store.statuses.remove(it.id) };save();toast("Прогресс сброшен");after() }.show()
    }
    @Deprecated("Activity result API compatibility")
    override fun onActivityResult(requestCode: Int,resultCode: Int,data: Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(resultCode!=Activity.RESULT_OK) return
        val uri=data?.data ?: return
        scope.launch {
            try {
                if(requestCode==701) {
                    val raw=store.export()
                    withContext(Dispatchers.IO) { contentResolver.openOutputStream(uri,"wt")?.bufferedWriter()?.use { it.write(raw) } ?: error("Нет доступа к файлу") }
                    toast("Прогресс экспортирован")
                } else if(requestCode==702) {
                    val raw=withContext(Dispatchers.IO) { contentResolver.openInputStream(uri)?.use { input ->
                        val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
                        while(true) { val n=input.read(buffer);if(n<0) break;require(out.size()+n<=8*1024*1024) { "Файл слишком большой" };out.write(buffer,0,n) };out.toString("UTF-8")
                    } ?: error("Нет доступа к файлу") }
                    val result=store.import(raw,repo.cards.map { it.id }.toSet());save();toast("Восстановлено: ${result.first}. Неизвестных карточек: ${result.second}")
                }
            } catch (_: Exception) { toast("Не удалось обработать файл. Проверьте формат и доступ.") }
        }
    }
    private fun toast(value: String) { Toast.makeText(this,value,Toast.LENGTH_LONG).show() }
    @Deprecated("Back compatibility") override fun onBackPressed() { if(screen!="HOME") home() else super.onBackPressed() }
    private fun openAdvanced(page:String,resume:Boolean=false) {
        recording.stop();audio.stop();PlaybackService.command(this,PlaybackService.STOP);returningFromPractice=true
        startActivity(Intent(this,V5Activity::class.java).putExtra("page",page).putExtra("resume",resume))
    }
    private fun saveStudy() {
        if(!::checkpoint.isInitialized || !::audio.isInitialized || screen!="STUDY" || current==null) return
        checkpoint.save("study","Тренировка · "+current!!.category,JSONObject().put("category",category).put("filter",filter).put("queue",queue.toJson()).put("revealed",revealed).put("scroll",studyScroll?.scrollY ?: 0).put("audio",audio.snapshot()))
    }
    private fun resumeLast():Boolean {
        val saved=checkpoint.read() ?: return false
        return try {
            when(saved.getString("owner")) {
                "study"->{
                    val p=saved.getJSONObject("payload");category=p.getString("category");filter=p.getString("filter");require(filter in setOf("ACTIVE","HARD","NEW","LEARNED"))
                    require(queue.restoreJson(p.getJSONObject("queue"),eligible().map { it.id }.toSet()));restoringStudy=p;displayCurrent(false)
                }
                "practice"->{returningFromPractice=true;PlaybackService.command(this,PlaybackService.STOP);startActivity(Intent(this,PracticeActivity::class.java).putExtra("resume",true))}
                "advanced"->openAdvanced("",true)
                "radio"->{PlaybackService.start(this,true)}
                else->return false
            };true
        } catch(_:Exception) { checkpoint.clear(saved.optString("owner"));false }
    }
    private fun startRadio() {
        recording.stop();audio.stop()
        if(android.os.Build.VERSION.SDK_INT>=33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),801)
        PlaybackService.start(this)
    }
    override fun onStart() { super.onStart();checkpointTicks.start();RadioState.listener={ radioLabel?.text=(RadioState.card?.question?.plus("\n") ?: "")+RadioState.label } }
    override fun onResume() {
        super.onResume()
        if(returningFromPractice && ::repo.isInitialized) {
            returningFromPractice=false
            scope.launch { withContext(Dispatchers.IO) { store.load() };home() }
        }
    }
    override fun onStop() { if(::audio.isInitialized) audio.pause();saveStudy();checkpointTicks.stop();super.onStop();RadioState.listener=null;if(::recording.isInitialized) recording.stop();if(::audio.isInitialized) audio.pause() }
    override fun onDestroy() { checkpointTicks.stop();if(::recording.isInitialized) recording.close(); if(::audio.isInitialized) audio.close();saves.close();scope.cancel();super.onDestroy() }
    override fun onSaveInstanceState(out: Bundle) { if(screen=="STUDY") { current?.let { out.putString("card",it.id) };out.putString("category",category);out.putString("filter",filter) };super.onSaveInstanceState(out) }
}
