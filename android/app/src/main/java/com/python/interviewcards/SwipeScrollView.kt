package com.python.interviewcards
import android.content.Context
import android.view.MotionEvent
import android.widget.ScrollView
import kotlin.math.abs

/** Horizontal card gestures coexist with vertical reading and horizontal code scrolling. */
class SwipeScrollView(context: Context, private val next: ()->Unit, private val previous: ()->Unit): ScrollView(context) {
    private var x=0f;private var y=0f;private var fromEdge=false
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if(event.actionMasked==MotionEvent.ACTION_DOWN) { x=event.x;y=event.y;fromEdge=x<width*.15f || x>width*.85f }
        val dx=event.x-x;val dy=event.y-y
        if(event.actionMasked==MotionEvent.ACTION_UP && fromEdge && abs(dx)>resources.displayMetrics.density*72 && abs(dx)>abs(dy)*1.8) {
            val cancel=MotionEvent.obtain(event);cancel.action=MotionEvent.ACTION_CANCEL;super.dispatchTouchEvent(cancel);cancel.recycle()
            if(dx<0) next() else previous()
            return true
        }
        return super.dispatchTouchEvent(event)
    }
}
