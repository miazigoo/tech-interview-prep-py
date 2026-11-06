package com.python.interviewcards

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.os.CancellationSignal
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.math.BigDecimal
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class SqlResult(val columns: List<String>,val rows: List<List<String>>,val correct: Boolean)
class SqlTrainer(context: Context) {
    val content=JSONObject(context.assets.open("exercises.json").bufferedReader().use { it.readText() })
    val tasks=content.getJSONArray("sql")
    private val databases=mutableListOf<File>()
    init {
        val schemas=mutableListOf(content.getString("sql_schema"))
        content.optJSONArray("sql_check_schemas")?.let { array -> for(i in 0 until array.length()) schemas.add(array.getString(i)) }
        schemas.forEachIndexed { i,schema ->
            val file=File(context.cacheDir,"sql-training-v3-$i.db");file.delete()
            SQLiteDatabase.openOrCreateDatabase(file,null).use { db -> schema.split(';').filter { it.isNotBlank() }.forEach { db.execSQL(it) } }
            databases.add(file)
        }
    }
    fun schema(index:Int=0):String=if(index==0) content.getString("sql_schema") else content.getJSONArray("sql_check_schemas").getString(index-1)
    fun execute(sql: String,task: JSONObject,visibleIndex:Int=0): SqlResult {
        val deadline=android.os.SystemClock.elapsedRealtime()+4000
        fun budget():Long { val time=deadline-android.os.SystemClock.elapsedRealtime();if(time<=0) throw android.os.OperationCanceledException();return minOf(1500,time) }
        val definitions=mutableListOf(task)
        task.optJSONArray("checks")?.let { checks -> for(i in 0 until checks.length()) definitions.add(checks.getJSONObject(i)) }
        require(visibleIndex in definitions.indices)
        val visible=executeOn(sql,definitions[visibleIndex],databases[visibleIndex],budget())
        if(!visible.correct) return visible
        for(i in definitions.indices.filter { it!=visibleIndex }) {
            if(!executeOn(sql,definitions[i],databases[i],budget()).correct) return visible.copy(correct=false)
        }
        return visible
    }
    private fun executeOn(sql:String,task:JSONObject,database:File,budgetMillis:Long):SqlResult {
        val clean=sql.trim().removeSuffix(";").trim()
        require(clean.length in 1..4096 && ';' !in clean && '\u0000' !in clean) { "Введите один запрос длиной до 4096 символов" }
        require(Regex("^(SELECT|WITH)\\b",RegexOption.IGNORE_CASE).containsMatchIn(clean)) { "Разрешены только SELECT и WITH" }
        // Keep the educational dialect bounded: no string expansion or allocation functions.
        require(!Regex("'([^']|'')*'\\s*\\(").containsMatchIn(clean)) { "Кавычки вокруг имён функций не поддерживаются" }
        val syntax=clean.replace(Regex("'([^']|'')*'"), "''")
            .replace(Regex("/\\*.*?\\*/",RegexOption.DOT_MATCHES_ALL)," ").replace(Regex("--[^\\n]*")," ")
            .replace("\"","").replace("`","").replace("[","").replace("]","")
        require("||" !in syntax) { "Конкатенация в этом учебном режиме отключена" }
        val ctes=Regex("(?i)(?:WITH(?:\\s+RECURSIVE)?|,)\\s*(\\w+)\\s*(?:\\([^)]*\\))?\\s+AS\\s*\\(").findAll(syntax).map { it.groupValues[1].uppercase() }.toSet()
        val permitted=setOf("COUNT","SUM","AVG","MIN","MAX","TOTAL","COALESCE","IFNULL","NULLIF","ABS","ROUND","LOWER","UPPER","LENGTH","SUBSTR","SUBSTRING","TRIM","LTRIM","RTRIM","DATE","TIME","DATETIME","JULIANDAY","UNIXEPOCH","STRFTIME","TYPEOF","IIF","CAST","IN","EXISTS","AS","VALUES","FROM","JOIN","WHERE","AND","OR","NOT","ON","OVER","FILTER","ROW_NUMBER","RANK","DENSE_RANK","LAG","LEAD")+ctes
        Regex("([A-Za-z_][A-Za-z_0-9]*)\\s*\\(").findAll(syntax).forEach { require(it.groupValues[1].uppercase() in permitted) { "Функция ${it.groupValues[1]} не входит в учебный набор" } }
        val cancel=CancellationSignal()
        val executor=Executors.newSingleThreadScheduledExecutor()
        val timeout=executor.schedule({ cancel.cancel() },budgetMillis,TimeUnit.MILLISECONDS)
        try {
            SQLiteDatabase.openDatabase(database.path,null,SQLiteDatabase.OPEN_READONLY).use { db ->
                db.rawQuery(clean,null,cancel).use { cursor ->
                    val display=mutableListOf<List<String>>();val canonical=mutableListOf<List<String>>()
                    while(cursor.moveToNext()) {
                        require(display.size<200) { "Слишком много строк: добавьте LIMIT (максимум 200)" }
                        display.add((0 until cursor.columnCount).map { if(cursor.isNull(it)) "NULL" else cursor.getString(it) })
                        canonical.add((0 until cursor.columnCount).map { col -> when(cursor.getType(col)) {
                            Cursor.FIELD_TYPE_NULL->"NULL"
                            Cursor.FIELD_TYPE_INTEGER,Cursor.FIELD_TYPE_FLOAT->number(cursor.getString(col))
                            else->"T:"+cursor.getString(col)
                        } })
                    }
                    val expected=expected(task.getJSONArray("expected"))
                    val equal=if(task.getBoolean("ordered")) canonical==expected else canonical.groupingBy { it }.eachCount()==expected.groupingBy { it }.eachCount()
                    return SqlResult(cursor.columnNames.toList(),display,equal && cursor.columnCount==task.optInt("column_count",task.getJSONArray("expected").optJSONArray(0)?.length() ?: cursor.columnCount))
                }
            }
        } finally { timeout.cancel(false);executor.shutdownNow() }
    }
    private fun number(raw: String)="N:"+BigDecimal(raw).stripTrailingZeros().toPlainString()
    private fun expected(array: JSONArray)=(0 until array.length()).map { row ->
        val a=array.getJSONArray(row)
        (0 until a.length()).map { col -> val v=a.get(col);when { v==JSONObject.NULL->"NULL";v is Number->number(v.toString());else->"T:"+v.toString() } }
    }
}
