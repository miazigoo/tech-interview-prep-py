package com.python.interviewcards

import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(AndroidJUnit4::class)
class DeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun card() = Card("test",1,"01","test","q","a","code","note","check","question.mp3","answer.mp3")
    @Test fun realMediaPlayerPlaysExactlyOneToFiveCycles() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            for(n in 1..5) {
                val done=CountDownLatch(1);val paths=CopyOnWriteArrayList<String>();var error="";lateinit var audio: AudioController
                scenario.onActivity { activity ->
                    audio=AudioController(activity,{ label,_ -> if(label.startsWith("Карточка прослушана")) done.countDown() },{}, { error=it;done.countDown() },
                        { instrumentation.context.assets.openFd("short.mp3") },{ paths.add(it) })
                    audio.start(card(),n,true,0,1f,false)
                }
                assertTrue("Timed out at x$n",done.await(25,TimeUnit.SECONDS))
                scenario.onActivity { audio.close() }
                assertEquals("",error)
                assertEquals((1..n).flatMap { listOf("question.mp3","answer.mp3") },paths.toList())
            }
        }
    }
    @Test fun pauseBetweenTracksAndStopCancelDelayedWork() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val thinking=CountDownLatch(1);val paths=CopyOnWriteArrayList<String>();lateinit var audio: AudioController
            scenario.onActivity { activity ->
                audio=AudioController(activity,{ label,_ -> if(label.startsWith("Время на свой ответ")) thinking.countDown() },{}, {},
                    { instrumentation.context.assets.openFd("short.mp3") },{ paths.add(it) })
                audio.start(card(),3,true,2,1f,true)
            }
            assertTrue(thinking.await(10,TimeUnit.SECONDS))
            scenario.onActivity { audio.pause() }
            Thread.sleep(2300)
            assertEquals(listOf("question.mp3"),paths.toList())
            scenario.onActivity { audio.toggle();audio.stop() }
            Thread.sleep(2300)
            assertEquals(listOf("question.mp3"),paths.toList())
            scenario.onActivity { audio.close() }
        }
    }
    @Test fun malformedProgressDoesNotEraseExistingStatusAndRoundTripWorks() {
        val store=ProgressStore(instrumentation.targetContext)
        store.statuses["a"]=CardStatus.HARD
        val before=store.export()
        try { store.import("""{"schema_version":1,"deck_id":"python-middle-ru","statuses":{"a":"NEW","b":"INVALID"}}""",setOf("a","b"));fail() } catch (_: IllegalArgumentException) {}
        assertEquals(before,store.export())
        val restored=ProgressStore(instrumentation.targetContext)
        assertEquals(1 to 0,restored.import(before,setOf("a")))
        assertEquals(CardStatus.HARD,restored.status("a"))
    }
    @Test fun persistedProgressSurvivesCreatingNewStore() = kotlinx.coroutines.runBlocking {
        val context=instrumentation.targetContext
        val original=ProgressStore(context);original.load();val before=original.snapshot()
        try {
            original.statuses["py-middle-002"]=CardStatus.LEARNED
            original.repetitions=5;original.persist(original.snapshot())
            val restored=ProgressStore(context);restored.load()
            assertEquals(CardStatus.LEARNED,restored.status("py-middle-002"));assertEquals(5,restored.repetitions)
        } finally { original.persist(before) }
    }
    @Test fun missingAudioReportsErrorWithoutAutomaticCardLoop() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val error=CountDownLatch(1);var advanced=0;lateinit var audio: AudioController
            scenario.onActivity { activity ->
                audio=AudioController(activity,{ _,_-> },{ advanced++ },{ error.countDown() },
                    { throw java.io.FileNotFoundException("missing") })
                audio.start(card(),5,true,0,1f,true)
            }
            assertTrue(error.await(5,TimeUnit.SECONDS));Thread.sleep(300)
            scenario.onActivity { assertFalse(audio.active);audio.close() }
            assertEquals(0,advanced)
        }
    }

}
