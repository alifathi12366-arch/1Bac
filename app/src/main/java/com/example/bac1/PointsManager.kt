package com.example.bac1

import android.content.SharedPreferences
import java.util.Calendar

object PointsManager {

    fun currentMonthKey(): String {
        val cal = Calendar.getInstance()
        return "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.MONTH) + 1}"
    }

    // نقاط الشهر الحالي (تتصفّر تلقائيًا مع أول يوم في شهر جديد)
    fun getMonthPoints(prefs: SharedPreferences): Int {
        val key = currentMonthKey()
        val saved = prefs.getString("month_points_key", null)
        if (saved == null) {
            // أول مرة بعد التحديث: النقاط الحالية تُحسب للشهر الحالي
            val start = prefs.getInt("student_points", 0)
            prefs.edit().putString("month_points_key", key).putInt("month_points", start).apply()
            return start
        }
        if (saved != key) {
            prefs.edit().putString("month_points_key", key).putInt("month_points", 0).apply()
            return 0
        }
        return prefs.getInt("month_points", 0)
    }

    // يزوّد نقاط الشهر والنقاط الكلية معًا، ويرجّع نقاط الشهر
    fun addPoints(prefs: SharedPreferences, n: Int): Int {
        val month = getMonthPoints(prefs) + n
        val total = prefs.getInt("student_points", 0) + n
        prefs.edit().putInt("month_points", month).putInt("student_points", total).apply()
        return month
    }
}
