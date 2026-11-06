package com.python.interviewcards
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.widget.*
import com.google.android.material.button.MaterialButton

class AudioSeekView(context:Context,private val position:()->Int,private val duration:()->Int,private val enabled:()->Boolean,private val seek:(Long)->Unit,private val shift:(Long)->Unit,private val phrase:()->Unit,private val transport:(()->Unit)?=null,private val active:()->Boolean={false},private val paused:()->Boolean={false}):LinearLayout(context) {
    constructor(context:Context,audio:AudioController,showTransport:Boolean=false):this(context,{audio.positionMillis()},{audio.durationMillis()},{audio.canSeek()},{audio.seekTo(it)},{audio.seekBy(it)},{audio.repeatPhrase()},if(showTransport) ({audio.toggle()}) else null,{audio.active},{audio.paused})
    private val handler=Handler(Looper.getMainLooper());private var dragging=false
    private val seekbar=SeekBar(context).apply { contentDescription="Позиция аудиотрека" }
    private val time=TextView(context).apply { textSize=11f;setTextColor(Color.LTGRAY) }
    private var transportButton:MaterialButton?=null
    private val controls=mutableListOf<MaterialButton>()
    private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
    private fun format(n:Int)="${n/60000}:${(n/1000%60).toString().padStart(2,'0')}"
    private val update=object:Runnable { override fun run() {
        transportButton?.let { it.visibility=if(active()) VISIBLE else GONE;it.text=if(paused()) "▶ Продолжить аудио" else "Ⅱ Пауза аудио" }
        val d=duration().coerceAtLeast(0);val p=position().coerceIn(0,d)
        if(!dragging) { seekbar.max=d.coerceAtLeast(1);seekbar.progress=p }
        time.text="${format(p)} / ${format(d)}";seekbar.isEnabled=enabled();controls.forEach { it.isEnabled=enabled() }
        handler.postDelayed(this,250)
    } }
    init {
        orientation=VERTICAL
        if(transport!=null) {
            transportButton=MaterialButton(context).apply { isAllCaps=false;minHeight=dp(48);setTextColor(Color.rgb(184,160,255));backgroundTintList=android.content.res.ColorStateList.valueOf(Color.rgb(27,30,40));setOnClickListener { transport.invoke() } }
            addView(transportButton,LayoutParams(-1,-2))
        }
        addView(seekbar,LayoutParams(-1,dp(28)))
        val row=LinearLayout(context).apply { gravity=android.view.Gravity.CENTER_VERTICAL }
        row.addView(time,LayoutParams(dp(78),-2))
        listOf("−10с" to {shift(-10000L)},"Фраза" to phrase,"+10с" to {shift(10000L)}).forEach { (title,action) ->
            val b=MaterialButton(context).apply { text=title;isAllCaps=false;backgroundTintList=android.content.res.ColorStateList.valueOf(Color.rgb(27,30,40));setTextColor(Color.rgb(184,160,255));textSize=11f;minWidth=0;minimumWidth=0;minHeight=dp(48);insetTop=0;insetBottom=0;setPadding(dp(2),0,dp(2),0);contentDescription=when(title) { "Фраза"->"Повторить последнюю фразу";"−10с"->"Назад на 10 секунд";else->"Вперёд на 10 секунд" };setOnClickListener { action() } }
            controls.add(b);row.addView(b,LayoutParams(0,dp(48),1f))
        };addView(row)
        seekbar.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(v:SeekBar) { dragging=true }
            override fun onStopTrackingTouch(v:SeekBar) { dragging=false;seek(v.progress.toLong()) }
            override fun onProgressChanged(v:SeekBar,value:Int,user:Boolean) { if(user) time.text="${format(value)} / ${format(duration())}" }
        })
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow();handler.post(update) }
    override fun onDetachedFromWindow() { handler.removeCallbacksAndMessages(null);super.onDetachedFromWindow() }
}
