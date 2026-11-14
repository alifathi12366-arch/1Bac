package com.example.bac1

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class TeacherLessonsActivity : AppCompatActivity() {

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val code = intent.getStringExtra(MyTeachersActivity.EXTRA_TEACHER_CODE) ?: ""
        val name = intent.getStringExtra(MyTeachersActivity.EXTRA_TEACHER_NAME) ?: ""

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(0xFF121212.toInt())
        root.layoutDirection = View.LAYOUT_DIRECTION_RTL

        val title = TextView(this)
        title.text = "📚 محتوى أستاذ $name"
        title.textSize = 20f
        title.setTextColor(0xFFFFFFFF.toInt())
        title.gravity = Gravity.CENTER
        title.setPadding(0, dp(8), 0, dp(12))
        title.setTypeface(null, android.graphics.Typeface.BOLD)
        root.addView(title)

        val empty = TextView(this)
        empty.text = "لا يوجد محتوى متاح حالياً"
        empty.textSize = 16f
        empty.setTextColor(0xFF888888.toInt())
        empty.gravity = Gravity.CENTER
        empty.visibility = View.GONE
        root.addView(empty)

        val rv = RecyclerView(this)
        rv.layoutManager = LinearLayoutManager(this)
        root.addView(rv, LinearLayout.LayoutParams(-1, 0, 1f))

        setContentView(root)

        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val p = dp(16)
            v.setPadding(bars.left + p, bars.top + p, bars.right + p, bars.bottom + p)
            insets
        }

        if (code.isEmpty()) {
            empty.visibility = View.VISIBLE
            return
        }

        FirebaseFirestore.getInstance()
            .collection("teacherContent")
            .whereEqualTo("teacherCode", code)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { result ->
                val items = result.documents.map { doc ->
                    TeacherContentItem(
                        type = doc.getString("type") ?: "message",
                        text = doc.getString("text") ?: ""
                    )
                }
                if (items.isEmpty()) {
                    empty.visibility = View.VISIBLE
                } else {
                    rv.adapter = TeacherContentAdapter(items)
                }
            }
    }
}
