package com.example.fishingidlegame.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    var isLoading by remember { mutableStateOf(false) }
    
    val infiniteTransition = rememberInfiniteTransition()
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(2000), RepeatMode.Reverse)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF050B14)),
        contentAlignment = Alignment.Center
    ) {
        // Fondo decorativo con gradiente
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        listOf(Color(0xFF1B2631), Color(0xFF050B14)),
                        radius = 2000f
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "OCEAN\nTITANS",
                fontSize = 54.sp,
                lineHeight = 50.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(glowAlpha + 0.3f)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Surface(
                color = Color(0xFFf9c74f),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    "PREMIUM ACCESS",
                    fontSize = 10.sp,
                    color = Color(0xFF0A1628),
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    letterSpacing = 2.sp
                )
            }

            Spacer(modifier = Modifier.height(64.dp))

            if (!isLoading) {
                Button(
                    onClick = {
                        isLoading = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("START ADVENTURE", color = Color(0xFF0A1628), fontWeight = FontWeight.Black, fontSize = 16.sp)
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                TextButton(onClick = { /* More options */ }) {
                    Text("MANAGE ACCOUNTS", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                LaunchedEffect(Unit) {
                    delay(1500) // Simulación de carga "AAA"
                    onLoginSuccess()
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFFf9c74f), strokeWidth = 3.dp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "SYNCHRONIZING DATA...", 
                        color = Color.White, 
                        fontSize = 10.sp, 
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(100.dp))
            
            Text(
                "V 2.0.4 - STABLE BUILD",
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.2f),
                fontWeight = FontWeight.Bold
            )
        }
    }
}
