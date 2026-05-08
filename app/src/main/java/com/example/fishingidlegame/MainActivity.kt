package com.example.fishingidlegame

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.fishingidlegame.data.GameRepository
import com.example.fishingidlegame.data.UserRepository
import com.example.fishingidlegame.ui.GameScreen
import com.example.fishingidlegame.ui.LoginScreen
import com.example.fishingidlegame.viewmodel.FishingViewModel
import com.example.fishingidlegame.viewmodel.FishingViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val repository = GameRepository(applicationContext)
        val userRepository = UserRepository(applicationContext)
        val factory = FishingViewModelFactory(repository)

        setContent {
            val viewModel: FishingViewModel = viewModel(factory = factory)

            // null = comprobando sesión, "" = sin sesión, "nombre" = con sesión
            var loggedInUsername by remember { mutableStateOf<String?>(null) }
            var sessionChecked by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                val activeUser = userRepository.getActiveUser()
                if (activeUser != null) {
                    viewModel.switchUser(activeUser.email, activeUser.firebaseId, activeUser.username)
                    loggedInUsername = activeUser.username
                }
                sessionChecked = true
            }

            MaterialTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    when {
                        !sessionChecked -> {
                            // Pantalla de fondo oscuro mientras se comprueba sesión
                            Box(Modifier.fillMaxSize().background(Color(0xFF050B14)))
                        }
                        loggedInUsername == null -> {
                            LoginScreen(
                                userRepository = userRepository,
                                onLoginSuccess = { user ->
                                    viewModel.switchUser(user.email, user.firebaseId, user.username)
                                    loggedInUsername = user.username
                                }
                            )
                        }
                        else -> {
                            GameScreen(viewModel, onLogout = {
                                userRepository.logout()
                                loggedInUsername = null
                            })
                        }
                    }
                }
            }
        }
    }
}
