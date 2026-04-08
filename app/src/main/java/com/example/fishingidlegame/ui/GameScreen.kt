package com.example.fishingidlegame.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.input.pointer.pointerInput
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
    val currentBiome = GameConfig.biomes[state.currentBiomeIndex]

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        if (state.gamePhase == "BOSS_FIGHT") {
                            viewModel.onPlayerTap()
                        }
                    }
                )
            }
    ) {
        // 1. Mundo (Canvas)
        GameCanvas(state, fishList, currentBiome, onLaunch = { viewModel.launchHook(240f) })

        // 2. HUD Superior
        GameHUD(state, currentBiome)

        // 3. UI DE BOSS (Tensión por toques)
        if (state.gamePhase == "BOSS_FIGHT") {
            BossFightTapUI(state)
        }

        // 4. Overlay de Menú
        if (state.gamePhase == "MENU") {
            Column(modifier = Modifier.align(Alignment.BottomCenter)) {
                UpgradeMenu(state, onUpgrade = { viewModel.buyUpgrade(it) })
                Spacer(modifier = Modifier.height(24.dp))
            }
            GameOverlay(currentBiome, onLaunch = { viewModel.launchHook(240f) })
        }

        // 5. Toasts
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            state.toastMessage?.let { GameToast(it) }
        }
    }
}

@Composable
fun BossFightTapUI(state: com.example.fishingidlegame.model.GameState) {
    val biome = GameConfig.biomes[state.currentBiomeIndex]
    
    // Animación de vibración si la tensión es peligrosa
    val infiniteTransition = rememberInfiniteTransition()
    val shake by infiniteTransition.animateFloat(
        initialValue = -2f, targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(50), RepeatMode.Reverse)
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(top = 100.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("¡BOSS: ${biome.bossName.uppercase()}!", color = Color.White, fontWeight = FontWeight.Black, fontSize = 24.sp)
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Vida del Boss
        Text("STAMINA DEL JEFE", color = Color.White.copy(0.6f), fontSize = 10.sp)
        LinearProgressIndicator(
            progress = state.bossHealth / state.bossMaxHealth,
            modifier = Modifier.width(300.dp).height(12.dp).clip(RoundedCornerShape(6.dp)),
            color = Color(0xFFE74C3C),
            trackColor = Color.Black.copy(0.4f)
        )

        Spacer(modifier = Modifier.height(40.dp))

        // BARRA DE TENSIÓN DINÁMICA
        Text("TENSIÓN", color = Color.White, fontWeight = FontWeight.Bold)
        Box(
            modifier = Modifier
                .width(320.dp)
                .height(40.dp)
                .offset(x = if (state.bossActive) shake.dp else 0.dp) // Vibra si hay boss
                .background(Color.Black.copy(0.5f), RoundedCornerShape(8.dp))
        ) {
            // Sweet Spot (Zona Verde)
            Box(modifier = Modifier.fillMaxHeight().width(90.dp).align(Alignment.Center).background(Color(0xFF2ECC71).copy(0.4f)))
            
            // Aguja de tensión (Simulada, en una app real usaríamos el valor de tensionValue del VM)
            Box(modifier = Modifier.fillMaxHeight().width(4.dp).align(Alignment.Center).background(Color.White))
        }
        
        Text("¡DA TOQUES RÁPIDOS PARA TIRAR!", color = Color(0xFFf9c74f), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 20.dp))
    }
}

// Reutilizamos el Canvas y HUD anteriores...
@Composable
fun GameCanvas(state: com.example.fishingidlegame.model.GameState, fishList: List<Fish>, biome: com.example.fishingidlegame.model.Biome, onLaunch: () -> Unit) {
    Canvas(modifier = Modifier.fillMaxSize().clickable { if (state.gamePhase == "MENU") onLaunch() }) {
        val scale = size.width / 480f
        val surfaceY = 400f * scale
        val camOffset = state.camY * scale

        // Fondo Bioma
        drawRect(color = biome.skyColor, size = Size(size.width, surfaceY - camOffset))
        drawRect(
            brush = Brush.verticalGradient(listOf(biome.waterColorTop, biome.waterColorBottom), startY = (surfaceY - camOffset).coerceAtLeast(0f)),
            topLeft = Offset(0f, (surfaceY - camOffset).coerceAtLeast(0f)),
            size = Size(size.width, size.height)
        )

        // El Boss Gigante (Representación visual)
        if (state.gamePhase == "BOSS_FIGHT") {
            val bx = size.width / 2
            val by = (state.hookY * scale) - camOffset
            drawCircle(Color.Black.copy(0.6f), radius = 120f * scale, center = Offset(bx, by))
            drawCircle(Color(0xFF1A1A1A), radius = 100f * scale, center = Offset(bx, by))
            // Ojo brillante del Boss
            drawCircle(Color.Red, radius = 10f * scale, center = Offset(bx + 40*scale, by - 20*scale))
        }

        // Peces y Sedal
        fishList.forEach { fish ->
            val fx = fish.x * scale
            val fy = (fish.y * scale) - camOffset
            if (!fish.isCaught && fy > surfaceY - camOffset) {
                drawCircle(fish.type.color, radius = 10f * scale, center = Offset(fx, fy))
            }
        }

        if (state.gamePhase != "MENU") {
            val hX = state.hookX * scale
            val hY = (state.hookY * scale) - camOffset
            drawLine(Color.White, Offset(size.width/2 + 40*scale, surfaceY - camOffset - 60*scale), Offset(hX, hY), strokeWidth = 2f)
            drawCircle(Color(0xFFf9c74f), radius = 6f * scale, center = Offset(hX, hY))
        }
    }
}

@Composable
fun GameHUD(state: com.example.fishingidlegame.model.GameState, biome: com.example.fishingidlegame.model.Biome) {
    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Column {
            Text(biome.name.uppercase(), color = Color(0xFFf9c74f), fontSize = 11.sp, fontWeight = FontWeight.Black)
            Text("${state.score} pts", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
        }
        Surface(color = Color(0xAA0A1628), shape = RoundedCornerShape(999.dp)) {
            Text("🐟 ${state.totalFishCaught}", color = Color.White, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun GameOverlay(biome: com.example.fishingidlegame.model.Biome, onLaunch: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("FISHING", fontSize = 56.sp, fontWeight = FontWeight.Black, color = Color.White)
            Text("BIOMA: ${biome.name}", color = Color(0xFFf9c74f), fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(40.dp))
            Button(onClick = onLaunch, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFf9c74f))) {
                Text("LANZAR", color = Color.Black, fontWeight = FontWeight.Black)
            }
        }
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
        colors = CardDefaults.cardColors(containerColor = if (canAfford) Color(0xFF1A2B3C) else Color(0xFF0D1621))
    ) {
        Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(upgrade.icon, fontSize = 28.sp)
            Text(upgrade.name, fontSize = 10.sp, color = Color.White.copy(0.6f), fontWeight = FontWeight.Bold)
            Text("${upgrade.values[level]}${upgrade.unit}", fontSize = 13.sp, color = Color(0xFFf9c74f), fontWeight = FontWeight.ExtraBold)
            Spacer(modifier = Modifier.height(6.dp))
            Text(if (cost == -1) "MAX" else "$cost", color = if (canAfford) Color.White else Color.Gray, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
fun GameToast(msg: String) {
    Surface(color = Color(0xEE0A1628), shape = RoundedCornerShape(999.dp), border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFf9c74f)), modifier = Modifier.padding(top = 100.dp)) {
        Text(text = msg, color = Color.White, modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp), fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}
