package com.example.clockedge

import android.app.Activity
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK)
            setPadding(64, 64, 64, 64)
        }
        val info = TextView(this).apply {
            text = "ClockEdge\n\nAppuyez sur le bouton, puis \"Définir comme fond d'écran\" (écran de verrouillage ou les deux)."
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            textSize = 16f
        }
        val button = Button(this).apply {
            text = "Choisir ce fond d'écran"
            setOnClickListener {
                val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(
                    WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    ComponentName(this@MainActivity, ClockWallpaperService::class.java)
                )
                startActivity(intent)
            }
        }
        layout.addView(info)
        layout.addView(button)
        setContentView(layout)
    }
}
