package com.example.bac1

import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class StudentsManageActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private lateinit var container: LinearLayout
    private var code = ""
    private var teacherName = ""
    private var auto = false
    private var paused = false
    private var pending: List<DocumentSnapshot> = emptyList()
    private var blocked: List<DocumentSnapshot> = emptyList()
    private var students: List<DocumentSnapshot> = emptyList()
    private val selected = HashSet<String>()
    private val pendingChecks = mutableListOf<CheckBox>()
    private var query = ""
    private var limit = 40
    private var listBox: LinearLayout? = null
    private var btnAcceptSel: Button? = null
    private var btnRejectSel: Button? = null
    private var btnBlockSel: Button? = null

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun tv(text: String, size: Float = 16f, bold: Boolean = false): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = size
        t.setTextColor(0xFF222222.toInt())
        t.setPadding(0, dp(8), 0, dp(4))
        if (bold) t.setTypeface(null, Typeface.BOLD)
        return t
    }

    private fun nameOf(d: DocumentSnapshot): String {
        val f = d.getString("fullName") ?: ""
        if (f.isNotBlank()) return f
        val n = d.getString("name") ?: d.getString("studentName") ?: ""
        return if (n.isNotBlank()) n else "طالب"
    }

    private fun infoLine(d: DocumentSnapshot): String {
        val p = d.getString("phone") ?: ""
        val g = d.getString("parentPhone") ?: ""
        val ps = if (p.isEmpty()) "—" else JoinUtil.localPhone(p)
        val gs = if (g.isEmpty()) "—" else JoinUtil.localPhone(g)
        return "📱 $ps  ·  👨‍👩‍👦 $gs"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_questions)
        container = findViewById(R.id.adminContainer)
        val prefs = getSharedPreferences("bac1_prefs", MODE_PRIVATE)
        code = prefs.getString("teacher_code", "") ?: ""
        teacherName = prefs.getString("teacher_name", "") ?: ""
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    // ---------- التحميل ----------
    private fun load() {
        container.removeAllViews()
        container.addView(tv("👥 طلابي", 22f, true))
        container.addView(tv("جارٍ التحميل...", 14f))
        if (code.isEmpty()) return
        db.collection("teachers").document(code).get()
            .addOnSuccessListener { t ->
                auto = t.getBoolean("autoAccept") ?: false
                paused = t.getBoolean("joinPaused") ?: false
                loadLists()
            }
            .addOnFailureListener { loadLists() }
    }

    private fun loadError() {
        container.removeAllViews()
        container.addView(tv("👥 طلابي", 22f, true))
        container.addView(tv("تعذّر التحميل، تحقق من الإنترنت", 14f))
    }

    private fun loadLists() {
        db.collection("joinRequests").whereEqualTo("teacherCode", code).whereEqualTo("status", "pending").get()
            .addOnSuccessListener { p ->
                pending = p.documents.sortedBy { it.getLong("createdAt") ?: 0L }
                db.collection("joinRequests").whereEqualTo("teacherCode", code).whereEqualTo("status", "blocked").get()
                    .addOnSuccessListener { b ->
                        blocked = b.documents
                        db.collection("users").whereEqualTo("joined_teacher_code", code).get()
                            .addOnSuccessListener { u ->
                                students = u.documents.sortedBy { nameOf(it) }
                                selected.retainAll(pending.map { it.id }.toSet())
                                render()
                            }
                            .addOnFailureListener { loadError() }
                    }
                    .addOnFailureListener { loadError() }
            }
            .addOnFailureListener { loadError() }
    }

    // ---------- العرض ----------
    private fun render() {
        container.removeAllViews()
        pendingChecks.clear()
        container.addView(tv("👥 طلابي", 22f, true))

        // الإعدادات
        val autoBtn = Button(this)
        autoBtn.isAllCaps = false
        autoBtn.text = if (auto) "⚡ قبول الطلبات تلقائيًا: شغّال ✅" else "⚡ قبول الطلبات تلقائيًا: مقفول"
        autoBtn.setOnClickListener { setFlag("autoAccept", !auto) { auto = !auto } }
        container.addView(autoBtn)
        container.addView(tv("لو شغّال: أي طالب يكتب كودك يتقبل فورًا. لو مقفول: بتراجع الطلبات بنفسك.", 12f))

        val pauseBtn = Button(this)
        pauseBtn.isAllCaps = false
        pauseBtn.text = if (paused) "⏸ استقبال الطلبات: موقّف 🔴" else "▶ استقبال الطلبات: شغّال 🟢"
        pauseBtn.setOnClickListener { setFlag("joinPaused", !paused) { paused = !paused } }
        container.addView(pauseBtn)

        // الطلبات المعلّقة
        container.addView(tv("⏳ طلبات الانضمام (${pending.size})", 18f, true))
        if (pending.isEmpty()) {
            container.addView(tv("مفيش طلبات دلوقتي.", 14f))
        } else {
            val allCb = CheckBox(this)
            allCb.text = "تحديد الكل"
            allCb.setTextColor(0xFF000000.toInt())
            allCb.setOnCheckedChangeListener { _, on -> pendingChecks.forEach { it.isChecked = on } }
            container.addView(allCb)

            pending.forEach { d ->
                val cb = CheckBox(this)
                cb.text = "${nameOf(d)}\n${infoLine(d)}"
                cb.setTextColor(0xFF000000.toInt())
                cb.isChecked = d.id in selected
                cb.setOnCheckedChangeListener { _, on ->
                    if (on) selected.add(d.id) else selected.remove(d.id)
                    refreshSel()
                }
                pendingChecks.add(cb)
                container.addView(cb)
            }

            val accept = Button(this)
            accept.isAllCaps = false
            accept.setOnClickListener { confirm("تقبل الطلبات المحددة؟") { decide("accept") } }
            btnAcceptSel = accept
            container.addView(accept)

            val reject = Button(this)
            reject.isAllCaps = false
            reject.setOnClickListener { confirm("ترفض الطلبات المحددة؟") { decide("reject") } }
            btnRejectSel = reject
            container.addView(reject)

            val block = Button(this)
            block.isAllCaps = false
            block.setOnClickListener {
                confirm("تحظر الطلبات المحددة؟ مش هيقدروا يبعتوا طلب تاني.") { decide("block") }
            }
            btnBlockSel = block
            container.addView(block)

            val acceptAll = Button(this)
            acceptAll.isAllCaps = false
            acceptAll.text = "✅ اقبل كل الطلبات (${pending.size})"
            acceptAll.setOnClickListener {
                confirm("تقبل كل الطلبات (${pending.size})؟") { run(pending, "accept") }
            }
            container.addView(acceptAll)
            refreshSel()
        }

        // الطلاب
        container.addView(tv("✅ طلابي المقبولين (${students.size})", 18f, true))
        val search = EditText(this)
        search.hint = "🔍 دوّر بالاسم أو الرقم"
        search.setTextColor(0xFF000000.toInt())
        search.setText(query)
        search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString() ?: ""
                limit = 40
                renderStudents()
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        container.addView(search)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        listBox = box
        container.addView(box)
        renderStudents()

        // المحظورين
        if (blocked.isNotEmpty()) {
            container.addView(tv("🚫 المحظورين (${blocked.size})", 18f, true))
            blocked.forEach { d ->
                val b = Button(this)
                b.isAllCaps = false
                b.text = "🚫 ${nameOf(d)}  (اضغط لإلغاء الحظر)"
                b.setOnClickListener {
                    confirm("تلغي الحظر عن ${nameOf(d)}؟ هيقدر يبعت طلب انضمام تاني.") {
                        d.reference.delete()
                            .addOnSuccessListener { load() }
                            .addOnFailureListener {
                                Toast.makeText(this, "فشل إلغاء الحظر", Toast.LENGTH_SHORT).show()
                            }
                    }
                }
                container.addView(b)
            }
        }
    }

    private fun refreshSel() {
        btnAcceptSel?.text = "✅ اقبل المحدد (${selected.size})"
        btnRejectSel?.text = "❌ ارفض المحدد (${selected.size})"
        btnBlockSel?.text = "🚫 احظر المحدد (${selected.size})"
    }

    private fun renderStudents() {
        val box = listBox ?: return
        box.removeAllViews()
        val q = query.trim().lowercase()
        val filtered = students.filter {
            q.isEmpty() || nameOf(it).lowercase().contains(q) ||
                    (it.getString("name") ?: "").lowercase().contains(q) ||
                    (it.getString("phone") ?: "").contains(q) ||
                    (it.getString("parentPhone") ?: "").contains(q)
        }
        if (filtered.isEmpty()) {
            box.addView(tv(if (students.isEmpty()) "لسه مفيش طلاب." else "مفيش نتيجة للبحث.", 14f))
            return
        }
        filtered.take(limit).forEach { d ->
            val b = Button(this)
            b.isAllCaps = false
            val noParent = (d.getString("parentPhone") ?: "").isEmpty()
            b.text = "${nameOf(d)}\n${infoLine(d)}" + if (noParent) "\n⚠️ بياناته ناقصة" else ""
            b.setOnClickListener { studentMenu(d) }
            box.addView(b)
        }
        if (filtered.size > limit) {
            val more = Button(this)
            more.text = "عرض المزيد (${filtered.size - limit})"
            more.setOnClickListener {
                limit += 40
                renderStudents()
            }
            box.addView(more)
        }
    }

    // ---------- الإجراءات ----------
    private fun confirm(msg: String, onYes: () -> Unit) {
        AlertDialog.Builder(this)
            .setMessage(msg)
            .setPositiveButton("تأكيد") { _, _ -> onYes() }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun setFlag(field: String, value: Boolean, onOk: () -> Unit) {
        db.collection("teachers").document(code).set(hashMapOf<String, Any>(field to value), SetOptions.merge())
            .addOnSuccessListener {
                onOk()
                render()
            }
            .addOnFailureListener {
                Toast.makeText(this, "فشل الحفظ", Toast.LENGTH_SHORT).show()
            }
    }

    private fun decide(kind: String) {
        run(pending.filter { it.id in selected }, kind)
    }

    private fun run(docs: List<DocumentSnapshot>, kind: String) {
        if (docs.isEmpty()) {
            Toast.makeText(this, "اختار طلب واحد على الأقل", Toast.LENGTH_SHORT).show()
            return
        }
        var left = docs.size
        val after: (Boolean) -> Unit = { _ ->
            left -= 1
            if (left == 0) {
                val what = when (kind) {
                    "accept" -> "تم قبول ${docs.size} ✅"
                    "reject" -> "تم رفض ${docs.size}"
                    else -> "تم حظر ${docs.size}"
                }
                Toast.makeText(this, what, Toast.LENGTH_SHORT).show()
                selected.clear()
                load()
            }
        }
        docs.forEach { d ->
            when (kind) {
                "accept" -> JoinUtil.approve(
                    db, code, d.getString("identifier") ?: "", d.getString("studentName") ?: "",
                    d.getString("fullName") ?: "", d.getString("phone") ?: "",
                    d.getString("parentPhone") ?: "", after
                )
                "reject" -> JoinUtil.reject(d.reference, after)
                else -> JoinUtil.block(d.reference, after)
            }
        }
    }

    private fun studentMenu(d: DocumentSnapshot) {
        val phone = d.getString("phone") ?: ""
        val parent = d.getString("parentPhone") ?: ""
        AlertDialog.Builder(this)
            .setTitle(nameOf(d))
            .setItems(
                arrayOf("📞 اتصل بالطالب", "📞 اتصل بولي الأمر", "💬 واتساب لولي الأمر", "🚫 إخراج الطالب")
            ) { _, which ->
                when (which) {
                    0 -> dial(phone)
                    1 -> dial(parent)
                    2 -> whatsapp(parent)
                    3 -> confirm("تخرّج ${nameOf(d)}؟ هيتشال من قايمتك ومجموعاتك ومش هيقدر يدخل بالكود تاني.") {
                        JoinUtil.removeStudent(db, code, teacherName, d) { ok ->
                            Toast.makeText(this, if (ok) "تم إخراج الطالب" else "فشل الإخراج", Toast.LENGTH_SHORT).show()
                            if (ok) load()
                        }
                    }
                    else -> {}
                }
            }
            .show()
    }

    private fun dial(number: String) {
        if (number.isEmpty()) {
            Toast.makeText(this, "مفيش رقم متسجّل", Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))
    }

    private fun whatsapp(number: String) {
        val digits = number.filter { it.isDigit() }
        if (digits.isEmpty()) {
            Toast.makeText(this, "مفيش رقم متسجّل", Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits")))
    }
}
