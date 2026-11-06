package com.python.interviewcards

import android.content.*
import android.media.*
import android.os.*

class AudioController(private val context: Context, private val update: (String, Boolean) -> Unit,
    private val finished: () -> Unit, private val error: (String) -> Unit,
    private val openAsset: (String) -> android.content.res.AssetFileDescriptor = { context.assets.openFd(it) },
    private val trackStarted: (String) -> Unit = {},
    private val trackCompleted: (String) -> Unit = {}) {
    private val handler = Handler(Looper.getMainLooper())
    private val manager = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener { change -> if (change != AudioManager.AUDIOFOCUS_GAIN) pause() }.build()
    private val noisy = object : BroadcastReceiver() { override fun onReceive(c: Context?, i: Intent?) { pause() } }
    private var player: MediaPlayer? = null
    private var playingPath = ""
    private var prepared = false
    private var lastPosition=0
    private var lastDuration=0
    private val cues by lazy { runCatching { org.json.JSONObject(context.assets.open("audio_cues.json").bufferedReader().use { it.readText() }) }.getOrDefault(org.json.JSONObject()) }
    private var epoch = 0
    private var task: (() -> Unit)? = null
    private var deadline = 0L
    private var remaining = 0L
    private var card: Card? = null
    private var plan: PlaybackPlan? = null
    private var revealed = false
    private var thinking = 3000L
    private var speed = 1f
    private var autoNext = false
    private var includeUnderstanding=false
    private var kind="extra"
    private var restorePosition=0
    private var detailed=false
    private var label = "Нажмите «Слушать карточку»"
    var paused = false; private set
    var active = false; private set
    init {
        androidx.core.content.ContextCompat.registerReceiver(context,noisy,IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
    }
    private fun notifyState() {
        val suffix = plan?.let { " · ${it.cycle} из ${it.repetitions}" } ?: ""
        update((if (paused) "Пауза · " else "") + label + suffix, active && !paused)
    }
    fun start(card: Card, repetitions: Int, revealed: Boolean, thinkingSeconds: Int, speed: Float, autoNext: Boolean, includeUnderstanding:Boolean=false,detailed:Boolean=false) {
        stop(); this.card = card; this.revealed = revealed; thinking = thinkingSeconds*1000L; this.speed = speed; this.autoNext = autoNext;this.includeUnderstanding=includeUnderstanding
        this.detailed=detailed;kind="plan";plan = PlaybackPlan(repetitions); active = true; playQuestion()
    }
    fun single(card: Card, answer: Boolean, speed: Float) {
        stop(); this.card = card; this.speed = speed; active = true
        kind="single";label = if (answer) "Ответ" else "Вопрос"
        track(if (answer) card.answerAudio else card.questionAudio) { active = false; label = "Прослушано"; manager.abandonAudioFocusRequest(focus); notifyState() }
    }
    fun reveal() {
        revealed = true
        if (plan?.phase == Phase.WAIT_REVEAL && active) {
            if (paused) { task = { playAnswer() }; remaining = 0; label = "Ответ готов"; notifyState() }
            else playAnswer()
        }
    }
    private fun playQuestion() {
        val c = card ?: return
        label = "Вопрос"
        track(c.questionAudio) {
            when (plan?.questionEnded(revealed)) {
                Phase.THINKING -> { label = "Время на свой ответ"; delay(thinking) { playAnswer() } }
                Phase.WAIT_REVEAL -> { label = "Ответьте сами и откройте ответ"; notifyState() }
                else -> Unit
            }
        }
    }
    private fun playAnswer() {
        val c = card ?: return
        if(plan?.phase!=Phase.ANSWER) plan?.answerStarted(); label = if(detailed) "Подробный ответ" else "Ответ"
        val path=if(detailed) LearningV5(context).card(c.id).optString("detailed_audio",c.answerAudio) else c.answerAudio
        track(path) { afterAnswer(c) }
    }
    private fun afterAnswer(c:Card) {
        if(detailed) { finishCycle();return }
        playTail(c,0)
    }
    private fun playTail(c:Card,start:Int) {
        val tail=if(includeUnderstanding) listOf("Объяснение" to c.explanationAudio,"Аналогия" to c.analogyAudio).filter { it.second.isNotBlank() } else emptyList()
        fun playTail(index:Int) {
            if(index>=tail.size) { finishCycle();return }
            label=tail[index].first;track(tail[index].second) { playTail(index+1) }
        }
        playTail(start)
    }
    private fun finishCycle() {
        when(plan?.answerEnded()) {
            Phase.BETWEEN->{ label="Пауза между повторами";delay(1000) { plan?.nextCycle();playQuestion() } }
            Phase.FINISHED->{
                label="Карточка прослушана"
                if(autoNext) { label="Далее через 2 секунды";delay(2000) { stop();finished() } }
                else { active=false;manager.abandonAudioFocusRequest(focus);notifyState() }
            }
            else->Unit
        }
    }
    fun extra(path:String,title:String,speed:Float) {
        stop();this.speed=speed;active=true;kind="extra";label=title
        track(path) { active=false;label="Прослушано";manager.abandonAudioFocusRequest(focus);notifyState() }
    }
    private fun track(path: String, done: () -> Unit) {
        releasePlayer();lastPosition=0;lastDuration=0; val version = epoch; playingPath=path
        try {
            val p = MediaPlayer(); player = p; prepared = false
            p.setAudioAttributes(attributes)
            openAsset(path).use { p.setDataSource(it.fileDescriptor,it.startOffset,it.length) }
            p.setOnPreparedListener {
                if (version != epoch || player !== p) return@setOnPreparedListener
                prepared = true;lastDuration=p.duration;lastPosition=AudioNavigation.bounded(restorePosition.toLong(),lastDuration)
                if(restorePosition>0) p.seekTo(lastPosition.toLong(),MediaPlayer.SEEK_CLOSEST)
                restorePosition=0
                if (!paused) startPrepared(p)
                notifyState()
            }
            p.setOnSeekCompleteListener { if(version==epoch && player===p) notifyState() }
            p.setOnCompletionListener { if (version == epoch && player === p) { lastPosition=lastDuration;releasePlayer(); done(); trackCompleted(path) } }
            p.setOnErrorListener { _,_,_ -> if (version == epoch) fail(); true }
            p.prepareAsync(); notifyState()
        } catch (_: Exception) { fail() }
    }
    private fun startPrepared(p: MediaPlayer) {
        if (manager.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { paused = true; notifyState(); return }
        try { p.playbackParams = PlaybackParams().setSpeed(speed); p.start(); trackStarted(playingPath) } catch (_: Exception) { fail() }
    }
    private fun delay(ms: Long, action: () -> Unit) {
        task = action; remaining = ms
        notifyState()
        if (!paused) armDelay()
    }
    private fun armDelay() {
        deadline = SystemClock.uptimeMillis() + remaining
        val version = epoch
        handler.postDelayed({
            if (version == epoch && !paused) { val action = task; task = null; action?.invoke() }
        }, remaining)
    }
    fun snapshot():org.json.JSONObject? {
        if(!active) return null
        return org.json.JSONObject().put("kind",kind).put("card",card?.id).put("path",playingPath).put("position",positionMillis()).put("label",label)
            .put("cycle",plan?.cycle ?: 1).put("phase",plan?.phase?.name ?: "").put("repetitions",plan?.repetitions ?: 1)
            .put("revealed",revealed).put("thinking",thinking).put("speed",speed).put("auto",autoNext).put("understanding",includeUnderstanding).put("detailed",detailed)
            .put("remaining",if(task!=null && !paused) (deadline-SystemClock.uptimeMillis()).coerceAtLeast(0) else remaining)
    }
    /** Restores paused, never starts audio automatically after process death. */
    fun restore(j:org.json.JSONObject,cards:List<Card>):Boolean {
        stop()
        return try {
            val path=j.getString("path");require(path.startsWith("audio/") && !path.contains(".."))
            kind=j.getString("kind");card=cards.find { it.id==j.optString("card") };speed=j.getDouble("speed").toFloat().coerceIn(.75f,1.5f)
            thinking=j.optLong("thinking",3000).coerceIn(0,30000);autoNext=j.optBoolean("auto");revealed=j.optBoolean("revealed");includeUnderstanding=j.optBoolean("understanding");detailed=j.optBoolean("detailed")
            label=j.getString("label");restorePosition=j.getInt("position").coerceAtLeast(0);active=true;paused=true
            if(kind=="plan") {
                require(card!=null);val phase=Phase.valueOf(j.getString("phase"));if(phase !in setOf(Phase.QUESTION,Phase.ANSWER)) restorePosition=0;plan=PlaybackPlan(j.getInt("repetitions")).apply { restore(j.getInt("cycle"),phase) }
                when(phase) {
                    Phase.QUESTION->playQuestion()
                    Phase.ANSWER->when(label) { "Объяснение"->playTail(card!!,0);"Аналогия"->playTail(card!!,1);else->playAnswer() }
                    Phase.THINKING->delay(j.getLong("remaining").coerceIn(0,30000)) { playAnswer() }
                    Phase.WAIT_REVEAL->{ label="Ответьте сами и откройте ответ";notifyState() }
                    Phase.BETWEEN->delay(j.getLong("remaining").coerceIn(0,1000)) { plan?.nextCycle();playQuestion() }
                    Phase.FINISHED->delay(j.getLong("remaining").coerceIn(0,2000)) { stop();finished() }
                }
            } else { require(kind in setOf("single","extra"));track(path) { active=false;label="Прослушано";manager.abandonAudioFocusRequest(focus);notifyState() } }
            true
        } catch(_:Exception) { stop();false }
    }
    fun durationMillis():Int=lastDuration
    fun positionMillis():Int=if(prepared) runCatching { player?.currentPosition ?: lastPosition }.getOrDefault(lastPosition) else if(restorePosition>0) restorePosition else lastPosition
    fun canSeek():Boolean=prepared && player!=null && lastDuration>0
    fun seekTo(position:Long) { if(canSeek()) { val p=AudioNavigation.bounded(position,lastDuration);player?.seekTo(p.toLong(),MediaPlayer.SEEK_CLOSEST);lastPosition=p;notifyState() } }
    fun seekBy(milliseconds:Long) { seekTo(positionMillis().toLong()+milliseconds) }
    fun repeatPhrase() {
        if(!canSeek()) return
        val a=cues.optJSONObject(playingPath)?.optJSONArray("starts_ms")
        val starts=if(a==null) listOf(0) else (0 until a.length()).map { a.getInt(it) }
        seekTo(AudioNavigation.phrase(positionMillis(),starts).toLong())
        if(paused) resume()
    }
    fun toggle() { if (paused) resume() else pause() }
    fun pause() {
        if (!active || paused) return
        if (task != null) remaining = (deadline - SystemClock.uptimeMillis()).coerceAtLeast(0)
        handler.removeCallbacksAndMessages(null)
        paused = true
        if (prepared) runCatching { player?.pause() }
        manager.abandonAudioFocusRequest(focus); notifyState()
    }
    private fun resume() {
        if (!active) return
        paused = false
        if (task != null) armDelay() else if (prepared) player?.let { startPrepared(it) }
        notifyState()
    }
    fun stop() {
        epoch++; handler.removeCallbacksAndMessages(null); task = null; remaining = 0
        releasePlayer();lastPosition=0;lastDuration=0;restorePosition=0; plan = null; active = false; paused = false; label = "Готово к прослушиванию"
        manager.abandonAudioFocusRequest(focus); notifyState()
    }
    private fun fail() { stop(); label = "Не удалось открыть аудио"; notifyState(); error("Аудио недоступно. Можно читать ответ и перейти дальше.") }
    private fun releasePlayer() { prepared = false; player?.let { runCatching { it.release() } }; player = null }
    fun close() { stop(); context.unregisterReceiver(noisy) }
}
