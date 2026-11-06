package com.python.interviewcards

import android.Manifest
import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.*
import android.os.Build
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class VoiceAnswer(private val context:Context,private val changed:(String)->Unit) {
    private val manager=context.getSystemService(AudioManager::class.java)
    private val attributes=AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    private val focus=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attributes).setOnAudioFocusChangeListener { if(it!=AudioManager.AUDIOFOCUS_GAIN) stop() }.build()
    private val noisy=object:BroadcastReceiver() { override fun onReceive(c:Context?,i:Intent?) { stop() } }
    init { androidx.core.content.ContextCompat.registerReceiver(context,noisy,IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED) }
    private var recorder:MediaRecorder?=null
    private var player:MediaPlayer?=null
    private var temporary:File?=null
    private var destination:File?=null
    val recording get()=recorder!=null
    private fun file(id:String):File { require(id.matches(Regex("[a-zA-Z0-9_-]+")));return File(File(context.filesDir,"voice_answers").apply { mkdirs() },"$id.m4a") }
    fun exists(id:String)=file(id).exists()
    fun start(id:String) {
        require(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED) { "Нужен доступ к микрофону" }
        stop();require(manager.requestAudioFocus(focus)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { "Не удалось получить аудиофокус" };destination=file(id);temporary=File.createTempFile("answer-",".m4a",context.cacheDir)
        @Suppress("DEPRECATION") val r=if(Build.VERSION.SDK_INT>=31) MediaRecorder(context) else MediaRecorder()
        try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC);r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);r.setAudioSamplingRate(44100);r.setAudioEncodingBitRate(96000)
            r.setOutputFile(temporary!!.absolutePath);r.setMaxDuration(120000)
            r.setOnInfoListener { _,what,_ -> if(what==MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) stop() }
            r.setOnErrorListener { _,_,_-> stop(false) }
            r.prepare();r.start();recorder=r;changed("Идёт запись · максимум 2 минуты")
        } catch(e:Exception) { r.release();manager.abandonAudioFocusRequest(focus);temporary?.delete();temporary=null;destination=null;throw e }
    }
    fun stop(save:Boolean=true) {
        player?.release();player=null;manager.abandonAudioFocusRequest(focus)
        val r=recorder ?: return
        recorder=null
        var valid=false
        try { r.stop();valid=save && (temporary?.length() ?: 0)>100 } catch(_:Exception) {} finally { r.release() }
        if(valid) {
            try {
                Files.move(temporary!!.toPath(),destination!!.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE)
                changed("Запись сохранена на устройстве")
            } catch(_:Exception) { changed("Не удалось сохранить новую запись. Предыдущая сохранена.") }
        } else changed("Запись не сохранена: слишком короткая или возникла ошибка")
        temporary?.delete();temporary=null;destination=null
    }
    fun play(id:String) {
        stop();val f=file(id);require(f.exists()) { "Сначала запишите ответ" }
        val p=MediaPlayer();player=p
        try {
            p.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            p.setDataSource(f.path);p.setOnPreparedListener { if(player===p) { if(manager.requestAudioFocus(focus)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED) it.start() else { stop();changed("Аудиофокус недоступен") } } }
            p.setOnCompletionListener { if(player===p) { p.release();player=null;manager.abandonAudioFocusRequest(focus);changed("Прослушано") } }
            p.setOnErrorListener { _,_,_-> stop();changed("Не удалось открыть запись");true };p.prepareAsync();changed("Прослушивание собственного ответа")
        } catch(e:Exception) { p.release();player=null;throw e }
    }
    fun close() { stop();context.unregisterReceiver(noisy) }
    fun delete(id:String) { stop();file(id).delete();changed("Запись удалена") }
}
