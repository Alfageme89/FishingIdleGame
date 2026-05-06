package com.example.fishingidlegame.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fishingidlegame.data.UserRepository

private val BgDark = Color(0xFF050B14)
private val CardBg = Color(0xFF0D1B2A)
private val Accent = Color(0xFF1B7FD4)
private val Gold = Color(0xFFf9c74f)
private val TextMuted = Color(0xFF8AAABB)
private val FieldBg = Color(0xFF0A1628)
private val ErrorColor = Color(0xFFFF6B6B)

@Composable
fun LoginScreen(userRepository: UserRepository, onLoginSuccess: (email: String, username: String) -> Unit) {
    var mode by remember { mutableStateOf(Mode.LOGIN) }
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glowAlpha"
    )

    Box(
        modifier = Modifier.fillMaxSize().background(BgDark),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.radialGradient(listOf(Color(0xFF1B2631), BgDark), radius = 2000f)
            )
        )

        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(48.dp))

            Text(
                "OCEAN\nTITANS",
                fontSize = 52.sp, lineHeight = 48.sp,
                fontWeight = FontWeight.Black, color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(glowAlpha)
            )

            Spacer(Modifier.height(6.dp))

            Surface(color = Gold, shape = RoundedCornerShape(4.dp)) {
                Text(
                    "FISHING IDLE",
                    fontSize = 10.sp, color = BgDark,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                    letterSpacing = 2.sp
                )
            }

            Spacer(Modifier.height(32.dp))

            // Tab selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(CardBg)
                    .padding(4.dp)
            ) {
                TabButton("Iniciar Sesión", mode == Mode.LOGIN, Modifier.weight(1f)) { mode = Mode.LOGIN }
                TabButton("Registrarse", mode == Mode.REGISTER, Modifier.weight(1f)) { mode = Mode.REGISTER }
            }

            Spacer(Modifier.height(16.dp))

            AnimatedContent(
                targetState = mode,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
                label = "formTransition"
            ) { currentMode ->
                when (currentMode) {
                    Mode.LOGIN -> LoginForm(userRepository) { email, username -> onLoginSuccess(email, username) }
                    Mode.REGISTER -> RegisterForm(userRepository) { email, username -> onLoginSuccess(email, username) }
                }
            }

            Spacer(Modifier.height(40.dp))

            Text(
                "v0.2.0 — Local Auth",
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.15f),
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun LoginForm(userRepository: UserRepository, onLoginSuccess: (email: String, username: String) -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CardBg).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AuthField(
            value = email, onValueChange = { email = it; error = null },
            label = "Correo electrónico",
            keyboardType = KeyboardType.Email
        )

        AuthField(
            value = password, onValueChange = { password = it; error = null },
            label = "Contraseña",
            keyboardType = KeyboardType.Password,
            isPassword = true,
            passwordVisible = passwordVisible,
            onTogglePassword = { passwordVisible = !passwordVisible }
        )

        if (error != null) {
            Text(error!!, color = ErrorColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(4.dp))

        Button(
            onClick = {
                error = validateLogin(email, password)
                if (error == null) {
                    loading = true
                    val result = userRepository.login(email, password)
                    result.fold(
                        onSuccess = { user -> onLoginSuccess(user.email, user.username) },
                        onFailure = { e -> error = e.message; loading = false }
                    )
                }
            },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Accent),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (loading) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            } else {
                Text("ENTRAR", fontWeight = FontWeight.Black, fontSize = 15.sp, letterSpacing = 1.sp)
            }
        }
    }
}

@Composable
private fun RegisterForm(userRepository: UserRepository, onLoginSuccess: (email: String, username: String) -> Unit) {
    var email by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CardBg).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AuthField(
            value = email, onValueChange = { email = it; error = null },
            label = "Correo electrónico",
            keyboardType = KeyboardType.Email
        )

        AuthField(
            value = username, onValueChange = { username = it; error = null },
            label = "Nombre de usuario"
        )

        AuthField(
            value = password, onValueChange = { password = it; error = null },
            label = "Contraseña",
            keyboardType = KeyboardType.Password,
            isPassword = true,
            passwordVisible = passwordVisible,
            onTogglePassword = { passwordVisible = !passwordVisible }
        )

        AuthField(
            value = confirmPassword, onValueChange = { confirmPassword = it; error = null },
            label = "Confirmar contraseña",
            keyboardType = KeyboardType.Password,
            isPassword = true,
            passwordVisible = passwordVisible,
            onTogglePassword = { passwordVisible = !passwordVisible }
        )

        if (error != null) {
            Text(error!!, color = ErrorColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(4.dp))

        Button(
            onClick = {
                error = validateRegister(email, username, password, confirmPassword)
                if (error == null) {
                    loading = true
                    val result = userRepository.register(email, username, password)
                    result.fold(
                        onSuccess = { user -> onLoginSuccess(user.email, user.username) },
                        onFailure = { e -> error = e.message; loading = false }
                    )
                }
            },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Gold),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (loading) {
                CircularProgressIndicator(color = BgDark, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            } else {
                Text("CREAR CUENTA", color = BgDark, fontWeight = FontWeight.Black, fontSize = 15.sp, letterSpacing = 1.sp)
            }
        }
    }
}

@Composable
private fun TabButton(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) Accent else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            color = if (selected) Color.White else TextMuted
        )
    }
}

@Composable
private fun AuthField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    onTogglePassword: (() -> Unit)? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            trailingIcon = if (isPassword) ({
                IconButton(onClick = { onTogglePassword?.invoke() }) {
                    Icon(
                        if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = null,
                        tint = TextMuted
                    )
                }
            }) else null,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = FieldBg,
                unfocusedContainerColor = FieldBg,
                focusedBorderColor = Accent,
                unfocusedBorderColor = Color(0xFF1E3A4A),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = Accent
            ),
            shape = RoundedCornerShape(10.dp)
        )
    }
}

private fun validateLogin(email: String, password: String): String? {
    if (email.isBlank()) return "Introduce tu correo"
    if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) return "Correo no válido"
    if (password.isBlank()) return "Introduce tu contraseña"
    return null
}

private fun validateRegister(email: String, username: String, password: String, confirm: String): String? {
    if (email.isBlank()) return "Introduce un correo"
    if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) return "Correo no válido"
    if (username.isBlank()) return "Introduce un nombre de usuario"
    if (username.length < 3) return "El nombre debe tener al menos 3 caracteres"
    if (password.length < 6) return "La contraseña debe tener al menos 6 caracteres"
    if (password != confirm) return "Las contraseñas no coinciden"
    return null
}

private enum class Mode { LOGIN, REGISTER }
