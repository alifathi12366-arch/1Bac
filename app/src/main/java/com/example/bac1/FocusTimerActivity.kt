package com.example.bac1

import android.graphics.Color
import android.media.RingtoneManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

class FocusTimerActivity : AppCompatActivity() {

    private enum class S { IDLE, RUNNING, PAUSED }

    private val handler = Handler(Looper.getMainLooper())
    private var state = S.IDLE
    private var studyPhase = true
    private var studyMs = 25 * 60_000L
    private var breakMs = 5 * 60_000L
    private var phaseEnd = 0L
    private var remainingMs = 0L
    private var cycles = 0

    private lateinit var etStudy: EditText
    private lateinit var etBreak: EditText
    private lateinit var tvPhase: TextView
    private lateinit var tvTime: TextView
    private lateinit var tvCycles: TextView
    private lateinit var btnStart: Button
    private lateinit var btnPause: Button
    private lateinit var btnReset: Button
    private lateinit var panelInputs: View

    private val prefs by lazy { getSharedPreferences("bac1_focus", MODE_PRIVATE) }

    private val tick = object : Runnable {
        override fun run() {
            if (state != S.RUNNING) return
            val now = System.currentTimeMillis()
            var advanced = false
            while (now >= phaseEnd) {
                if (studyPhase) cycles++
                studyPhase = !studyPhase
                phaseEnd += if (studyPhase) studyMs else breakMs
                advanced = true
            }
            if (advanced) {
                playSound()
                updatePhaseUi()
            }
            showTime(phaseEnd - now)
            handler.postDelayed(this, 250)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_focus_timer)

        etStudy = findViewById(R.id.etStudy)
        etBreak = findViewById(R.id.etBreak)
        tvPhase = findViewById(R.id.tvPhase)
        tvTime = findViewById(R.id.tvTime)
        tvCycles = findViewById(R.id.tvCycles)
        btnStart = findViewById(R.id.btnStartFocus)
        btnPause = findViewById(R.id.btnPauseFocus)
        btnReset = findViewById(R.id.btnResetFocus)
        panelInputs = findViewById(R.id.panelInputs)

        etStudy.setText(prefs.getInt("study_min", 25).toString())
        etBreak.setText(prefs.getInt("break_min", 5).toString())

        btnStart.setOnClickListener { startTimer() }
        btnPause.setOnClickListener { togglePause() }
        btnReset.setOnClickListener { resetTimer() }

        resetTimer()
    }

    private fun startTimer() {
        val s = etStudy.text.toString().trim().toIntOrNull()
        val b = etBreak.text.toString().trim().toIntOrNull()
        if (s == null || s !in 1..180) {
            Toast.makeText(this, "دقايق المذاكرة من 1 لـ 180", Toast.LENGTH_SHORT).show()
            return
        }
        if (b == null || b !in 1..60) {
            Toast.makeText(this, "دقايق الراحة من 1 لـ 60", Toast.LENGTH_SHORT).show()
            return
        }
        prefs.edit().putInt("study_min", s).putInt("break_min", b).apply()
        studyMs = s * 60_000L
        breakMs = b * 60_000L

        studyPhase = true
        cycles = 0
        phaseEnd = System.currentTimeMillis() + studyMs
        state = S.RUNNING
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        panelInputs.visibility = View.GONE
        btnStart.visibility = View.GONE
        btnPause.visibility = View.VISIBLE
        btnPause.text = "⏸ إيقاف مؤقت"
        btnReset.visibility = View.VISIBLE
        updatePhaseUi()
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    private fun togglePause() {
        if (state == S.RUNNING) {
            remainingMs = (phaseEnd - System.currentTimeMillis()).coerceAtLeast(0)
            state = S.PAUSED
            handler.removeCallbacks(tick)
            btnPause.text = "▶ استئناف"
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else if (state == S.PAUSED) {
            phaseEnd = System.currentTimeMillis() + remainingMs
            state = S.RUNNING
            btnPause.text = "⏸ إيقاف مؤقت"
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            handler.post(tick)
        }
    }

    private fun resetTimer() {
        state = S.IDLE
        handler.removeCallbacks(tick)
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        panelInputs.visibility = View.VISIBLE
        btnStart.visibility = View.VISIBLE
        btnPause.visibility = View.GONE
        btnReset.visibility = View.GONE

        tvPhase.text = "جاهز؟ 💪"
        tvPhase.setTextColor(Color.parseColor("#455A64"))
        tvTime.text = "--:--"
        tvCycles.text = ""
    }

    private fun updatePhaseUi() {
        if (studyPhase) {
            tvPhase.text = "📚 وقت المذاكرة"
            tvPhase.setTextColor(Color.parseColor("#2E7D32"))
        } else {
            tvPhase.text = "☕ وقت الراحة"
            tvPhase.setTextColor(Color.parseColor("#E65100"))
        }
        tvCycles.text = "جلسات المذاكرة المكتملة: $cycles"
    }

    private fun showTime(ms: Long) {
        val total = ((ms + 999) / 1000).coerceAtLeast(0)
        tvTime.text = String.format(Locale.US, "%02d:%02d", total / 60, total % 60)
    }

    private fun playSound() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            RingtoneManager.getRingtone(applicationContext, uri)?.play()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        super.onDestroy()
    }
}
