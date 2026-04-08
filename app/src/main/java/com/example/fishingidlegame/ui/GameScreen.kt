package com.example.fishingidlegame.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
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
                detectTapGestures(onTap = { if (state.gamePhase == "BOSS_FIGHT") viewModel.onPlayerTap() })
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    viewModel.setHookTarget(change.position.x)
                }
            }
    ) {
        // 1. Fondo y Mundo
        GameCanvas(state, fishList, currentBiome)

        // 2. HUD Superior (Botones y Stats)
        GameTopHUD(state, viewModel)

        // 3. UI De Pesca Activa (Botón de Recoger)
        if (state.gamePhase == "FISHING") {
            Button(
                onClick = { viewModel.forceReel() },
                modifier = Modifier.align(Alignment.BottomEnd).padding(32.dp).size(80.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE67E22))
            ) {
                Icon(Icons.Default.VerticalAlignTop, contentDescription = "Recoger")
            }
        }

        // 4. Boss Fight
        if (state.gamePhase == "BOSS_FIGHT") {
            BossFightUI(state)
        }

        // 5. Menú e Upgrades
        if (state.gamePhase == "MENU") {
            UpgradeMenuOverlay(state, viewModel)
        }

        // 6. Overlays (Colección y Mapas)
        if (state.showCollection) CollectionOverlay(state, onClose = { viewModel.toggleCollection(false) })
        if (state.showMapSelector) MapSelectorOverlay(state, onSelect = { viewModel.changeBiome(it) }, onClose = { viewModel.toggleMapSelector(false) })

        // 7. Toasts
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            state.toastMessage?.let { GameToast(it) }
        }
    }
}

@Composable
fun GameTopHUD(state: com.example.fishingidlegame.model.GameState, viewModel: FishingViewModel) {
    val currentBiome = GameConfig.biomes[state.currentBiomeIndex]
    
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column {
            Text(currentBiome.name.uppercase(), color = Color(0xFFf9c74f), fontSize = 12.sp, fontWeight = FontWeight.Black)
            Text("${state.score} pts", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
        }
        
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Botón Mapas
            IconButton(onClick = { viewModel.toggleMapSelector(true) }, modifier = Modifier.background(Color.Black.copy(0.4f), CircleShape)) {
                Icon(Icons.Default.Map, contentDescription = "Mapas", tint = Color.White)
            }
            // Botón Colección
            IconButton(onClick = { viewModel.toggleCollection(true) }, modifier = Modifier.background(Color.Black.copy(0.4f), CircleShape)) {
                Icon(Icons.Default.List, contentDescription = "Colección", tint = Color.White)
            }
        }
    }
}

