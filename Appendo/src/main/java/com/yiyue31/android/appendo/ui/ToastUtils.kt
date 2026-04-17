package com.yiyue31.android.appendo.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.widget.Toast

/**
 * Show a toast with centered text at the bottom of screen
 */
fun showToast(context: Context, message: String) {
    val toast = Toast.makeText(context, "", Toast.LENGTH_SHORT)

    // Create a custom TextView for the toast with proper styling
    val textView = android.widget.TextView(context).apply {
        text = message
        setTextColor(Color.WHITE)
        textSize = 16f
        gravity = android.view.Gravity.CENTER

        // Add background with rounded corners
        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.parseColor("#CC000000")) // Semi-transparent black
            cornerRadius = 24f
        }
        background = drawable

        setPadding(48, 24, 48, 24)
    }

    toast.view = textView
    // Don't set gravity - let it use default (bottom center)
    toast.show()
}
