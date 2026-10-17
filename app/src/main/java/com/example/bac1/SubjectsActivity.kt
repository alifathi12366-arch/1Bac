package com.example.bac1

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SubjectsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_subjects)

        setupSubject(
            cardId = R.id.cardArabic,
            subjectName = "اللغة العربية",
            desc = "القراءة، النصوص، الأدب، النحو",
            colorHex = "#F5C518",
            bookLink = "https://drive.google.com/file/d/1_wabND8G53RlCFxyIeIP4NYOAJSesBT7/view?usp=drivesdk",
            teacherLink = "https://youtube.com/@mohamedsalah.bassthalk?si=BMSSudooCyUUFvoo",
            bookLink3 = "https://drive.google.com/file/d/1c2peDQbPRr8Kbgu_5pY3VaaRqguWeb_g/view?usp=drivesdk",
            bookLabel1 = "كتاب القراءة والنصوص",
            bookLabel3 = "كتاب القصة",
            subjectKey = "arabic"
        )
        setupSubject(R.id.cardEnglish, "اللغة الإنجليزية", "Reading Comprehension & Writing", "#4FA3E3", "https://drive.google.com/file/d/1Z39u9VOs8SH6nrHxZ3IE3SznDxbQPu5V/view?usp=drivesdk", "https://youtube.com/@mahmoudradwan.englishawy?si=IG4D6QJ7oUNo8nVA", subjectKey = "english")
        setupSubject(R.id.cardMath, "الرياضيات", "الحساب، الدوال، الهندسة", "#3DBFA0", "https://drive.google.com/file/d/1oxls3xtQ4D6UgsXchYI4jB_YU2tedCET/view?usp=drivesdk", "https://youtube.com/@-mrlotfyzahran4469?si=PbZcVrEKd7o8DJYG", subjectKey = "math")
        setupSubject(R.id.cardScience, "العلوم المتكاملة", "الفيزياء والكيمياء وعلوم الحياة", "#5C7CFA", "https://drive.google.com/file/d/1KtD56ULXsVh1imQaN4JIIwjPoFtjdhdr/view?usp=drivesdk", "https://youtube.com/@gohary?si=Gq5uRgIbQWSCmOnu", subjectKey = "science")
        setupSubject(R.id.cardPhilosophy, "الفلسفة والمنطق", "المفاهيم الفلسفية والاستدلال", "#9ACD32", "https://drive.google.com/file/d/1WO_PYfE_j5SIOKMpX0UMah85cbkuM1k1/view?usp=drivesdk", "https://youtube.com/@filasofmasr?si=_DUPM-YnU_foOrud", subjectKey = "philosophy")
        setupSubject(R.id.cardHistory, "التاريخ", "الحضارات القديمة والتاريخ المعاصر", "#E06B9E", "https://drive.google.com/file/d/1bZgJHy_363Ic1J4csuEUOsK3h0VzB0rn/view?usp=drivesdk", "https://youtube.com/@-mostafaarafa4617?si=qrWg04WNl9jGYVdu", subjectKey = "history")
        setupSubject(R.id.cardProgramming, "البرمجة", "أساسيات البرمجة والذكاء الاصطناعي", "#8E9BAE", "https://drive.google.com/file/d/1AtcPFcl7ZNciYnFwSfFLv3mdX5T9tq7z/view?usp=drivesdk", "", subjectKey = "programming")
        setupSubject(R.id.cardIslamic, "التربية الإسلامية", "العقيدة، الفقه، والسيرة النبوية", "#7B7FE0", "https://drive.google.com/file/d/1adHx4kesuOnn965bqjGIp-iUrpBDNc1b/view?usp=drivesdk", "", subjectKey = "")
        setupSubject(R.id.cardFrench, "اللغة الفرنسية", "القواعد والتعبير الكتابي والفهم", "#B58ED6", "https://drive.google.com/file/d/1SWQm7elUNKIM9mEBzogFnbi9ld6UGfbn/view?usp=drivesdk", "", subjectKey = "french")
    }

    private fun setupSubject(
        cardId: Int,
        subjectName: String,
        desc: String,
        colorHex: String,
        bookLink: String,
        teacherLink: String,
        bookLink2: String = "",
        bookLink3: String = "",
        bookLabel1: String = "فتح كتاب المادة",
        bookLabel2: String = "فتح كتاب المادة",
        bookLabel3: String = "فتح كتاب المادة",
        subjectKey: String = ""
    ) {
        val card = findViewById<View>(cardId) ?: return
        val nameView = card.findViewById<TextView>(R.id.subjectName)
        val descView = card.findViewById<TextView>(R.id.subjectDesc)
        val iconBox = card.findViewById<LinearLayout>(R.id.iconBox)

        nameView?.text = subjectName
        descView?.text = desc
        iconBox?.setBackgroundColor(Color.parseColor(colorHex))

        card.setOnClickListener {
            val intent = Intent(this, SubjectDetailActivity::class.java)
            intent.putExtra("subjectName", subjectName)
            intent.putExtra("subjectKey", subjectKey)
            intent.putExtra("bookLink", bookLink)
            intent.putExtra("bookLink2", bookLink2)
            intent.putExtra("bookLink3", bookLink3)
            intent.putExtra("bookLabel1", bookLabel1)
            intent.putExtra("bookLabel2", bookLabel2)
            intent.putExtra("bookLabel3", bookLabel3)
            intent.putExtra("teacherLink", teacherLink)
            startActivity(intent)
        }
    }
}