@Composable
fun BossFightUI(state: com.example.fishingidlegame.model.GameState) {
    Column(modifier = Modifier.fillMaxSize().padding(top = 100.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("BOSS FIGHT", color = Color.Red, fontWeight = FontWeight.Black, fontSize = 32.sp)
        Spacer(modifier = Modifier.height(8.dp))
        
        // Vida del Boss
        LinearProgressIndicator(
            progress = state.bossHealth / state.bossMaxHealth,
            modifier = Modifier.width(300.dp).height(12.dp).clip(RoundedCornerShape(6.dp)),
            color = Color.Red,
            trackColor = Color.Black.copy(0.5f)
        )
        
        Spacer(modifier = Modifier.height(40.dp))
        
        // Barra de Tensión
        Box(modifier = Modifier.width(300.dp).height(40.dp).background(Color.Black.copy(0.6f), RoundedCornerShape(20.dp))) {
            // Sweet Spot
            val isFirst = state.currentBiomeIndex == 0
            val start = if(isFirst) 0.3f else 0.4f
            val end = if(isFirst) 0.9f else 0.8f
            
            Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(end - start).offset(x = (300 * start).dp).background(Color.Green.copy(0.3f)))
            
            // Aguja
            Box(modifier = Modifier.fillMaxHeight().width(4.dp).offset(x = (300 * (state.bossTension / 100f)).dp).background(Color.White))
        }
        Text("¡TOCA PARA MANTENER LA BARRA EN VERDE!", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
    }
}

@Composable
fun CollectionOverlay(state: com.example.fishingidlegame.model.GameState, onClose: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.8f)).clickable { onClose() }, contentAlignment = Alignment.Center) {
        Card(modifier = Modifier.fillMaxWidth(0.85f).fillMaxHeight(0.7f).clickable(enabled = false) {}, colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2B3C))) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("COLECCIÓN DE PECES", color = Color(0xFFf9c74f), fontWeight = FontWeight.Black, fontSize = 20.sp)
                Spacer(modifier = Modifier.height(16.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(GameConfig.fishTypes.values.toList()) { type ->
                        val isCaught = state.caughtSpecies.contains(type.name)
                        Row(modifier = Modifier.fillMaxWidth().background(Color.Black.copy(0.3f), RoundedCornerShape(8.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(40.dp).background(if (isCaught) type.color else Color.DarkGray, CircleShape))
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(if (isCaught) type.name else "???", color = Color.White, fontWeight = FontWeight.Bold)
                                if (isCaught) Text("Capturados: ${state.speciesCounts[type.name] ?: 0}", color = Color.Gray, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MapSelectorOverlay(state: com.example.fishingidlegame.model.GameState, onSelect: (Int) -> Unit, onClose: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.8f)).clickable { onClose() }, contentAlignment = Alignment.Center) {
        Card(modifier = Modifier.fillMaxWidth(0.85f).clickable(enabled = false) {}, colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2B3C))) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("VIAJAR A...", color = Color(0xFFf9c74f), fontWeight = FontWeight.Black, fontSize = 20.sp)
                Spacer(modifier = Modifier.height(16.dp))
                GameConfig.biomes.forEachIndexed { index, biome ->
                    val isUnlocked = index <= state.maxBiomeReached
                    Button(
                        onClick = { if (isUnlocked) onSelect(index) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = if (isUnlocked) Color(0xFF2E4053) else Color.DarkGray),
                        enabled = isUnlocked
                    ) {
                        Text(biome.name, color = if (isUnlocked) Color.White else Color.Gray)
                        if (!isUnlocked) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UpgradeMenuOverlay(state: com.example.fishingidlegame.model.GameState, viewModel: FishingViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth().clickable { viewModel.launchHook(540f) }, contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("LANZAR", fontSize = 48.sp, fontWeight = FontWeight.Black, color = Color.White)
                Icon(Icons.Default.TouchApp, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
            }
        }
        UpgradeMenu(state, onUpgrade = { viewModel.buyUpgrade(it) })
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun GameCanvas(state: com.example.fishingidlegame.model.GameState, fishList: List<Fish>, biome: com.example.fishingidlegame.model.Biome) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val scale = size.width / 1080f
        val surfaceY = 400f * scale
        val camOffset = state.camY * scale

        // Fondo Bioma
        drawRect(color = biome.skyColor, size = Size(size.width, surfaceY - camOffset))
        drawRect(
            brush = Brush.verticalGradient(listOf(biome.waterColorTop, biome.waterColorBottom), startY = (surfaceY - camOffset).coerceAtLeast(0f)),
            topLeft = Offset(0f, (surfaceY - camOffset).coerceAtLeast(0f)),
            size = Size(size.width, size.height)
        )

        // Boss
        if (state.gamePhase == "BOSS_FIGHT") {
            drawCircle(Color.Black.copy(0.4f), radius = 250f * scale, center = Offset(size.width/2, (state.hookY * scale) - camOffset))
        }

        // Peces
        fishList.forEach { fish ->
            val fx = fish.x * scale
            val fy = (fish.y * scale) - camOffset
            if (!fish.isCaught && fy > surfaceY - camOffset) {
                drawCircle(fish.type.color, radius = 20f * scale, center = Offset(fx, fy))
            }
        }

        // Sedal y Anzuelo
        if (state.gamePhase != "MENU") {
            val hX = state.hookX * scale
            val hY = (state.hookY * scale) - camOffset
            drawLine(Color.White.copy(0.7f), Offset(size.width/2, (surfaceY - camOffset).coerceAtLeast(0f)), Offset(hX, hY), strokeWidth = 3f)
            drawCircle(Color.White, radius = 10f * scale, center = Offset(hX, hY))
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
        colors = CardDefaults.cardColors(containerColor = if (canAfford) Color(0xFF1A2B3C) else Color(0xFF0D1621)),
        border = if (canAfford) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFf9c74f)) else null
    ) {
        Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(upgrade.icon, fontSize = 28.sp)
            Text(upgrade.name, fontSize = 10.sp, color = Color.White.copy(0.6f), fontWeight = FontWeight.Bold)
            val valueText = if(level < upgrade.values.size) "${upgrade.values[level]}${upgrade.unit}" else "MAX"
            Text(valueText, fontSize = 13.sp, color = Color(0xFFf9c74f), fontWeight = FontWeight.ExtraBold)
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
