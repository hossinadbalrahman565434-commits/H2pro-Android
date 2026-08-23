package com.h2pro.app

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val view = TextView(this).apply {
            text = "H2pro\nمرحباً بك في تطبيقك 🚀"
            textSize = 26f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
        }
        setContentView(view)
    }
}
