package com.vikram.mindkeep

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.vikram.mindkeep.core.designsystem.theme.MindKeepTheme
import com.vikram.mindkeep.feature.MindKeepApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MindKeepTheme {
                MindKeepApp()
            }
        }
    }
}
