package com.example.fishingidlegame

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import com.example.fishingidlegame.data.GameRepository
import com.example.fishingidlegame.ui.GameScreen
import com.example.fishingidlegame.ui.LoginScreen
import com.example.fishingidlegame.viewmodel.FishingViewModel
import com.example.fishingidlegame.viewmodel.FishingViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Inicializamos el repositorio
        val repository = GameRepository(applicationContext)
        val factory = FishingViewModelFactory(repository)

        setContent {
            // El ViewModel ahora se crea usando la Factory
            val viewModel: FishingViewModel = viewModel(factory = factory)
            var isLoggedIn by remember { mutableStateOf(false) }

            MaterialTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    if (!isLoggedIn) {
                        LoginScreen(onLoginSuccess = { isLoggedIn = true })
                    } else {
                        GameScreen(viewModel)
                    }
                }
            }
        }
    }
}
