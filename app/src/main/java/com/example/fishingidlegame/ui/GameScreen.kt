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
    val currentBiome = GameConfig.biomes[state.currentBiomeIndex]

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0A1628))) {
        // 1. Mundo (Canvas Dinámico)
        GameCanvas(state, fishList, currentBiome, onLaunch = { viewModel.launchHook(240f) })

        // 2. HUD Superior
        GameHUD(state, currentBiome)

        // 3. Overlay Inicial
        AnimatedVisibility(
            visible = state.gamePhase == "MENU",
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            GameOverlay(currentBiome, onLaunch = { viewModel.launchHook(240f) })
        }

        // 4. Barra de Carga
        if (state.gamePhase != "MENU") {
            WeightBar(
                currentKg = state.currentKg,
                maxKg = GameConfig.upgrades["weight"]?.values?.get(state.upgLevels["weight"] ?: 0) ?: 20f,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 140.dp)
            )
        }

        // 5. Toasts
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            state.toastMessage?.let { msg ->
                GameToast(msg)
            }
        }

        // 6. Botón de Prestigio (Solo si tiene suficientes puntos acumulados)
        if (state.gamePhase == "MENU" && state.totalLifetimeScore > 5000) {
            PrestigeButton(
                multiplier = 1.0f + (state.totalLifetimeScore / 5000f),
                onClick = { viewModel.resetForPrestige() },
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 100.dp, end = 16.dp)
            )
        }

        // 7. Menú de Mejoras
        if (state.gamePhase == "MENU") {
            Column(modifier = Modifier.align(Alignment.BottomCenter)) {
                UpgradeMenu(state, onUpgrade = { viewModel.buyUpgrade(it) })
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun GameCanvas(
    state: com.example.fishingidlegame.model.GameState, 
    fishList: List<Fish>, 
    biome: com.example.fishingidlegame.model.Biome,
    onLaunch: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition()
    val waveOffset by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 400f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing))
    )

    Canvas(modifier = Modifier.fillMaxSize().clickable { if (state.gamePhase == "MENU") onLaunch() }) {
        val scale = size.width / 480f
        val surfaceY = 400f * scale
        val camOffset = state.camY * scale

        // Dibujar Cielo del Bioma
        drawRect(
            color = biome.skyColor,
            size = Size(size.width, surfaceY - camOffset)
        )

        // Dibujar Agua del Bioma (Degradado)
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(biome.waterColorTop, biome.waterColorBottom),
                startY = (surfaceY - camOffset).coerceAtLeast(0f)
            ),
            topLeft = Offset(0f, (surfaceY - camOffset).coerceAtLeast(0f)),
            size = Size(size.width, size.height)
        )

        // Ondas
        if (surfaceY - camOffset > -50) {
            for (i in 0..8) {
                val ox = (i * 120 * scale + waveOffset * scale) % size.width
                drawCircle(Color.White.copy(alpha = 0.15f), radius = 15f * scale, center = Offset(ox, surfaceY - camOffset))
            }
        }

        // Peces
        fishList.forEach { fish ->
            val fx = fish.x * scale
            val fy = (fish.y * scale) - camOffset
            if (fy > -100 && fy < size.height + 100) {
                rotate(degrees = if (fish.vx > 0) 0f else 180f, pivot = Offset(fx, fy)) {
                    drawOval(fish.type.color, topLeft = Offset(fx - 15*scale, fy - 10*scale), size = Size(30*scale, 20*scale))
                    drawPath(Path().apply {
                        moveTo(fx - 15*scale, fy)
                        lineTo(fx - 25*scale, fy - 8*scale)
                        lineTo(fx - 25*scale, fy + 8*scale)
                        close()
                    }, fish.type.color)
                    drawCircle(Color.White, radius = 2f*scale, center = Offset(fx + 8*scale, fy - 3*scale))
                }
            }
        }

        // Barco
        val boatX = size.width / 2
        val boatY = surfaceY - camOffset
        if (boatY > -200) {
            drawBoat(boatX, boatY, scale)
        }

        // Sedal y Anzuelo
        if (state.gamePhase != "MENU") {
            val hX = state.hookX * scale
            val hY = (state.hookY * scale) - camOffset
            drawLine(Color.White.copy(alpha = 0.6f), Offset(boatX + 40 * scale, boatY - 60 * scale), Offset(hX, hY), strokeWidth = 1.5f * scale)
            drawCircle(Color(0xFFf9c74f), radius = 6f * scale, center = Offset(hX, hY))
        }
    }
}

@Composable
fun PrestigeButton(multiplier: Float, onClick: () -> Unit, modifier: Modifier) {
    Surface(
        onClick = onClick,
        color = Color(0xFF6200EE),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.shadow(8.dp, RoundedCornerShape(12.dp))
    ) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("PRESTIGIO", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text("x${"%.2f".format(multiplier)}", color = Color(0xFFf9c74f), fontSize = 18.sp, fontWeight = FontWeight.Black)
            Text("REINICIAR", color = Color.White.copy(alpha = 0.6f), fontSize = 8.sp)
        }
    }
}

