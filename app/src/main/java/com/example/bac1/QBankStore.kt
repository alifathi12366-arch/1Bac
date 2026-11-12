package com.example.bac1

import android.content.Context
import org.json.JSONObject

object QBankStore {
    private const val PREFS = "bac1_qbank"

    fun key(text: String): String = text.trim().hashCode().toString()

    fun save(ctx: Context, date: String, text: String, correct: Boolean, chosen: String) {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val obj = try {
            JSONObject(p.getString("r_$date", "{}") ?: "{}")
        } catch (e: Exception) {
            JSONObject()
        }
        obj.put(key(text), JSONObject().put("c", correct).put("a", chosen))
        p.edit().putString("r_$date", obj.toString()).apply()
    }

    fun load(ctx: Context, date: String): Map<String, Pair<Boolean, String>> {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val out = mutableMapOf<String, Pair<Boolean, String>>()
        try {
            val obj = JSONObject(p.getString("r_$date", "{}") ?: "{}")
            val it = obj.keys()
            while (it.hasNext()) {
                val k = it.next()
                val o = obj.getJSONObject(k)
                out[k] = Pair(o.optBoolean("c", false), o.optString("a", ""))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return out
    }
}
