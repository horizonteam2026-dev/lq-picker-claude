package com.lqpicker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lqpicker.ui.DraftScreen
import com.lqpicker.ui.LQTheme
import com.lqpicker.ui.Pal

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LQTheme {
                val vm: DraftViewModel = viewModel()
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Pal.bg)
                        .safeDrawingPadding()
                ) {
                    DraftScreen(vm)
                }
            }
        }
    }
}
