package com.example.spidermanbuddy

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val button = Button(this)
        button.text = "Launch Spiderman Buddy!"
        button.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivityForResult(intent, 1)
            } else {
                startService(Intent(this, FloatingService::class.java))
                Toast.makeText(this, "Spidey Launched!", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
        setContentView(button)
    }
}