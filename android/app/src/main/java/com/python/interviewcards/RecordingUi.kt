package com.python.interviewcards
import android.Manifest
import android.graphics.Color
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class RecordingUi(private val activity:AppCompatActivity,private val before:()->Unit) {
    private var message:TextView?=null
    private var record:MaterialButton?=null
    private var currentId=""
    private var pending=""
    private val voice=VoiceAnswer(activity) { text -> message?.text=text;record?.text=if(voiceIsRecording()) "■ Остановить запись" else "● Записать свой ответ" }
    private fun voiceIsRecording():Boolean=voice.recording
    private val permission=activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if(granted && pending==currentId) begin(pending) else message?.text="Без микрофона можно продолжать обучение"
    }
    private fun begin(id:String) { try { before();voice.start(id) } catch(e:Exception) { message?.text=e.message ?: "Не удалось начать запись" } }
    fun panel(id:String):View {
        stop();currentId=id
        val box=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL }
        message=TextView(activity).apply { text=if(voice.exists(id)) "Есть сохранённый ответ" else "Запись остаётся только на этом устройстве";setTextColor(Color.LTGRAY) }
        fun btn(s:String,action:()->Unit)=MaterialButton(activity).apply { text=s;isAllCaps=false;setOnClickListener { action() } }
        record=btn("● Записать свой ответ") {
            if(voice.recording) voice.stop() else { pending=id;before();permission.launch(Manifest.permission.RECORD_AUDIO) }
        }
        val inner=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL;visibility=View.GONE }
        box.addView(btn("Мой устный ответ") { inner.visibility=if(inner.visibility==View.VISIBLE) View.GONE else View.VISIBLE })
        box.addView(inner)
        inner.addView(record);inner.addView(message)
        inner.addView(btn("▷ Прослушать свой ответ") { try { before();voice.play(id) } catch(e:Exception) { message?.text=e.message } })
        inner.addView(btn("Удалить мою запись") {
            MaterialAlertDialogBuilder(activity).setTitle("Удалить запись?").setMessage("Удаляется только ваш ответ на эту карточку.").setNegativeButton("Отмена",null).setPositiveButton("Удалить") { _,_->voice.delete(id) }.show()
        })
        return box
    }
    fun stop() { voice.stop() }
    fun close() { voice.close() }
}
