package com.python.interviewcards

import android.app.*
import android.content.*
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.*
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*

object RadioState {
    var controller:AudioController?=null
    var running=false
    var paused=false
    var card: Card?=null
    var label="Готово к прогулке: вопросы и ответы подряд"
    var listener: (() -> Unit)?=null
    fun changed() { listener?.invoke() }
}

class PlaybackService: Service() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
    private lateinit var audio: AudioController
    private lateinit var media: MediaSession
    private lateinit var wake: PowerManager.WakeLock
    private lateinit var store: ProgressStore
    private lateinit var repo: CardRepository
    private val queue=LearningQueue()
    private var stopped=false
    private var ready=false
    private var generation=0
    private lateinit var checkpoint:Checkpoint
    private val checkpointTicks=CheckpointTicker { saveRadio() }
    companion object {
        const val START="radio.START";const val TOGGLE="radio.TOGGLE";const val NEXT="radio.NEXT";const val PREVIOUS="radio.PREVIOUS";const val STOP="radio.STOP"
        const val CHANNEL="interview_audio"
        fun start(context: Context,resume:Boolean=false) { ContextCompat.startForegroundService(context,Intent(context,PlaybackService::class.java).setAction(START).putExtra("resume",resume)) }
        fun command(context: Context,action: String) { if(RadioState.running) context.startService(Intent(context,PlaybackService::class.java).setAction(action)) }
    }
    override fun onCreate() {
        super.onCreate()
        checkpoint=Checkpoint(this);checkpointTicks.start()
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL,"Обучение с озвучкой",NotificationManager.IMPORTANCE_LOW))
        wake=getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"middle:audio").apply { setReferenceCounted(false) }
        store=ProgressStore(applicationContext);repo=CardRepository(this)
        media=MediaSession(this,"Middle").apply {
            setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
            setCallback(object: MediaSession.Callback() {
                override fun onPlay() { if(audio.paused) audio.toggle() }
                override fun onPause() { audio.pause() }
                override fun onSkipToNext() { advance(false) }
                override fun onSkipToPrevious() { advance(true) }
                override fun onSeekTo(pos:Long) { audio.seekTo(pos) }
                override fun onFastForward() { audio.seekBy(10000) }
                override fun onRewind() { audio.seekBy(-10000) }
                override fun onStop() { stopRadio() }
            })
            setSessionActivity(PendingIntent.getActivity(this@PlaybackService,0,Intent(this@PlaybackService,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            isActive=true
        }
        audio=AudioController(this,{ label,playing ->
            RadioState.label=label;RadioState.paused=!playing
            if(playing) wake.acquire(30*60*1000L) else if(wake.isHeld) wake.release()
            publish()
        },{ advance(false) },{ RadioState.label=it;stopRadio(it) })
        RadioState.controller=audio
    }
    override fun onStartCommand(intent: Intent?,flags: Int,startId: Int): Int {
        if(intent?.action==START) {
            RadioState.running=true;stopped=false;ready=false
            startForeground(42,notification())
            val token=++generation
            scope.launch {
                try { store.load() } catch (_: Exception) { stopRadio("Не удалось загрузить прогресс. Откройте приложение и повторите.");return@launch }
                if(stopped || token!=generation) return@launch
                ready=true
                val saved=if(intent.getBooleanExtra("resume",false)) checkpoint.read()?.takeIf { it.optString("owner")=="radio" }?.optJSONObject("payload") else null
                if(saved!=null && queue.restoreJson(saved.optJSONObject("queue") ?: org.json.JSONObject(),weights().keys)) {
                    RadioState.card=repo.cards.find { it.id==queue.current }
                    val restored=saved.optJSONObject("audio")?.let { audio.restore(it,repo.cards) }==true
                    if(!restored) { playCurrent();audio.pause() };publish()
                } else { queue.begin(weights());playCurrent();if(intent.getBooleanExtra("resume",false)) audio.pause() }
                saveRadio()
            }
        } else when(intent?.action) { TOGGLE->audio.toggle();NEXT->advance(false);PREVIOUS->advance(true);STOP->stopRadio() }
        return START_NOT_STICKY
    }
    private fun weights()=repo.cards.filter { store.status(it.id)!=CardStatus.LEARNED }.associate { it.id to if(store.status(it.id)==CardStatus.HARD) 3 else 1 }
    private fun advance(previous: Boolean) {
        if(stopped || !ready) return
        val old=queue.current
        if(previous) queue.previous(weights().keys) else if(queue.next()==null) queue.begin(weights(),avoid=old)
        playCurrent()
    }
    private fun playCurrent() {
        val c=repo.cards.find { it.id==queue.current }
        if(c==null) { stopRadio("Все карточки запомнены. Выберите проверку запомненного.");return }
        RadioState.card=c
        audio.start(c,store.repetitions,true,store.thinkingSeconds,store.speed,true,store.includeUnderstanding,store.answerLevel=="detailed")
        publish()
    }
    private fun saveRadio() {
        if(!::checkpoint.isInitialized || !ready || stopped || !RadioState.running) return
        checkpoint.save("radio","Непрерывное прослушивание",org.json.JSONObject().put("queue",queue.toJson()).put("audio",audio.snapshot()))
    }
    private fun action(name: String,code: Int)=PendingIntent.getService(this,code,Intent(this,PlaybackService::class.java).setAction(name),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    private fun notification(): Notification = Notification.Builder(this,CHANNEL)
        .setSmallIcon(R.drawable.ic_audio).setContentTitle(RadioState.card?.question ?: "Middle · непрерывное обучение")
        .setContentText(RadioState.label).setContentIntent(media.controller.sessionActivity)
        .setDeleteIntent(action(STOP,4)).setVisibility(Notification.VISIBILITY_PUBLIC).setOnlyAlertOnce(true).setOngoing(!RadioState.paused)
        .addAction(Notification.Action.Builder(android.R.drawable.ic_media_previous,"Назад",action(PREVIOUS,1)).build())
        .addAction(Notification.Action.Builder(if(RadioState.paused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause,if(RadioState.paused) "Продолжить" else "Пауза",action(TOGGLE,2)).build())
        .addAction(Notification.Action.Builder(android.R.drawable.ic_media_next,"Далее",action(NEXT,3)).build())
        .addAction(Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel,"Остановить",action(STOP,4)).build())
        .setStyle(Notification.MediaStyle().setMediaSession(media.sessionToken).setShowActionsInCompactView(0,1,2)).build()
    private fun publish() {
        if(!RadioState.running) return
        val state=if(RadioState.paused) PlaybackState.STATE_PAUSED else PlaybackState.STATE_PLAYING
        media.setPlaybackState(PlaybackState.Builder().setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_STOP or PlaybackState.ACTION_SEEK_TO or PlaybackState.ACTION_FAST_FORWARD or PlaybackState.ACTION_REWIND).setState(state,audio.positionMillis().toLong(),if(RadioState.paused) 0f else store.speed).build())
        media.setMetadata(MediaMetadata.Builder().putLong(MediaMetadata.METADATA_KEY_DURATION,audio.durationMillis().toLong()).putString(MediaMetadata.METADATA_KEY_TITLE,RadioState.card?.question ?: "Middle").putString(MediaMetadata.METADATA_KEY_ARTIST,"Python Middle · вопрос и ответ").build())
        getSystemService(NotificationManager::class.java).notify(42,notification());RadioState.changed()
    }
    private fun stopRadio(message: String="Прослушивание остановлено") {
        if(stopped) return
        checkpoint.clear("radio");stopped=true;generation++;RadioState.running=false
        audio.stop();RadioState.paused=false;RadioState.label=message;RadioState.changed()
        if(wake.isHeld) wake.release()
        stopForeground(STOP_FOREGROUND_REMOVE);stopSelf()
    }
    override fun onBind(intent: Intent?)=null
    override fun onDestroy() { saveRadio();checkpointTicks.stop();RadioState.controller=null;stopped=true;generation++;RadioState.running=false;audio.close();media.release();if(wake.isHeld) wake.release();scope.cancel();RadioState.changed();super.onDestroy() }
}
