package com.example.bac1

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.github.chrisbanes.photoview.PhotoView

class ImageViewerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_viewer)

        val url = intent.getStringExtra("imageUrl") ?: ""
        val photoView = findViewById<PhotoView>(R.id.photoView)

        photoView.maximumScale = 6f
        Glide.with(this).load(url).into(photoView)

        findViewById<TextView>(R.id.btnClose).setOnClickListener { finish() }
    }
}
