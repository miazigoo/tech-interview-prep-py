package com.python.interviewcards
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class V3DeviceTest {
    private val ins=InstrumentationRegistry.getInstrumentation()
    @Test fun understandingTracksAreInsideEachRepetition() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val done=CountDownLatch(1);val tracks=CopyOnWriteArrayList<String>();lateinit var audio:AudioController
            scenario.onActivity { activity ->
                audio=AudioController(activity,{ label,_->if(label.startsWith("Карточка прослушана")) done.countDown() },{}, { done.countDown() },{ins.context.assets.openFd("short.mp3")},{ tracks.add(it) })
                val c=Card("test",1,"01","test","q","a","code","note","check","q.mp3","a.mp3",explanationAudio="explain.mp3",analogyAudio="analogy.mp3")
                audio.start(c,2,true,0,1f,false,true)
            }
            assertTrue(done.await(15,TimeUnit.SECONDS));scenario.onActivity { audio.close() }
            assertEquals(listOf("q.mp3","a.mp3","explain.mp3","analogy.mp3","q.mp3","a.mp3","explain.mp3","analogy.mp3"),tracks.toList())
        }
    }
    @Test fun hardcodedSqlAnswerFailsHiddenDatasets() {
        val e=SqlTrainer(ins.targetContext);val t=e.tasks.getJSONObject(0)
        assertFalse(e.execute("SELECT 'Анна' UNION ALL SELECT 'Борис' UNION ALL SELECT 'Вера' UNION ALL SELECT 'Глеб'",t).correct)
        for(i in 0 until e.tasks.length()) { val task=e.tasks.getJSONObject(i);assertTrue(task.getString("id"),e.execute(task.getString("solution"),task).correct) }
        assertTrue(e.execute(t.getString("solution"),t,2).correct)
    }
    @Test fun mistakesAndUnderstandingSettingSurviveRoundTrip()=kotlinx.coroutines.runBlocking {
        val s=ProgressStore(ins.targetContext);s.load();val before=s.snapshot()
        try {
            s.mistakes["sql-001"]="Подсказка";s.includeUnderstanding=false;s.interviewDate="2026-12-20";s.persist(s.snapshot())
            val again=ProgressStore(ins.targetContext);again.load()
            assertEquals("Подсказка",again.mistakes["sql-001"]);assertFalse(again.includeUnderstanding);assertEquals("2026-12-20",again.interviewDate)
        } finally {s.persist(before)}
    }
    @Test fun deniedMicrophoneDoesNotDeleteExistingAnswer() {
        val ctx=ins.targetContext
        val dir=java.io.File(ctx.filesDir,"voice_answers").apply {mkdirs()};val f=java.io.File(dir,"test.m4a");val old="saved-answer".toByteArray();f.writeBytes(old)
        try {
            val voice=VoiceAnswer(ctx,{})
            if(ctx.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=android.content.pm.PackageManager.PERMISSION_GRANTED) {
                try {voice.start("test");fail("Must request permission") } catch(_:IllegalArgumentException) {}
                assertArrayEquals(old,f.readBytes())
            }
            voice.close()
        } finally { f.delete() }
    }
}
