package com.vikram.mindkeep
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.vikram.mindkeep.core.designsystem.theme.MindKeepTheme
import com.vikram.mindkeep.navigation.MindKeepNavHost
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MindKeepTheme { MindKeepNavHost() } }
    }
}
