package com.example.bac1

import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class LibraryActivity : AppCompatActivity() {

    private lateinit var adapter: LibraryAdapter
    private var allItems: MutableList<LibraryItem> = mutableListOf()

    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var currentAudioPath: String = ""

    private val subjects = arrayOf(
        "اللغة العربية", "اللغة الإنجليزية", "الرياضيات", "العلوم المتكاملة",
        "الفلسفة والمنطق", "التاريخ", "البرمجة", "التربية الإسلامية", "اللغة الفرنسية", "عام"
    )

    private val pickFileLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            askForSubjectThenSave(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_library)

        allItems = LibraryStorage.loadItems(this)

        val recyclerView = findViewById<RecyclerView>(R.id.libraryRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = LibraryAdapter(
            allItems,
            onItemClick = { item -> openItem(item) },
            onDeleteClick = { item -> deleteItem(item) }
        )
        recyclerView.adapter = adapter

        findViewById<android.widget.Button>(R.id.btnUploadFile).setOnClickListener {
            pickFileLauncher.launch("*/*")
        }

        findViewById<android.widget.Button>(R.id.btnRecordAudio).setOnClickListener {
            toggleRecording()
        }

        findViewById<android.widget.Button>(R.id.btnAddNote).setOnClickListener {
            showAddNoteDialog()
        }

        findViewById<EditText>(R.id.searchBox).addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                filterList(s.toString())
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun filterList(query: String) {
        val filtered = if (query.isBlank()) {
            allItems
        } else {
            allItems.filter { it.fileName.contains(query, ignoreCase = true) }
        }
        adapter.updateList(filtered.toMutableList())
    }

    private fun getFileType(uri: Uri): String {
        val type = contentResolver.getType(uri) ?: ""
        return when {
            type.startsWith("image") -> "image"
            type.startsWith("video") -> "video"
            type.startsWith("audio") -> "audio"
            type.contains("pdf") -> "pdf"
            else -> "pdf"
        }
    }

    private fun askForSubjectThenSave(uri: Uri) {
        AlertDialog.Builder(this)
            .setTitle("اختر المادة")
            .setItems(subjects) { _, which ->
                saveFileToInternalStorage(uri, subjects[which])
            }
            .show()
    }

    private fun saveFileToInternalStorage(uri: Uri, subject: String) {
        try {
            val fileType = getFileType(uri)
            val fileName = "${UUID.randomUUID()}_${fileType}"
            val destFile = File(filesDir, fileName)

            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }

            val item = LibraryItem(
                id = UUID.randomUUID().toString(),
                fileName = "ملف $fileType",
                filePath = destFile.absolutePath,
                fileType = fileType,
                subject = subject,
                noteText = "",
                dateAdded = System.currentTimeMillis()
            )

            LibraryStorage.addItem(this, item)
            allItems = LibraryStorage.loadItems(this)
            adapter.updateList(allItems)
            Toast.makeText(this, "تم الحفظ", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Toast.makeText(this, "حصل خطأ في الحفظ", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    private fun toggleRecording() {
        if (!isRecording) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(android.Manifest.permission.RECORD_AUDIO), 100
                )
                return
            }
            startRecording()
        } else {
            stopRecordingAndSave()
        }
    }

    private fun startRecording() {
        val fileName = "${UUID.randomUUID()}_audio.3gp"
        val destFile = File(filesDir, fileName)
        currentAudioPath = destFile.absolutePath

        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            setOutputFile(currentAudioPath)
            prepare()
            start()
        }
        isRecording = true
        findViewById<android.widget.Button>(R.id.btnRecordAudio).text = "إيقاف التسجيل"
        Toast.makeText(this, "بدأ التسجيل...", Toast.LENGTH_SHORT).show()
    }

    private fun stopRecordingAndSave() {
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
            mediaRecorder = null
            isRecording = false
            findViewById<android.widget.Button>(R.id.btnRecordAudio).text = "تسجيل صوت"

            AlertDialog.Builder(this)
                .setTitle("اختر المادة")
                .setItems(subjects) { _, which ->
                    val item = LibraryItem(
                        id = UUID.randomUUID().toString(),
                        fileName = "تسجيل صوتي",
                        filePath = currentAudioPath,
                        fileType = "audio",
                        subject = subjects[which],
                        noteText = "",
                        dateAdded = System.currentTimeMillis()
                    )
                    LibraryStorage.addItem(this, item)
                    allItems = LibraryStorage.loadItems(this)
                    adapter.updateList(allItems)
                    Toast.makeText(this, "تم حفظ التسجيل", Toast.LENGTH_SHORT).show()
                }
                .show()

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showAddNoteDialog() {
        val editText = EditText(this)
        AlertDialog.Builder(this)
            .setTitle("ملاحظة جديدة")
            .setView(editText)
            .setPositiveButton("حفظ") { _, _ ->
                val noteText = editText.text.toString()
                if (noteText.isNotBlank()) {
                    AlertDialog.Builder(this)
                        .setTitle("اختر المادة")
                        .setItems(subjects) { _, which ->
                            val item = LibraryItem(
                                id = UUID.randomUUID().toString(),
                                fileName = noteText.take(20),
                                filePath = "",
                                fileType = "note",
                                subject = subjects[which],
                                noteText = noteText,
                                dateAdded = System.currentTimeMillis()
                            )
                            LibraryStorage.addItem(this, item)
                            allItems = LibraryStorage.loadItems(this)
                            adapter.updateList(allItems)
                        }
                        .show()
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun openItem(item: LibraryItem) {
        if (item.fileType == "note") {
            AlertDialog.Builder(this)
                .setTitle(item.fileName)
                .setMessage(item.noteText)
                .setPositiveButton("حسنًا", null)
                .show()
            return
        }

        try {
            val file = File(item.filePath)
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(uri, contentResolver.getType(uri))
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "مفيش تطبيق يقدر يفتح الملف ده", Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteItem(item: LibraryItem) {
        AlertDialog.Builder(this)
            .setTitle("حذف")
            .setMessage("متأكد عايز تمس
