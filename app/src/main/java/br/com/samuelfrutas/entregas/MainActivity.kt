package br.com.samuelfrutas.entregas

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
            setBackgroundColor(Color.rgb(16, 24, 20))
        }

        val title = TextView(this).apply {
            text = "Samuel Frutas"
            textSize = 30f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        val subtitle = TextView(this).apply {
            text = "Sistema de Entregas"
            textSize = 18f
            setTextColor(Color.rgb(53, 199, 89))
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 0)
        }

        root.addView(title)
        root.addView(subtitle)
        setContentView(root)
    }
}
