package com.example.fishingidlegame.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fishingidlegame.config.GameConfig
import com.example.fishingidlegame.model.Fish
import com.example.fishingidlegame.viewmodel.FishingViewModel
import kotlin.math.sin

@Composable
fun GameScreen(viewModel: FishingViewModel) {
    val state by viewModel.state.collectAsState()
    val fishList by viewModel.fishList.collectAsState()

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0A1628))) {
        // 1. Mundo (Canvas)
        GameCanvas(state, fishList, onLaunch = { viewModel.launchHook(240f) })

        // 2. HUD Superior
        GameHUD(state)

        // 3. Overlay Inicial
        AnimatedVisibility(
            visible = state.gamePhase == "MENU",
            enter = fadeIn() + scaleIn(initialScale = 0.9f),
            exit = fadeOut() + scaleOut(targetScale = 1.1f),
            modifier = Modifier.align(Alignment.Center)
        ) {
            GameOverlay(onLaunch = { viewModel.launchHook(240f) })
        }

        // 4. Barra de Peso Dinámica
        if (state.gamePhase != "MENU") {
            WeightBar(
                currentKg = state.currentKg,
                maxKg = GameConfig.upgrades["weight"]?.values?.get(state.upgLevels["weight"] ?: 0) ?: 10f,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 120.dp)
            )
        }

        // 5. Toasts / Notificaciones
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            state.toastMessage?.let { msg ->
                GameToast(msg)
            }
        }

        // 6. Menú de Mejoras Inferior
        AnimatedVisibility(
            visible = state.gamePhase == "MENU",
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column {
                UpgradeMenu(state, onUpgrade = { viewModel.buyUpgrade(it) })
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun GameOverlay(onLaunch: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition()
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse)
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(32.dp)
    ) {
        Text(
            "OCEAN\nTITANS",
            fontSize = 64.sp,
            lineHeight = 60.sp,
            fontWeight = FontWeight.Black,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.shadow(10.dp, CircleShape)
        )
        Text(
            "THE ULTIMATE FISHING SIMULATOR",
            fontSize = 12.sp,
            letterSpacing = 4.sp,
            color = Color(0xFFf9c74f),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 8.dp)
        )
        Spacer(modifier = Modifier.height(64.dp))
        Button(
            onClick = onLaunch,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFf9c74f)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .height(64.dp)
                .width(240.dp)
                .graphicsLayer(scaleX = scale, scaleY = scale),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
        ) {
            Text("LAUNCH EXPEDITION", color = Color(0xFF0A1628), fontWeight = FontWeight.Black, fontSize = 18.sp)
        }
    }
}

@Composable
fun GameToast(message: String) {
    Surface(
        color = Color(0xEEf9c74f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.padding(top = 100.dp).animateContentSize()
    ) {
        Text(
            text = message,
            color = Color(0xFF0A1628),
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            fontWeight = FontWeight.Black,
            fontSize = 14.sp
        )
    }
}

@Composable
fun GameCanvas(state: com.example.fishingidlegame.model.GameState, fishList: List<Fish>, onLaunch: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition()
    val waveOffset by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing))
    )

    Canvas(modifier = Modifier.fillMaxSize().clickable { if (state.gamePhase == "MENU") onLaunch() }) {
        val scale = size.width / 480f
        val surfaceY = 400f * scale
        val camOffset = state.camY * scale

        // 1. Cielo con Degradado Atmosférico
        drawRect(
            brush = Brush.verticalGradient(listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364))),
            size = Size(size.width, surfaceY - camOffset)
        )

        // 2. Agua Profunda
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color(0xFF1898a8),
                0.2f to Color(0xFF0A1628),
                1f to Color(0xFF050B14),
                startY = (surfaceY - camOffset).coerceAtLeast(0f)
            ),
            topLeft = Offset(0f, (surfaceY - camOffset).coerceAtLeast(0f)),
            size = Size(size.width, size.height)
        )

        // 3. Ondas de Superficie
        if (surfaceY - camOffset > -50) {
            for (i in 0..10) {
                val x = (i * 100 * scale + waveOffset * scale) % size.width
                drawCircle(Color.White.copy(alpha = 0.1f), radius = 20f * scale, center = Offset(x, surfaceY - camOffset))
            }
        }

        // 4. Peces con Efectos
        fishList.forEach { fish ->
            val fx = fish.x * scale
            val fy = (fish.y * scale) - camOffset
            
            // Solo dibujar si está en pantalla
            if (fy > -100 && fy < size.height + 100) {
                rotate(degrees = if (fish.vx > 0) 0f else 180f, pivot = Offset(fx, fy)) {
                    // Cuerpo del pez (Sombra)
                    drawOval(
                        color = Color.Black.copy(alpha = 0.3f),
                        topLeft = Offset(fx - fish.type.width*scale/2 + 5, fy - fish.type.height*scale/2 + 5),
                        size = Size(fish.type.width * scale, fish.type.height * scale)
                    )
                    // Cuerpo del pez
                    drawOval(
                        color = fish.type.color,
                        topLeft = Offset(fx - fish.type.width*scale/2, fy - fish.type.height*scale/2),
                        size = Size(fish.type.width * scale, fish.type.height * scale)
                    )
                    // Ojo
                    drawCircle(Color.White, radius = 2f * scale, center = Offset(fx + fish.type.width*scale/3, fy - 2*scale))
                }
            }
        }

        // 5. Barco con Animación de Balanceo
        val boatX = size.width / 2
        val boatY = surfaceY - camOffset
        if (boatY > -200) {
            val tilt = sin(waveOffset * 0.05f) * 2f
            rotate(degrees = tilt, pivot = Offset(boatX, boatY)) {
                drawBoat(boatX, boatY, scale)
            }
        }

        // 6. Línea de Pesca y Anzuelo Pro
        if (state.gamePhase != "MENU") {
            val hX = state.hookX * scale
            val hY = (state.hookY * scale) - camOffset
            
            // Línea con curva catenaria simulada
            val path = Path().apply {
                moveTo(boatX + 40 * scale, boatY - 60 * scale)
                quadraticBezierTo(
                    (boatX + 40 * scale + hX) / 2, (boatY - 60 * scale + hY) / 2,
                    hX, hY
                )
            }
            drawPath(path, Color.White, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f * scale))
            
            // Anzuelo Glow
            drawCircle(
                brush = Brush.radialGradient(listOf(Color(0xFFf9c74f), Color.Transparent), center = Offset(hX, hY), radius = 20f * scale),
                radius = 20f * scale,
                center = Offset(hX, hY)
            )
            drawCircle(Color(0xFFf9c74f), radius = 4f * scale, center = Offset(hX, hY))
        }
    }
}

