package com.example.bac1

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore

class ExamReviewActivity : AppCompatActivity() {

    private lateinit var body: LinearLayout

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun num(v: Any?, def: Int): Int = (v as? Number)?.toInt() ?: def

    private fun tv(s: String, size: Float, color: String, bold: Boolean = false): TextView {
        val t = TextView(this)
        t.text = s
        t.textSize = size
        t.setTextColor(Color.parseColor(color))
        if (bold) t.setTypeface(null, Typeface.BOLD)
        return t
    }

    private fun shape(fill: String, stroke: String?, radius: Int): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(Color.parseColor(fill))
        g.cornerRadius = dp(radius).toFloat()
        if (stroke != null) g.setStroke(dp(1), Color.parseColor(stroke))
        return g
    }

    private fun card(): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.setPadding(dp(14), dp(12), dp(14), dp(14))
        c.background = shape("#FFFFFF", "#DDE1E8", 16)
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.topMargin = dp(10)
        c.layoutParams = lp
        return c
    }

    private fun message(msg: String) {
        body.removeAllViews()
        val t = tv(msg, 16f, "#444444")
        t.gravity = Gravity.CENTER
        t.setPadding(0, dp(40), 0, 0)
        body.addView(t)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.layoutDirection = View.LAYOUT_DIRECTION_RTL
        root.setBackgroundColor(Color.parseColor("#ECEFF4"))
        val scroll = ScrollView(this)
        body = LinearLayout(this)
        body.orientation = LinearLayout.VERTICAL
        body.setPadding(dp(14), dp(12), dp(14), dp(24))
        scroll.addView(body, ViewGroup.LayoutParams(-1, -2))
        root.addView(scroll, LinearLayout.LayoutParams(-1, -1))
        setContentView(root)

        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        val examId = intent.getStringExtra("EXAM_ID") ?: ""
        val name = getSharedPreferences("bac1_prefs", MODE_PRIVATE).getString("student_name", "طالب") ?: "طالب"

        message("جارٍ التحميل...")
        if (examId.isEmpty()) {
            message("تعذّر فتح الامتحان")
            return
        }

        val db = FirebaseFirestore.getInstance()
        db.collection("exams").document(examId).get()
            .addOnSuccessListener { ex ->
                if (!ex.exists()) {
                    message("الامتحان ده مش موجود")
                } else {
                    db.collection("results").document("${examId}_$name").get()
                        .addOnSuccessListener { res ->
                            if (!res.exists()) message("مفيش نتيجة محفوظة ليك في الامتحان ده.")
                            else render(ex, res)
                        }
                        .addOnFailureListener { message("تعذّر الاتصال، تحقق من الإنترنت.") }
                }
            }
            .addOnFailureListener { message("تعذّر الاتصال، تحقق من الإنترنت.") }
    }

    private fun optionRow(opt: String, isCorrect: Boolean, isChosen: Boolean): TextView {
        val t = TextView(this)
        t.textSize = 16f
        t.setPadding(dp(12), dp(10), dp(12), dp(10))
        when {
            isCorrect -> {
                t.text = "✅  $opt" + (if (isChosen) "  (إجابتك ✔)" else "  (الإجابة الصحيحة)")
                t.setTextColor(Color.parseColor("#1B5E20"))
                t.setTypeface(null, Typeface.BOLD)
                t.background = shape("#E8F5E9", "#66BB6A", 12)
            }
            isChosen -> {
                t.text = "❌  $opt  (إجابتك)"
                t.setTextColor(Color.parseColor("#B71C1C"))
                t.setTypeface(null, Typeface.BOLD)
                t.background = shape("#FFEBEE", "#EF5350", 12)
            }
            else -> {
                t.text = "○  $opt"
                t.setTextColor(Color.parseColor("#333333"))
                t.background = shape("#F5F6F8", "#E3E6EB", 12)
            }
        }
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.topMargin = dp(6)
        t.layoutParams = lp
        return t
    }

    private fun render(ex: DocumentSnapshot, res: DocumentSnapshot) {
        body.removeAllViews()

        val title = ex.getString("title") ?: res.getString("examTitle") ?: "امتحان"
        val totalScore = num(ex.getLong("totalScore") ?: res.getLong("totalScore"), 0)
        val autoScore = num(res.getLong("autoScore"), 0)
        val mcq = (res.get("mcqAnswers") as? List<*>)?.mapNotNull { it as? Map<*, *> } ?: emptyList()
        val essay = (res.get("essayAnswers") as? List<*>)?.mapNotNull { it as? Map<*, *> } ?: emptyList()
        val essaySum = essay.sumOf { maxOf(0, num(it["awardedScore"], -1)) }
        val essayPending = essay.any { num(it["awardedScore"], -1) < 0 }

        val head = tv(title, 22f, "#1F2A44", true)
        head.setPadding(0, dp(4), 0, dp(2))
        body.addView(head)
        body.addView(tv("🔒 عرض فقط، مينفعش تعدّل في الإجابات", 13f, "#6B7280"))

        val sc = card()
        val st = tv("🎯 درجتك: ${autoScore + essaySum} من $totalScore", 20f, "#4B3FD1", true)
        sc.addView(st)
        if (essayPending) {
            sc.addView(tv("⏳ فيه أسئلة مقالية لسه المدرس مصححهاش", 13f, "#E65100"))
        }
        body.addView(sc)

        val qs = ex.get("questions") as? List<*> ?: emptyList<Any>()
        qs.forEachIndexed { i, item ->
            val q = item as? Map<*, *> ?: return@forEachIndexed
            val qText = q["text"] as? String ?: ""
            val qId = q["qId"]?.toString() ?: ""
            val type = q["type"] as? String ?: "mcq"
            val points = num(q["score"], 0)

            val c = card()
            c.addView(tv("السؤال ${i + 1}  ·  ($points درجة)", 13f, "#6B7280", true))

            if (type == "mcq") {
                val a = mcq.firstOrNull {
                    (qId.isNotEmpty() && it["qId"]?.toString() == qId) || (it["text"] as? String) == qText
                }
                val chosen = num(a?.get("chosenIndex"), -1)
                val opts = (q["options"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                val correct = num(q["correctIndex"], -1)

                val (badge, color) = when {
                    a == null || chosen < 0 -> Pair("⏱ لم تجب على السؤال ده", "#E65100")
                    chosen == correct -> Pair("✅ إجابة صحيحة", "#2E7D32")
                    else -> Pair("❌ إجابة خاطئة", "#C62828")
                }
                c.addView(tv(badge, 14f, color, true))

                val qt = tv(qText, 18f, "#1F2A44", true)
                qt.setPadding(0, dp(6), 0, dp(4))
                c.addView(qt)
                opts.forEachIndexed { k, opt -> c.addView(optionRow(opt, k == correct, k == chosen)) }
            } else {
                val a = essay.firstOrNull {
                    (qId.isNotEmpty() && it["qId"]?.toString() == qId) || (it["text"] as? String) == qText
                }
                val answerText = (a?.get("answerText") as? String)?.trim() ?: ""
                val awarded = num(a?.get("awardedScore"), -1)
                val maxScore = num(a?.get("maxScore"), points)

                c.addView(tv("✍️ سؤال مقالي", 14f, "#6A3CD6", true))
                val qt = tv(qText, 18f, "#1F2A44", true)
                qt.setPadding(0, dp(6), 0, dp(4))
                c.addView(qt)

                val ans = tv(if (answerText.isEmpty()) "لم تكتب إجابة" else answerText, 16f, "#333333")
                ans.setPadding(dp(12), dp(10), dp(12), dp(10))
                ans.background = shape("#F5F6F8", "#E3E6EB", 12)
                c.addView(ans)

                val score = if (awarded >= 0) tv("🏅 درجتك: $awarded من $maxScore", 14f, "#2E7D32", true)
                else tv("⏳ في انتظار تصحيح المدرس", 14f, "#E65100", true)
                score.setPadding(0, dp(8), 0, 0)
                c.addView(score)
            }
            body.addView(c)
        }
    }
}
