package com.example.fishingidlegame.ui

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fishingidlegame.config.GameConfig
import com.example.fishingidlegame.model.Fish
import com.example.fishingidlegame.viewmodel.FishingViewModel

@Composable
fun GameScreen(viewModel: FishingViewModel) {
    val state by viewModel.state.collectAsState()
    val fishList by viewModel.fishList.collectAsState()

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // 1. Mundo (Canvas)
        GameCanvas(state, fishList, onLaunch = { viewModel.launchHook(240f) })

        // 2. HUD Superior
        GameHUD(state)

        // 3. Overlay Inicial (Pantalla de "Toca para lanzar")
        AnimatedVisibility(
            visible = state.gamePhase == "MENU",
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            GameOverlay(onLaunch = { viewModel.launchHook(240f) })
        }

        // 4. Barra de Peso
        if (state.gamePhase != "MENU") {
            WeightBar(
                currentKg = state.currentKg,
                maxKg = GameConfig.upgrades["weight"]?.values?.get(state.upgLevels["weight"] ?: 0) ?: 20f,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 120.dp)
            )
        }

        // 5. Toasts (Mensajes flotantes)
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            state.toastMessage?.let { msg ->
                GameToast(msg)
            }
        }

        // 6. Menú de Upgrades
        if (state.gamePhase == "MENU") {
            Column(modifier = Modifier.align(Alignment.BottomCenter)) {
                UpgradeMenu(state, onUpgrade = { viewModel.buyUpgrade(it) })
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun GameOverlay(onLaunch: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(32.dp)
    ) {
        Text(
            "🎣 Fishing",
            fontSize = 56.sp,
            fontWeight = FontWeight.Black,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Text(
            "Prototipo — Fase 1",
            fontSize = 16.sp,
            color = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 8.dp)
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onLaunch,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFf9c74f)),
            shape = RoundedCornerShape(999.dp),
            modifier = Modifier.height(56.dp).padding(horizontal = 24.dp)
        ) {
            Text("TOCA PARA LANZAR", color = Color(0xFF0A1628), fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
fun GameToast(message: String) {
    Surface(
        color = Color(0xEE0A1628),
        shape = RoundedCornerShape(999.dp),
        border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFf9c74f)),
        modifier = Modifier.padding(bottom = 80.dp)
    ) {
        Text(
            text = message,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
    }
}

@Composable
fun GameCanvas(state: com.example.fishingidlegame.model.GameState, fishList: List<Fish>, onLaunch: () -> Unit) {
    Canvas(modifier = Modifier.fillMaxSize().clickable { if (state.gamePhase == "MENU") onLaunch() }) {
        val scale = size.width / 480f
        val surfaceY = 400f * scale
        val camOffset = state.camY * scale

        // Dibujar Cielo
        drawRect(
            brush = Brush.verticalGradient(listOf(Color(0xFF1e5fa8), Color(0xFF9fd4ef))),
            size = Size(size.width, surfaceY - camOffset)
        )

        // Dibujar Agua
        drawRect(
            brush = Brush.verticalGradient(listOf(Color(0xFF1898a8), Color(0xFF061220))),
            topLeft = Offset(0f, (surfaceY - camOffset).coerceAtLeast(0f)),
            size = Size(size.width, size.height)
        )

        // Dibujar Peces
        fishList.forEach { fish ->
            val fx = fish.x * scale
            val fy = (fish.y * scale) - camOffset
            if (fy > surfaceY - camOffset - 50) {
                drawCircle(fish.type.color, radius = fish.type.width * scale / 2, center = Offset(fx, fy))
            }
        }

        // Dibujar Barco
        val boatX = size.width / 2
        val boatY = surfaceY - camOffset
        if (boatY > -200) {
            drawBoat(boatX, boatY, scale)
        }

        // Dibujar Línea y Anzuelo
        if (state.gamePhase != "MENU") {
            val hX = state.hookX * scale
            val hY = (state.hookY * scale) - camOffset
            drawLine(Color.White.copy(alpha = 0.8f), Offset(boatX + 40 * scale, boatY - 60 * scale), Offset(hX, hY), strokeWidth = 1.5f * scale)
            drawCircle(Color(0xFFf9c74f), radius = 6f * scale, center = Offset(hX, hY))
        }
    }
}

fun DrawScope.drawBoat(x: Float, y: Float, scale: Float) {
    val bW = 140f * scale
    val bH = 40f * scale
    
    // Casco
    val path = Path().apply {
        moveTo(x - bW/2, y - 10*scale)
        lineTo(x + bW/2, y - 10*scale)
        lineTo(x + bW/2 - 15*scale, y + bH/2)
        lineTo(x - bW/2 + 15*scale, y + bH/2)
        close()
    }
    drawPath(path, Color(0xFFc0392b))
    
    // Cabina
    drawRect(Color(0xFFe8d5a0), topLeft = Offset(x - 25*scale, y - 45*scale), size = Size(45*scale, 35*scale))
    
    // Mástil
    drawLine(Color(0xFF6b3f1a), Offset(x + 10*scale, y - 10*scale), Offset(x + 10*scale, y - 80*scale), strokeWidth = 3f * scale)
}

@Composable
fun WeightBar(currentKg: Float, maxKg: Float, modifier: Modifier) {
    val progress = (currentKg / maxKg).coerceIn(0f, 1f)
    Column(modifier = modifier.width(260.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("CARGA", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, fontWeight = FontWeight.Black)
            Text("${"%.1f".format(currentKg)} / ${maxKg.toInt()} kg", color = Color(0xFFf9c74f), fontSize = 13.sp, fontWeight = FontWeight.Black)
        }
        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(99.dp)),
            color = if (progress >= 1f) Color(0xFFe63946) else Color(0xFF29b6f6),
            trackColor = Color.White.copy(alpha = 0.1f)
        )
    }
}

@Composable
fun GameHUD(state: com.example.fishingidlegame.model.GameState) {
    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Pill(text = "🎣 ${state.score} pts")
        Pill(text = "🐟 ${state.totalFishCaught} peces")
    }
}

@Composable
fun Pill(text: String) {
    Surface(color = Color(0xAA0A1628), shape = RoundedCornerShape(999.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF))) {
        Text(text = text, color = Color.White, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp), fontWeight = FontWeight.Bold, fontSize = 14.sp)
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