fun DrawScope.drawBoat(x: Float, y: Float, scale: Float) {
    val bW = 160f * scale
    val bH = 45f * scale
    
    // Casco Moderno
    val path = Path().apply {
        moveTo(x - bW/2, y - 15*scale)
        lineTo(x + bW/2, y - 15*scale)
        lineTo(x + bW/2 - 25*scale, y + bH/2)
        lineTo(x - bW/2 + 20*scale, y + bH/2)
        close()
    }
    drawPath(path, Color(0xFF2C3E50))
    drawPath(path, Color(0xFFE74C3C), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f * scale))
    
    // Cabina Pro
    drawRect(Color(0xFFECF0F1), topLeft = Offset(x - 35*scale, y - 55*scale), size = Size(55*scale, 40*scale))
    drawRect(Color(0xFF3498DB), topLeft = Offset(x - 30*scale, y - 50*scale), size = Size(20*scale, 15*scale)) // Ventana
    
    // Antenas/Mástil
    drawLine(Color(0xFFBDC3C7), Offset(x + 15*scale, y - 15*scale), Offset(x + 15*scale, y - 100*scale), strokeWidth = 2f * scale)
}

@Composable
fun WeightBar(currentKg: Float, maxKg: Float, modifier: Modifier) {
    val progress by animateFloatAsState(targetValue = (currentKg / maxKg).coerceIn(0f, 1f))
    val color = when {
        progress > 0.9f -> Color(0xFFE74C3C)
        progress > 0.7f -> Color(0xFFF39C12)
        else -> Color(0xFF2ECC71)
    }

    Column(modifier = modifier.width(280.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("CARGO CAPACITY", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text("${"%.1f".format(currentKg)} / ${maxKg.toInt()} KG", color = color, fontSize = 12.sp, fontWeight = FontWeight.Black)
        }
        Box(modifier = Modifier.fillMaxWidth().height(14.dp).background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(4.dp))) {
            Box(modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress)
                .background(color, RoundedCornerShape(4.dp))
                .shadow(4.dp, RoundedCornerShape(4.dp))
            )
        }
    }
}

@Composable
fun GameHUD(state: com.example.fishingidlegame.model.GameState) {
    Row(
        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column {
            Text("BALANCE", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text("$${state.score}", color = Color(0xFFf9c74f), fontSize = 28.sp, fontWeight = FontWeight.Black)
        }
        Surface(
            color = Color.White.copy(alpha = 0.05f),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
        ) {
            Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("⚓", fontSize = 16.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("${state.totalFishCaught}", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun UpgradeMenu(state: com.example.fishingidlegame.model.GameState, onUpgrade: (String) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(GameConfig.upgrades.values.toList()) { upgrade ->
            val level = state.upgLevels[upgrade.id] ?: 0
            val cost = if (level < upgrade.levels.size) upgrade.levels[level] else -1
            UpgradeCard(upgrade, level, cost, canAfford = state.score >= cost && cost != -1) { onUpgrade(upgrade.id) }
        }
    }
}

@Composable
fun UpgradeCard(upgrade: com.example.fishingidlegame.config.UpgradeConfig, level: Int, cost: Int, canAfford: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.width(150.dp).clickable(enabled = cost != -1) { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (cost == -1) Color(0xFF1E272E) else if (canAfford) Color(0xFF1B2631) else Color(0xFF0D1218)
        ),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, 
            if (cost == -1) Color(0xFF2ECC71).copy(alpha = 0.5f) 
            else if (canAfford) Color(0xFFf9c74f).copy(alpha = 0.4f) 
            else Color.White.copy(alpha = 0.05f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(upgrade.icon, fontSize = 32.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(upgrade.name.uppercase(), fontSize = 10.sp, color = Color.White.copy(alpha = 0.4f), fontWeight = FontWeight.Black)
            Text(
                text = if (level < upgrade.values.size) "${upgrade.values[level]}${upgrade.unit}" else "MAX",
                fontSize = 18.sp, 
                color = Color.White, 
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                color = if (cost == -1) Color(0xFF2ECC71) else if (canAfford) Color(0xFFf9c74f) else Color.White.copy(alpha = 0.1f),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (cost == -1) "MAXED" else "$$cost",
                    color = if (canAfford || cost == -1) Color(0xFF0A1628) else Color.White.copy(alpha = 0.3f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 6.dp),
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
