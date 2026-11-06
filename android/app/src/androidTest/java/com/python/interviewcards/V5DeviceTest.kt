package com.python.interviewcards

import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking

@RunWith(AndroidJUnit4::class)
class V5DeviceTest {
    private val ins=InstrumentationRegistry.getInstrumentation()
    @Test fun realAudioRestoresAtPositionAndWaitsForUser() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario->
            lateinit var first:AudioController;lateinit var restored:AudioController;lateinit var saved:JSONObject
            val cards=CardRepository(ins.targetContext).cards
            scenario.onActivity { a->first=AudioController(a,{_,_->},{},{fail(it)});first.single(cards.first(),true,1f) }
            var ready=false;repeat(50) { if(!ready) {scenario.onActivity {ready=first.canSeek()};Thread.sleep(100)} };assertTrue(ready)
            scenario.onActivity { first.pause();first.seekTo(12000) };Thread.sleep(300)
            scenario.onActivity { a->saved=first.snapshot()!!;first.close();restored=AudioController(a,{_,_->},{},{fail(it)});assertTrue(restored.restore(saved,cards));assertEquals(saved.getInt("position"),restored.snapshot()!!.getInt("position")) }
            ready=false;repeat(50) { if(!ready) {scenario.onActivity {ready=restored.canSeek()};Thread.sleep(100)} };assertTrue(ready)
            Thread.sleep(600)
            scenario.onActivity { assertTrue(restored.paused);assertTrue(kotlin.math.abs(restored.positionMillis()-12000)<600);restored.toggle();assertFalse(restored.paused);restored.close() }
        }
    }
    @Test fun restoringThinkingDelayDoesNotSeekIntoNextAnswer() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario->
            lateinit var audio:AudioController;val cards=CardRepository(ins.targetContext).cards;val card=cards.first()
            val j=JSONObject().put("kind","plan").put("card",card.id).put("path",card.questionAudio).put("position",5000).put("label","Время на свой ответ").put("cycle",1).put("phase","THINKING").put("repetitions",1).put("speed",1).put("thinking",100).put("remaining",100).put("revealed",true)
            scenario.onActivity { a->audio=AudioController(a,{_,_->},{},{fail(it)});assertTrue(audio.restore(j,cards));audio.toggle() }
            var ready=false;repeat(40) { if(!ready) {scenario.onActivity {ready=audio.canSeek()};Thread.sleep(50)} };assertTrue(ready)
            scenario.onActivity {audio.pause();assertTrue("Skipped beginning of answer",audio.positionMillis()<1500);audio.close()}
        }
    }
    @Test fun restoredPlanFinishesOnlyRemainingCycles() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario->
            val done=CountDownLatch(1);val paths=CopyOnWriteArrayList<String>();lateinit var audio:AudioController
            val card=Card("resume-test",1,"01","test","q","a","code","note","check","audio/q.mp3","audio/a.mp3")
            val saved=JSONObject().put("kind","plan").put("card",card.id).put("path",card.questionAudio).put("position",0).put("label","Вопрос").put("cycle",3).put("phase","QUESTION").put("repetitions",5).put("speed",1).put("thinking",0).put("remaining",0).put("revealed",true)
            scenario.onActivity { a->
                audio=AudioController(a,{label,_->if(label.startsWith("Карточка прослушана")) done.countDown()},{},{fail(it)},{ins.context.assets.openFd("short.mp3")},{paths.add(it)})
                assertTrue(audio.restore(saved,listOf(card)))
            }
            Thread.sleep(800);assertTrue(paths.isEmpty());scenario.onActivity { audio.toggle() }
            assertTrue(done.await(18,TimeUnit.SECONDS));scenario.onActivity {audio.close()}
            assertEquals(List(3) { listOf("audio/q.mp3","audio/a.mp3") }.flatten(),paths.toList())
        }
    }
    @Test fun newProgressRoundTripIsAtomicAndKeepsLearned()=runBlocking {
        val s=ProgressStore(ins.targetContext);s.load();val before=s.snapshot();val id="py-middle-001"
        try {
            s.statuses[id]=CardStatus.LEARNED;s.assess(id,1,true,"checklist");s.checklistMarks[id]=setOf(0,1);s.checklistHints.add(id);s.includeLearnedInDue=true;s.answerLevel="detailed";s.exerciseResults["whatif-001"]=false;s.exerciseDrafts["whatif-001"]="My prediction";s.mistakes["compare-001"]="Need review"
            s.persist(s.snapshot());val again=ProgressStore(ins.targetContext);again.load();assertEquals("detailed",again.answerLevel);assertTrue(again.includeLearnedInDue);assertEquals(s.schedules[id],again.schedules[id])
            val copy=ProgressStore(ins.targetContext);copy.import(s.export(),CardRepository(ins.targetContext).cards.map {it.id}.toSet())
            assertEquals(CardStatus.LEARNED,copy.status(id));assertEquals(setOf(0,1),copy.checklistMarks[id]);assertTrue(id in copy.checklistHints);assertEquals(false,copy.exerciseResults["whatif-001"]);assertTrue(copy.mistakes.containsKey("compare-001"));assertEquals("My prediction",copy.exerciseDrafts["whatif-001"])
            val unchanged=copy.export();val broken=JSONObject(unchanged).put("schedules",JSONObject().put(id,JSONObject().put("due",-1).put("streak",1).put("last",0)))
            try {copy.import(broken.toString(),setOf(id));fail("Accepted negative due date")} catch(_:IllegalArgumentException) {}
            assertEquals(unchanged,copy.export())
        } finally {s.persist(before)}
    }
    @Test fun sessionCheckpointSerializationKeepsOrderAnswersAndPausedTime() {
        val s=LearningSession("Interview",listOf("a","b"),600000);s.start(1000);s.visit();s.answers["a"]="partial";s.tick(4200);s.next()
        val restored=restoreSession(s.toJson(),setOf("a","b"));restored.tick(999999)
        assertEquals(3200,restored.activeMillis);assertEquals("b",restored.current());assertEquals(s.answers,restored.answers)
        val checkpoint=Checkpoint(ins.targetContext);val previous=checkpoint.read()
        try {checkpoint.save("test","Test",JSONObject().put("session",s.toJson()));assertEquals("test",Checkpoint(ins.targetContext).read()!!.getString("owner"))}
        finally {checkpoint.clear("test");previous?.let { checkpoint.save(it.getString("owner"),it.getString("title"),it.getJSONObject("payload")) } }
    }
}
