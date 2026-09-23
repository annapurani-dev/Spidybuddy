package com.example.spidermanbuddy

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(50, 50, 50, 50)
        
        val btnLaunch = Button(this)
        btnLaunch.text = "Launch Spiderman Buddy!"
        btnLaunch.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivityForResult(intent, 1)
            } else {
                startService(Intent(this, FloatingService::class.java))
                Toast.makeText(this, "Spidey Launched! Double-tap him to close.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
        
        val btnStop = Button(this)
        btnStop.text = "Stop Spiderman Buddy"
        btnStop.setOnClickListener {
            stopService(Intent(this, FloatingService::class.java))
            Toast.makeText(this, "Spidey Stopped!", Toast.LENGTH_SHORT).show()
            finish()
        }
        
        layout.addView(btnLaunch)
        layout.addView(btnStop)
        
        setContentView(layout)
    }
}