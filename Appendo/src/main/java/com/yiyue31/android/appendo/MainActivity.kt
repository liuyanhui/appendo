package com.yiyue31.android.appendo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.yiyue31.android.appendo.data.FileRepository
import com.yiyue31.android.appendo.ui.MainScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.DEBUG) {
            android.util.Log.d("MainActivity", "onCreate called")
        }
        val fileRepository = FileRepository(this)
        if (BuildConfig.DEBUG) {
            android.util.Log.d("MainActivity", "FileRepository created, URI: ${fileRepository.getFileUri()}")
        }
        setContent {
            MainScreen(fileRepository)
        }
    }

    override fun onResume() {
        super.onResume()
        if (BuildConfig.DEBUG) {
            android.util.Log.d("MainActivity", "onResume called")
        }
    }
}
