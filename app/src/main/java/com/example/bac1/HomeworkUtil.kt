package com.example.bac1

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object HomeworkUtil {

    class Draft(var text: String, val options: MutableList<String>, var correct: Int)
    class Parsed(val list: List<Draft>, val skipped: Int)

    private const val DIGITS = "٠١٢٣٤٥٦٧٨٩"
    private val optRe = Regex("^[\\(\\[]?\\s*([أابجدabcdABCD])\\s*[\\)\\]\\.\\-:ـ،]\\s*(.+)$")
    private val numRe = Regex("^\\s*(?:س\\s*)?\\d+\\s*[\\)\\]\\.\\-:ـ،]\\s*(.+)$")
    private val ansRe = Regex(
        "^\\s*(?:الإجابة الصحيحة|الاجابة الصحيحة|الإجابة|الاجابة|الاجابه|الإجابه|إجابة|اجابة|الصحيحة|answer|ans|correct)" +
                "\\s*[:：=\\-]?\\s*(.+)$",
        RegexOption.IGNORE_CASE
    )
    private val marks = charArrayOf('✓', '✔', '✅', '*')

    private fun normDigits(s: String): String {
        val sb = StringBuilder()
        for (ch in s) {
            val i = DIGITS.indexOf(ch)
            sb.append(if (i >= 0) ('0' + i) else ch)
        }
        return sb.toString()
    }

    private fun letterIndex(s: String): Int = when (s.trim().lowercase()) {
        "أ", "ا", "a", "1" -> 0
        "ب", "b", "2" -> 1
        "ج", "c", "3" -> 2
        "د", "d", "4" -> 3
        else -> -1
    }

    // بيقسّم أسئلة ملصوقة من Word أو واتساب
    fun parsePasted(raw: String): Parsed {
        val out = ArrayList<Draft>()
        var skipped = 0
        var cur: Draft? = null

        fun flush() {
            val c = cur
            if (c != null) {
                if (c.text.isNotBlank() && c.options.size >= 2) out.add(c) else skipped += 1
            }
            cur = null
        }

        for (line0 in normDigits(raw).lines()) {
            var l = line0.trim()
            if (l.isEmpty()) continue
            var pre = false
            while (l.isNotEmpty() && l[0] in marks) {
                pre = true
                l = l.substring(1).trim()
            }
            if (l.isEmpty()) continue

            val c = cur
            val am = ansRe.find(l)
            if (am != null && c != null) {
                val v = am.groupValues[1].trim()
                val token = v.split(Regex("[\\s\\)\\.\\-:]+")).firstOrNull { it.isNotEmpty() } ?: ""
                var idx = letterIndex(token)
                if (idx < 0 || idx >= c.options.size) idx = c.options.indexOfFirst { it.equals(v, true) }
                if (idx in c.options.indices) c.correct = idx
                continue
            }

            val om = optRe.find(l)
            if (om != null && c != null) {
                var t = om.groupValues[2].trim()
                var correct = pre
                while (t.isNotEmpty() && t[0] in marks) {
                    correct = true
                    t = t.substring(1).trim()
                }
                while (t.isNotEmpty() && t[t.length - 1] in marks) {
                    correct = true
                    t = t.substring(0, t.length - 1).trim()
                }
                if (t.isNotEmpty()) {
                    c.options.add(t)
                    if (correct) c.correct = c.options.size - 1
                }
                continue
            }

            val nm = numRe.find(l)
            val textLine = if (nm != null) nm.groupValues[1].trim() else l
            if (c == null) {
                cur = Draft(textLine, mutableListOf(), -1)
            } else if (nm != null || c.options.isNotEmpty()) {
                flush()
                cur = Draft(textLine, mutableListOf(), -1)
            } else {
                c.text = c.text + " " + textLine
            }
        }
        flush()
        return Parsed(out, skipped)
    }

    // بيقرأ رد الذكاء الاصطناعي (JSON)
    fun parseAi(raw: String): List<Draft> {
        val clean = raw.replace("```json", "").replace("```", "").trim()
        val arr: JSONArray = try {
            JSONArray(clean)
        } catch (e: Exception) {
            try {
                JSONObject(clean).optJSONArray("questions") ?: return emptyList()
            } catch (e2: Exception) {
                return emptyList()
            }
        }
        val out = ArrayList<Draft>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val text = (o.optString("text").ifEmpty { o.optString("question") }).trim()
            val oa = o.optJSONArray("options") ?: continue
            val opts = ArrayList<String>()
            for (k in 0 until oa.length()) {
                val t = oa.optString(k).trim()
                if (t.isNotEmpty()) opts.add(t)
            }
            if (text.isEmpty() || opts.size < 2) continue
            var c = -1
            for (key in listOf("correct_index", "correctIndex", "correct")) {
                if (o.has(key)) {
                    c = o.optInt(key, -1)
                    break
                }
            }
            if (c !in opts.indices) c = -1
            out.add(Draft(text, opts, c))
        }
        return out
    }

    fun fmtDate(ms: Long): String =
        SimpleDateFormat("yyyy/MM/dd  hh:mm a", Locale.forLanguageTag("ar")).format(Date(ms))

    fun remaining(ms: Long): String {
        if (ms <= 0) return "انتهى"
        val m = ms / 60000
        val d = m / 1440
        val h = (m % 1440) / 60
        val mm = m % 60
        return when {
            d > 0 -> "$d يوم و$h ساعة"
            h > 0 -> "$h ساعة و$mm دقيقة"
            else -> "$mm دقيقة"
        }
    }

    // فرق الوقت بين موبايل الطالب والسيرفر
    fun serverOffset(db: FirebaseFirestore, key: String, cb: (Long) -> Unit) {
        val ref = db.collection("serverClock").document(key)
        ref.set(hashMapOf<String, Any>("t" to FieldValue.serverTimestamp()))
            .addOnSuccessListener {
                ref.get()
                    .addOnSuccessListener { d ->
                        val ms = d.getTimestamp("t")?.toDate()?.time
                        cb(if (ms != null) ms - System.currentTimeMillis() else 0L)
                    }
                    .addOnFailureListener { cb(0L) }
            }
            .addOnFailureListener { cb(0L) }
    }
}
