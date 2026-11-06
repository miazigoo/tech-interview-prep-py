package com.python.interviewcards

import android.content.Intent
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject
import kotlinx.coroutines.runBlocking

@RunWith(AndroidJUnit4::class)
class V2DeviceTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    @Test fun allSqlReferenceSolutionsAndAlternateCorrectQueryPass() {
        val engine=SqlTrainer(instrumentation.targetContext)
        for(i in 0 until engine.tasks.length()) {
            val t=engine.tasks.getJSONObject(i)
            assertTrue(t.getString("id"),engine.execute(t.getString("solution"),t).correct)
        }
        assertTrue(engine.execute("select name from users order by users.id asc",engine.tasks.getJSONObject(0)).correct)
        assertFalse(engine.execute("select name from users order by id desc",engine.tasks.getJSONObject(0)).correct)
    }
    @Test fun sqlCannotWriteOrChainStatementsAndTimeoutIsBounded() {
        val engine=SqlTrainer(instrumentation.targetContext);val t=engine.tasks.getJSONObject(0)
        for(sql in listOf("DELETE FROM users","SELECT name FROM users; DROP TABLE users","WITH x AS (SELECT 1) DELETE FROM users")) {
            try { engine.execute(sql,t);fail("Accepted $sql") } catch (_: Exception) {}
        }
        assertTrue(engine.execute(t.getString("solution"),t).correct)
        for(sql in listOf("SELECT zeroblob(1000)","SELECT \"zeroblob\"(1000)","SELECT randomblob/**/(1000)","SELECT 'printf'('%10000s','x')")) {
            try { engine.execute(sql,t);fail("Accepted allocation function") } catch (_: IllegalArgumentException) {}
        }
        val start=System.currentTimeMillis()
        try { engine.execute("WITH RECURSIVE t(x) AS (VALUES(1) UNION ALL SELECT x+1 FROM t) SELECT SUM(x) FROM t",t);fail("No timeout") } catch (_: Exception) {}
        assertTrue("Timeout exceeded 7 seconds",System.currentTimeMillis()-start<7000)
    }
    @Test fun sqlChecksDuplicateMultiplicityAndColumnTypes() {
        val engine=SqlTrainer(instrumentation.targetContext)
        val t=JSONObject("""{"expected":[[1],[1]],"ordered":false}""")
        assertFalse(engine.execute("SELECT 1",t).correct)
        assertTrue(engine.execute("SELECT 1 UNION ALL SELECT 1",t).correct)
        assertFalse(engine.execute("SELECT '1' UNION ALL SELECT '1'",t).correct)
    }
    @Test fun reviewAssessmentDoesNotChangeLearnedAndOldExportStillLoads() {
        val store=ProgressStore(instrumentation.targetContext)
        store.statuses["py-middle-001"]=CardStatus.LEARNED
        val session=LearningSession("review",listOf("py-middle-001"));session.answers["py-middle-001"]="bad"
        assertEquals(CardStatus.LEARNED,store.status("py-middle-001"))
        store.import("""{"schema_version":1,"deck_id":"python-middle-ru","statuses":{"py-middle-002":"HARD"}}""",setOf("py-middle-001","py-middle-002"))
        assertEquals(CardStatus.LEARNED,store.status("py-middle-001"));assertEquals(CardStatus.HARD,store.status("py-middle-002"))
    }
    @Test fun foregroundServiceSurvivesClosingActivityAndHandlesCommands() {
        val context=instrumentation.targetContext
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { PlaybackService.start(it) }
        val deadline=System.currentTimeMillis()+10000
        while(RadioState.card==null && System.currentTimeMillis()<deadline) Thread.sleep(100)
        assertTrue(RadioState.running);assertNotNull(RadioState.card)
        scenario.close();Thread.sleep(600)
        assertTrue("Radio stopped with activity",RadioState.running)
        context.startService(Intent(context,PlaybackService::class.java).setAction(PlaybackService.TOGGLE));Thread.sleep(400)
        assertTrue(RadioState.paused)
        val previous=RadioState.card!!.id
        context.startService(Intent(context,PlaybackService::class.java).setAction(PlaybackService.NEXT));Thread.sleep(400)
        assertNotEquals(previous,RadioState.card!!.id)
        context.startService(Intent(context,PlaybackService::class.java).setAction(PlaybackService.STOP));Thread.sleep(400)
        assertFalse(RadioState.running)
    }
}
