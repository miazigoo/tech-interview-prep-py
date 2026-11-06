package com.python.interviewcards
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

data class DailyPlan(val days:Long,val needed:Int,val goal:Int,val capacity:Int,val practiceMinutes:Int,val tight:Boolean)
object PreparationPlan {
    fun calculate(today:LocalDate,target:LocalDate,minutes:Int,remaining:Int):DailyPlan {
        require(!target.isBefore(today) && minutes in 5..120 && remaining>=0)
        val days=ChronoUnit.DAYS.between(today,target)+1
        val practice=if(minutes>=15) 5 else 0
        val capacity=((minutes-practice)/2).coerceAtLeast(1)
        val needed=ceil(remaining.toDouble()/days).toInt()
        return DailyPlan(days,needed,minOf(needed,capacity),capacity,practice,needed>capacity)
    }
}
