package com.python.interviewcards
import android.os.Handler
import android.os.Looper
import android.text.*
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
object NoteEditor {
    fun show(activity:AppCompatActivity,id:String,store:ProgressStore,persist:()->Unit) {
        val handler=Handler(Looper.getMainLooper());val save=Runnable { persist() }
        val field=EditText(activity).apply { minLines=5;maxLines=12;gravity=android.view.Gravity.TOP;hint="Своё объяснение или пример из проекта";filters=arrayOf(InputFilter.LengthFilter(10000));setText(store.notes[id] ?: "") }
        field.addTextChangedListener(object:TextWatcher {
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) {
                if(s.isNullOrEmpty()) store.notes.remove(id) else store.notes[id]=s.toString()
                handler.removeCallbacks(save);handler.postDelayed(save,350)
            }
            override fun afterTextChanged(s:Editable?) {}
        })
        val dialog=MaterialAlertDialogBuilder(activity).setTitle("Моя заметка").setMessage("Сохраняется автоматически · до 10000 символов").setView(field).setPositiveButton("Готово",null).setNeutralButton("Удалить заметку") { _,_->
            MaterialAlertDialogBuilder(activity).setTitle("Удалить заметку?").setMessage("Удаляется только заметка к этой карточке.").setNegativeButton("Отмена",null).setPositiveButton("Удалить") { _,_->store.notes.remove(id);persist() }.show()
        }.create()
        val lifecycle=androidx.lifecycle.LifecycleEventObserver { _,event -> if(event==androidx.lifecycle.Lifecycle.Event.ON_STOP) { handler.removeCallbacks(save);persist() } }
        activity.lifecycle.addObserver(lifecycle)
        dialog.setOnDismissListener { handler.removeCallbacks(save);activity.lifecycle.removeObserver(lifecycle);persist() };dialog.show()
    }
}
