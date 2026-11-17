package com.example.bac1

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object GeminiClient {

    class Result(val text: String?, val error: String?)

    private val models = listOf("gemini-flash-latest", "gemini-3.8-flash", "gemini-2.5-flash")

    // لازم تتندهي من Thread (مش من الـ UI)
    fun ask(system: String?, user: String, json: Boolean = false): Result {
        val key = BuildConfig.GEMINI_API_KEY
        if (key.isEmpty()) return Result(null, "المفتاح مش متضبط في النسخة دي")

        var lastError = "خطأ غير معروف"
        for (model in models) {
            val body = JSONObject()
            if (system != null) {
                body.put(
                    "systemInstruction",
                    JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system)))
                )
            }
            body.put(
                "contents",
                JSONArray().put(
                    JSONObject().put("role", "user")
                        .put("parts", JSONArray().put(JSONObject().put("text", user)))
                )
            )
            if (json) body.put("generationConfig", JSONObject().put("responseMimeType", "application/json"))

            var code = 0
            var resp = ""
            try {
                val conn = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
                    .openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 15000
                conn.readTimeout = 40000
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("x-goog-api-key", key)
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                resp = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                conn.disconnect()
            } catch (e: java.io.IOException) {
                return Result(null, "تعذّر الاتصال، تأكد من الإنترنت وحاول تاني")
            }

            if (code in 200..299) {
                val text = extract(resp)
                if (text.isNotEmpty()) return Result(text, null)
                lastError = "الرد فاضي، جرّب تصيغ السؤال بشكل تاني"
                continue
            }
            val msg = try {
                JSONObject(resp).getJSONObject("error").optString("message", "")
            } catch (e: Exception) {
                ""
            }
            lastError = describe(code, msg, model)
            if (code == 401 || code == 403 || (code == 400 && msg.contains("API key", true))) break
        }
        return Result(null, lastError)
    }

    private fun extract(resp: String): String {
        return try {
            val parts = JSONObject(resp).getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts")
            val sb = StringBuilder()
            for (i in 0 until parts.length()) {
                val p = parts.getJSONObject(i)
                if (p.optBoolean("thought", false)) continue
                sb.append(p.optString("text", ""))
            }
            sb.toString().trim()
        } catch (e: Exception) {
            ""
        }
    }

    private fun describe(code: Int, msg: String, model: String): String {
        val short = msg.take(140)
        return when (code) {
            400 -> if (msg.contains("API key", true)) "مفتاح الخدمة غير صالح ($code)" else "الطلب مرفوض ($code): $short"
            401, 403 -> "المفتاح معطّل أو مش مسموح ($code): $short"
            404 -> "الموديل $model مش متاح ($code)"
            429 -> "الحد المجاني خلص دلوقتي ($code)"
            else -> "خطأ من الخدمة ($code): $short"
        }
    }
}
