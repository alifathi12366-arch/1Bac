package com.example.bac1

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class AiTutorActivity : AppCompatActivity() {

    private lateinit var etQuestion: EditText
    private lateinit var btnAsk: Button
    private lateinit var progressAi: ProgressBar
    private lateinit var tvAnswer: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_tutor)

        etQuestion = findViewById(R.id.etQuestion)
        btnAsk = findViewById(R.id.btnAsk)
        progressAi = findViewById(R.id.progressAi)
        tvAnswer = findViewById(R.id.tvAnswer)

        btnAsk.setOnClickListener { askAi() }
    }

    private fun askAi() {
        val question = etQuestion.text.toString().trim()
        if (question.isEmpty()) {
            Toast.makeText(this, "اكتب سؤالك الأول يا فنان", Toast.LENGTH_SHORT).show()
            return
        }

        progressAi.visibility = View.VISIBLE
        btnAsk.isEnabled = false
        tvAnswer.text = "جاري التفكير وتحضير الشرح..."

        Thread {
            var answerStr: String? = null
            try {
                // التعليمات المخصصة حصرياً لمناهج أولى ثانوي ومطالبك الدقيقة
                val systemPrompt = "أنت مدرس خبير ومساعد تعليمي ذكي مخصص حصرياً لمناهج الصف الأول الثانوي في مصر. " +
                        "المواد المتاحة لك فقط هي: (اللغة العربية، اللغة الإنجليزية، التاريخ، العلوم المتكاملة، الفلسفة والمنطق، الرياضيات، اللغة الفرنسية، التربية الدينية الإسلامية، البرمجة، اللغة الإيطالية، واللغة الألمانية). " +
                        "قواعد العمل: " +
                        "1. اجعل إجاباتك مبسطة، مشجعة، ومناسبة لطالب في اولى ثانوي، وبأسلوب ودود ومحفز. " +
                        "2. التزم تماماً بمنهج المواد المذكورة فقط، وممنوع الإجابة عن أي مواضيع خارج المنهج أو المرحلة الدراسية. " +
                        "3. لو السؤال غير واضح أو خارج المنهج، اعتذر بلطف ووجه الطالب ليسأل في المواد الدراسية المقررة. " +
                        "4. عند شرح المسائل أو القواعد، وضح الخطوات ببساطة. " +
                        "سؤال الطالب هو: $question"

                val body = JSONObject()
                    .put("contents", JSONArray().put(
                        JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt)))
                    ))

                val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("x-goog-api-key", BuildConfig.GEMINI_API_KEY)
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val resp = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                conn.disconnect()

                if (code in 200..299) {
                    val parts = JSONObject(resp).getJSONArray("candidates").getJSONObject(0)
                        .getJSONObject("content").getJSONArray("parts")
                    val sb = StringBuilder()
                    for (i in 0 until parts.length()) {
                        val p = parts.getJSONObject(i)
                        sb.append(p.optString("text", ""))
                    }
                    answerStr = sb.toString().trim()
                } else {
                    answerStr = "حصل مشكلة في الاتصال بالمدرس الذكي (كود: $code)، حاول تاني."
                }
            } catch (e: Exception) {
                e.printStackTrace()
                answerStr = "تعذّر الاتصال، تأكد من الإنترنت وحاول تاني."
            }

            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                progressAi.visibility = View.GONE
                btnAsk.isEnabled = true
                tvAnswer.text = answerStr ?: "عذراً، حدث خطأ غير معروف."
            }
        }.start()
    }
}
