package com.python.interviewcards

import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject
import kotlinx.coroutines.runBlocking

@RunWith(AndroidJUnit4::class)
class V4DeviceTest {
    private val ins=InstrumentationRegistry.getInstrumentation()
    @Test fun notesEvidenceAndRoutesPersistAndExportWithoutChangingLearned()=runBlocking {
        val s=ProgressStore(ins.targetContext);s.load();val before=s.snapshot()
        try {
            val id="py-middle-002"
            s.statuses[id]=CardStatus.LEARNED;s.notes[id]="Мой пример\nОбъект и ссылка — разные понятия"
            s.viewed(id);s.assess(id,1,true,"followup");s.routeProgress["path-objects"]=2
            s.persist(s.snapshot())
            val loaded=ProgressStore(ins.targetContext);loaded.load()
            assertEquals(s.notes[id],loaded.notes[id]);assertEquals(s.views[id],loaded.views[id])
            assertEquals(s.evidence[id],loaded.evidence[id]);assertEquals(2,loaded.routeProgress["path-objects"])
            assertEquals(CardStatus.LEARNED,loaded.status(id))
            val target=ProgressStore(ins.targetContext);target.import(s.export(),CardRepository(ins.targetContext).cards.map { it.id }.toSet())
            assertEquals(s.notes[id],target.notes[id]);assertEquals(s.evidence[id],target.evidence[id])
            assertEquals(2,target.routeProgress["path-objects"]);assertEquals(CardStatus.LEARNED,target.status(id))
        } finally {s.persist(before)}
    }
    @Test fun malformedNewFieldsAreAtomicAndLegacyDoesNotInventReadiness() {
        val s=ProgressStore(ins.targetContext);val id="py-middle-001"
        s.statuses[id]=CardStatus.LEARNED;s.notes[id]="Keep me"
        val before=s.export()
        val malformed=listOf(
            JSONObject(before).put("notes",JSONObject().put(id,"x".repeat(10001))),
            JSONObject(before).put("evidence",JSONObject().put(id,JSONObject().put("score",2).put("assisted",true).put("source","test").put("at",1))),
            JSONObject(before).put("routes",JSONObject().put("path-objects",999)))
        malformed.forEach { json ->
            try { s.import(json.toString(),setOf(id));fail("Invalid import accepted") } catch(_:IllegalArgumentException) {}
            assertEquals(before,s.export())
        }
        val legacy=ProgressStore(ins.targetContext)
        legacy.import("""{"schema_version":1,"deck_id":"python-middle-ru","statuses":{"py-middle-001":"LEARNED"}}""",setOf(id))
        assertNull(Readiness.calculate(setOf(id),legacy.evidence).percent)
    }
    @Test fun realPlayerSeekPreservesPauseAndPhraseResumes() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var audio:AudioController
            scenario.onActivity { activity ->
                audio=AudioController(activity,{_,_->},{},{fail(it)})
                audio.single(CardRepository(activity).cards.first(),true,1f)
            }
            var ready=false
            repeat(50) { if(!ready) { scenario.onActivity { ready=audio.canSeek() };Thread.sleep(100) } }
            assertTrue("Real MP3 did not prepare",ready)
            scenario.onActivity { audio.pause();assertTrue(audio.durationMillis()>12000);audio.seekTo(12000) }
            Thread.sleep(300)
            scenario.onActivity { assertTrue(audio.paused);assertTrue(kotlin.math.abs(audio.positionMillis()-12000)<600);audio.seekBy(-10000) }
            Thread.sleep(300)
            scenario.onActivity { assertTrue(audio.paused);assertTrue(audio.positionMillis() in 1400..2600);audio.repeatPhrase();assertFalse(audio.paused) }
            Thread.sleep(300)
            scenario.onActivity { assertTrue(audio.active);audio.stop();assertFalse(audio.canSeek());assertEquals(0,audio.positionMillis());audio.close() }
        }
    }
    @Test fun backgroundMediaSessionSupportsSeekingWhileActivityIsClosed() {
        val ctx=ins.targetContext
        ins.uiAutomation.adoptShellPermissionIdentity("android.permission.MEDIA_CONTENT_CONTROL")
        try {
            val scenario=ActivityScenario.launch(MainActivity::class.java)
            scenario.onActivity { PlaybackService.start(it) }
            var ready=false
            repeat(60) { if(!ready) { ins.runOnMainSync { ready=RadioState.controller?.canSeek()==true };Thread.sleep(100) } }
            assertTrue(ready)
            val manager=ctx.getSystemService(android.media.session.MediaSessionManager::class.java)
            val media=manager.getActiveSessions(null).first { it.packageName==ctx.packageName }
            media.transportControls.pause();Thread.sleep(200);scenario.close()
            val duration=media.metadata!!.getLong(android.media.MediaMetadata.METADATA_KEY_DURATION)
            val destination=minOf(1500L,duration/2)
            media.transportControls.seekTo(destination);Thread.sleep(300)
            assertEquals(android.media.session.PlaybackState.STATE_PAUSED,media.playbackState!!.state)
            ins.runOnMainSync { assertTrue(kotlin.math.abs(RadioState.controller!!.positionMillis()-destination)<600) }
            assertTrue(RadioState.running)
        } finally {
            PlaybackService.command(ctx,PlaybackService.STOP);Thread.sleep(300)
            ins.uiAutomation.dropShellPermissionIdentity()
        }
    }
}