// ... Resto de componentes (GameHUD, WeightBar, etc.) actualizados para usar los nuevos campos ...
// (Nota: Por brevedad no repito todo el archivo, pero incluyo los cambios clave)

@Composable
fun GameHUD(state: com.example.fishingidlegame.model.GameState, biome: com.example.fishingidlegame.model.Biome) {
    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Column {
            Text(biome.name.uppercase(), color = Color(0xFFf9c74f), fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            Text("${state.score} pts", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
            if (state.prestigeMultiplier > 1f) {
                Text("Bono: x${"%.2f".format(state.prestigeMultiplier)}", color = Color(0xFF64DC82), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        Pill(text = "🐟 ${state.totalFishCaught}")
    }
}

@Composable
fun GameOverlay(biome: com.example.fishingidlegame.model.Biome, onLaunch: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("FISHING", fontSize = 56.sp, fontWeight = FontWeight.Black, color = Color.White)
        Text(biome.name, fontSize = 18.sp, color = Color(0xFFf9c74f), fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(40.dp))
        Button(
            onClick = onLaunch,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFf9c74f)),
            modifier = Modifier.height(56.dp).width(200.dp)
        ) {
            Text("LANZAR", color = Color.Black, fontWeight = FontWeight.Black)
        }
    }
}

// Reutilizamos Pill, WeightBar, UpgradeMenu, UpgradeCard del archivo anterior con sus lógicas
@Composable
fun Pill(text: String) {
    Surface(color = Color(0xAA0A1628), shape = RoundedCornerShape(999.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF))) {
        Text(text = text, color = Color.White, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp), fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
fun GameToast(message: String) {
    Surface(color = Color(0xEE0A1628), shape = RoundedCornerShape(999.dp), border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFf9c74f)), modifier = Modifier.padding(top = 100.dp)) {
        Text(text = message, color = Color.White, modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp), fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

@Composable
fun WeightBar(currentKg: Float, maxKg: Float, modifier: Modifier) {
    val progress = (currentKg / maxKg).coerceIn(0f, 1f)
    Column(modifier = modifier.width(260.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("CARGA", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text("${"%.1f".format(currentKg)} / ${maxKg.toInt()} kg", color = Color(0xFFf9c74f), fontSize = 12.sp, fontWeight = FontWeight.Black)
        }
        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(99.dp)),
            color = if (progress >= 1f) Color.Red else Color(0xFF29b6f6),
            trackColor = Color.White.copy(alpha = 0.1f)
        )
    }
}

@Composable
fun UpgradeMenu(state: com.example.fishingidlegame.model.GameState, onUpgrade: (String) -> Unit) {
    LazyRow(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
        modifier = Modifier.width(135.dp).clickable(enabled = cost != -1) { onClick() },
        colors = CardDefaults.cardColors(containerColor = if (canAfford) Color(0xFF1A2B3C) else Color(0xFF0D1621)),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, if (cost == -1) Color(0xFF64DC82) else if (canAfford) Color(0xFFf9c74f) else Color(0x22FFFFFF))
    ) {
        Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(upgrade.icon, fontSize = 28.sp)
            Text(upgrade.name, fontSize = 10.sp, color = Color.White.copy(alpha = 0.6f), fontWeight = FontWeight.Bold)
            Text("${upgrade.values[level]}${upgrade.unit}", fontSize = 13.sp, color = Color(0xFFf9c74f), fontWeight = FontWeight.ExtraBold)
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                color = if (cost == -1) Color(0x3364DC82) else if (canAfford) Color(0xFFf9c74f) else Color(0x11FFFFFF),
                shape = RoundedCornerShape(999.dp)
            ) {
                Text(
                    text = if (cost == -1) "MÁXIMO" else "$cost pts",
                    color = if (canAfford || cost == -1) Color(0xFF0A1628) else Color.White.copy(alpha = 0.3f),
                    fontSize = 10.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

fun DrawScope.drawBoat(x: Float, y: Float, scale: Float) {
    val bW = 140f * scale
    val bH = 40f * scale
    val path = Path().apply {
        moveTo(x - bW/2, y - 10*scale)
        lineTo(x + bW/2, y - 10*scale)
        lineTo(x + bW/2 - 15*scale, y + bH/2)
        lineTo(x - bW/2 + 15*scale, y + bH/2)
        close()
    }
    drawPath(path, Color(0xFFc0392b))
    drawRect(Color(0xFFe8d5a0), topLeft = Offset(x - 25*scale, y - 45*scale), size = Size(45*scale, 35*scale))
    drawLine(Color(0xFF6b3f1a), Offset(x + 10*scale, y - 10*scale), Offset(x + 10*scale, y - 80*scale), strokeWidth = 3f * scale)
}
