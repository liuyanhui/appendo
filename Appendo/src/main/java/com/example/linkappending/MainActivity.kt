package com.example.linkappending

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.linkappending.data.FileRepository
import com.example.linkappending.ui.MainScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.d("MainActivity", "onCreate called")
        val fileRepository = FileRepository(this)
        android.util.Log.d("MainActivity", "FileRepository created, URI: ${fileRepository.getFileUri()}")
        setContent {
            MainScreen(fileRepository)
        }
    }

    override fun onResume() {
        super.onResume()
        android.util.Log.d("MainActivity", "onResume called")
    }
}
