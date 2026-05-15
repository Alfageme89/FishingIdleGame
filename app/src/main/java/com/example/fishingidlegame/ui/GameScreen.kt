package com.example.fishingidlegame.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fishingidlegame.config.*
import com.example.fishingidlegame.model.*
import com.example.fishingidlegame.viewmodel.FishingViewModel
import com.example.fishingidlegame.viewmodel.formatPoints
import androidx.compose.ui.res.painterResource
import com.example.fishingidlegame.R
import kotlin.math.roundToInt

@Composable
fun GameScreen(viewModel: FishingViewModel, onLogout: () -> Unit = {}) {
    val state by viewModel.state.collectAsState()
    val fishList by viewModel.fishList.collectAsState()
    val powerUps by viewModel.powerUps.collectAsState()
    val currentBiome = GameConfig.biomes[state.currentBiomeIndex]

    var selectedFishForDetail by remember { mutableStateOf<FishType?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0E14)).then(
        if (state.sfxEnabled) Modifier
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(color = Color.White, blendMode = androidx.compose.ui.graphics.BlendMode.Difference)
            }
        else Modifier
    )) {
        // 1. Capa de Juego (Mundo)
        Box(
            modifier = Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { if (state.gamePhase == "BOSS_FIGHT") viewModel.onPlayerTap() })
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ -> viewModel.setHookTarget(change.position.x) }
                }
        ) {
            GameCanvas(state, fishList, powerUps, currentBiome)
        }

        // 2. HUD de Juego Activo
        if (state.gamePhase == "FISHING" || state.gamePhase == "REELING") {
            DepthMeter(state)
            PowerUpIndicatorList(state.activePowerUps)
            
            Box(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                // Botón Turbo
                Surface(
                    modifier = Modifier.align(Alignment.BottomStart).size(85.dp),
                    shape = CircleShape,
                    color = if (state.isTurbo) Color.Cyan.copy(0.4f) else Color.White.copy(0.1f),
                    border = BorderStroke(2.dp, if (state.isTurbo) Color.Cyan else Color.White)
                ) {
                    Box(modifier = Modifier.pointerInput(Unit) {
                        detectTapGestures(onPress = {
                            viewModel.setTurbo(true)
                            try { awaitRelease() } finally { viewModel.setTurbo(false) }
                        })
                    }, contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.RocketLaunch, null, tint = Color.White)
                            Text("TURBO", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }

                // Indicador de peso centrado en la parte inferior
                val maxKg = GameConfig.upgrades["weight"]?.values?.get(state.upgLevels["weight"] ?: 0) ?: 5f
                val kgProgress = (state.currentKg / maxKg).coerceIn(0f, 1f)
                Column(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${String.format("%.1f", state.currentKg)} / ${maxKg.toInt()} kg",
                        color = if (kgProgress >= 1f) Color.Red else Color.White.copy(0.85f),
                        fontSize = 11.sp, fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    LinearProgressIndicator(
                        progress = kgProgress,
                        modifier = Modifier.width(90.dp).height(5.dp).clip(RoundedCornerShape(3.dp)),
                        color = if (kgProgress >= 1f) Color.Red else Color.Cyan,
                        trackColor = Color.White.copy(0.15f)
                    )
                }

                if (state.gamePhase == "FISHING") {
                    FloatingActionButton(
                        onClick = { viewModel.forceReel() },
                        modifier = Modifier.align(Alignment.BottomEnd).size(64.dp),
                        containerColor = Color(0xFFE67E22),
                        shape = CircleShape
                    ) { Icon(Icons.Default.VerticalAlignTop, "Subir", tint = Color.White) }
                }
            }
        }

        // 3. UI de Menú Principal
        if (state.gamePhase == "MENU") MainMenuUI(state, viewModel)

        // 4. Fases Especiales del Boss
        if (state.gamePhase == "BOSS_WARNING") BossWarningOverlay(state, state.currentBiomeIndex)
        if (state.gamePhase == "BOSS_FIGHT") BossFightUI(state, state.currentBiomeIndex)
        if (state.gamePhase == "BOSS_FAIL") BossFailOverlay(state.currentBiomeIndex) { viewModel.dismissBossFail() }

        // 5. Ventanas Modales
        if (state.showCollection) CollectionOverlay(state, viewModel, onFishClick = { selectedFishForDetail = it }, onClose = { viewModel.toggleCollection(false) })
        if (state.showMapSelector) MapSelectorOverlay(state, onSelect = { viewModel.changeBiome(it) }, onClose = { viewModel.toggleMapSelector(false) })
        if (state.showPrestigeConfirm) PrestigeConfirmOverlay(state, viewModel, onConfirm = { viewModel.confirmPrestige() }, onCancel = { viewModel.closePrestigeConfirm() })
        if (state.showShop) ShopOverlay(state, onClose = { viewModel.toggleShop(false) }, onBuy = { type: PowerUpType, cost: Long -> viewModel.buyConsumable(type, cost) })
        if (state.showSettings) SettingsOverlay(state, viewModel, onLogout = onLogout)
        if (state.showResetConfirm) ResetConfirmOverlay(onConfirm = { viewModel.confirmReset() }, onCancel = { viewModel.cancelReset() })
        
        selectedFishForDetail?.let { FishDetailOverlay(it, state) { selectedFishForDetail = null } }

        // 6. HUD Superior Global
        GameTopHUD(state, viewModel)

        // 7. Notificaciones
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            state.toastMessage?.let { GameToast(it) }
        }
    }
}

@Composable
fun MainMenuUI(state: GameState, viewModel: FishingViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth().clickable { viewModel.launchHook(540f) }, contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("LANZAR", fontSize = 56.sp, fontWeight = FontWeight.Black, color = Color.White)
                Icon(Icons.Default.TouchApp, null, tint = Color.White.copy(0.6f), modifier = Modifier.size(48.dp))
            }
        }

        // Botonera Estilo Gacha
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            GachaButton("Diario", Icons.Default.MenuBook) { viewModel.toggleCollection(true) }
            GachaButton("Mapas", Icons.Default.Map) { viewModel.toggleMapSelector(true) }
            GachaButton("Tienda", Icons.Default.ShoppingCart) { viewModel.toggleShop(true) }
            GachaButton("Ajustes", Icons.Default.Settings) { viewModel.toggleSettings(true) }
        }

        // Cuadrícula de Mejoras
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(0.5f)),
            border = BorderStroke(1.dp, Color.White.copy(0.1f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("LABORATORIO DE EQUIPO", color = Color.Cyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(12.dp))
                GameConfig.upgrades.values.toList().chunked(3).forEach { row ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { upg ->
                            val level = state.upgLevels[upg.id] ?: 0
                            val cost = if(level < upg.levels.size) upg.levels[level] else -1
                            val currentValue = if (level < upg.values.size) upg.values[level] else upg.values.last()
                            Box(modifier = Modifier.weight(1f)) {
                                UpgradeCardSmall(upg, level, cost, currentValue, state.score >= cost && cost != -1) { viewModel.buyUpgrade(upg.id) }
                            }
                        }
                        if (row.size < 3) repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
fun ShopOverlay(state: GameState, onClose: () -> Unit, onBuy: (PowerUpType, Long) -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.85f)).clickable { onClose() }, contentAlignment = Alignment.Center) {
        Card(
            modifier = Modifier.fillMaxWidth(0.9f).fillMaxHeight(0.7f).clickable(enabled = false) { },
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1B2F))
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("SUMINISTROS", color = Color.White, fontWeight = FontWeight.Black, fontSize = 24.sp)
                    Text("${formatPoints(state.score)} pts", color = Color.Cyan, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(24.dp))
                
                data class ShopItem(val type: PowerUpType, val icon: String, val name: String, val desc: String, val baseCost: Long)
                val items = listOf(
                    ShopItem(PowerUpType.SPEED,  "🚀", "Propulsor Nitro",  "Velocidad x2 durante 15s (se apila con turbo)", 500L),
                    ShopItem(PowerUpType.MAGNET, "🧲", "Súper Imán",       "Radio de captura x3 durante 15s",               800L),
                    ShopItem(PowerUpType.SHIELD, "🛡️", "Escudo de Red",    "Sin límite de peso durante 15s",                1200L),
                    ShopItem(PowerUpType.GOLD,   "💰", "Anzuelo Dorado",   "Te devuelve el 25% extra de lo que pagas",      2000L)
                )

                items.forEach { item ->
                    val mult = state.shopPriceMultipliers[item.type.name] ?: 1f
                    val actualCost = (item.baseCost * mult).toLong()
                    val canAfford = state.score >= actualCost
                    val active = item.type != PowerUpType.GOLD &&
                        (state.activePowerUps[item.type] ?: 0L) > System.currentTimeMillis()
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            .clickable(enabled = canAfford && !active) { onBuy(item.type, item.baseCost) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (active) Color(0xFF0D2B0D) else Color(0xFF16213E)
                        ),
                        border = if (active) BorderStroke(1.dp, Color.Green.copy(0.6f)) else null
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(item.icon, fontSize = 32.sp)
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.name, color = if (active) Color.Green else Color.White, fontWeight = FontWeight.Bold)
                                Text(if (active) "¡ACTIVO!" else item.desc,
                                    color = if (active) Color.Green.copy(0.8f) else Color.White.copy(0.5f),
                                    fontSize = 12.sp)
                                if (mult > 1f) Text("Precio inflado x${String.format("%.1f", mult)}", color = Color(0xFFFF8C00), fontSize = 10.sp)
                            }
                            Button(
                                onClick = { onBuy(item.type, item.baseCost) },
                                enabled = canAfford && !active,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (active) Color.Gray else Color.Cyan,
                                    disabledContainerColor = Color.Gray.copy(0.4f)
                                )
                            ) {
                                Text(formatPoints(actualCost), color = Color.Black, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsOverlay(state: GameState, viewModel: FishingViewModel, onLogout: () -> Unit = {}) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.85f)).clickable { viewModel.toggleSettings(false) }, contentAlignment = Alignment.Center) {
        Card(
            modifier = Modifier.fillMaxWidth(0.85f).clickable(enabled = false) { },
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E))
        ) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("CONFIGURACIÓN", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
                Spacer(modifier = Modifier.height(32.dp))

                SettingsToggle("Modo Invertido", state.sfxEnabled) { viewModel.toggleSFX() }

                Spacer(modifier = Modifier.height(40.dp))

                TextButton(onClick = { viewModel.requestReset() }) {
                    Text("BORRAR PROGRESO", color = Color.Red.copy(0.7f), fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onLogout,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3D0000))
                ) {
                    Icon(Icons.Default.Logout, contentDescription = null, tint = Color(0xFFFF6B6B), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("CERRAR SESIÓN", color = Color(0xFFFF6B6B), fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = { viewModel.toggleSettings(false) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(0.1f))) {
                    Text("CERRAR", color = Color.White)
                }
            }
        }
    }
}

@Composable
fun SettingsToggle(label: String, enabled: Boolean, onToggle: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontWeight = FontWeight.Bold)
        Switch(checked = enabled, onCheckedChange = { onToggle() }, colors = SwitchDefaults.colors(checkedThumbColor = Color.Cyan))
    }
}

@Composable
fun ResetConfirmOverlay(onConfirm: () -> Unit, onCancel: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.9f)).clickable { onCancel() }, contentAlignment = Alignment.Center) {
        Card(
            modifier = Modifier.fillMaxWidth(0.8f).clickable(enabled = false) { },
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2B1010))
        ) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Warning, null, tint = Color.Red, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text("¿ESTÁS SEGURO?", color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Esta acción eliminará TODO tu progreso permanentemente (incluyendo prestigio y colección).", color = Color.White.copy(0.7f), textAlign = TextAlign.Center, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(32.dp))
                Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) {
                    Text("SÍ, BORRAR TODO", fontWeight = FontWeight.Black)
                }
                TextButton(onClick = onCancel) {
                    Text("CANCELAR", color = Color.White)
                }
            }
        }
    }
}

@Composable
fun BossWarningOverlay(state: GameState, biomeIndex: Int) {
    val bossImageRes = when (biomeIndex % 3) {
        0 -> R.drawable.boss1
        1 -> R.drawable.boss2
        else -> R.drawable.boss3
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(id = bossImageRes),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            alpha = 0.85f
        )
        Box(modifier = Modifier.fillMaxSize().background(Color.Red.copy(0.35f)))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("¡PREPÁRATE!", color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.Black)
            Text("EL GUARDIÁN ESTÁ AQUÍ", color = Color.White, fontSize = 20.sp)
            Spacer(modifier = Modifier.height(24.dp))
            Text(state.bossCountdown.toString(), color = Color.White, fontSize = 100.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
fun BossFailOverlay(biomeIndex: Int, onDismiss: () -> Unit) {
    val bossImageRes = when (biomeIndex % 3) {
        0 -> R.drawable.boss1
        1 -> R.drawable.boss2
        else -> R.drawable.boss3
    }
    Box(
        modifier = Modifier.fillMaxSize().clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = bossImageRes),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            alpha = 0.5f
        )
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.7f)))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "HAS FRACASADO",
                color = Color.Red,
                fontSize = 52.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "El guardián te ha vencido",
                color = Color.White.copy(0.7f),
                fontSize = 18.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(48.dp))
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B0000)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.padding(horizontal = 32.dp)
            ) {
                Text("VOLVER AL INICIO", color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
        }
    }
}

@Composable
fun BossFightUI(state: GameState, biomeIndex: Int) {
    val bossImageRes = when (biomeIndex % 3) {
        0 -> R.drawable.boss1
        1 -> R.drawable.boss2
        else -> R.drawable.boss3
    }
    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = bossImageRes),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            alpha = 0.75f
        )
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.45f)))
        Column(modifier = Modifier.fillMaxSize().padding(top = 100.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("LUCHA POR TU VIDA", color = Color.Red, fontWeight = FontWeight.Black, fontSize = 32.sp)
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(progress = state.bossHealth / state.bossMaxHealth, modifier = Modifier.width(300.dp).height(12.dp).clip(RoundedCornerShape(6.dp)), color = Color.Red, trackColor = Color.White.copy(0.1f))

            Spacer(modifier = Modifier.height(60.dp))

            Box(modifier = Modifier.width(320.dp).height(50.dp).background(Color.Black.copy(0.6f), RoundedCornerShape(25.dp)).border(2.dp, Color.White.copy(0.2f), RoundedCornerShape(25.dp))) {
                val safeZoneWidth = 0.25f
                val safeStart = (state.bossSafeZonePos / 100f) - (safeZoneWidth / 2f)
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(safeZoneWidth)
                        .offset(x = (320 * safeStart.coerceIn(0f, 1f - safeZoneWidth)).dp)
                        .background(Color.Green.copy(0.4f))
                )

                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(4.dp)
                        .offset(x = (320 * (state.bossTension / 100f)).dp)
                        .background(Color.White)
                        .shadow(4.dp)
                )
            }
            Text("¡MANTENTE EN EL ÁREA VERDE!", color = Color.White.copy(0.8f), fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))

            Spacer(modifier = Modifier.height(16.dp))
            val bossStabilityLevel = state.upgLevels["boss"] ?: 0
            val bossStabilityVals = GameConfig.upgrades["boss"]?.values ?: listOf(1f)
            val bossStabilityVal = if (bossStabilityLevel < bossStabilityVals.size) bossStabilityVals[bossStabilityLevel] else 1f
            val maxMisses = (5 + ((bossStabilityVal - 1f) / (3.2f - 1f) * 11f)).toInt().coerceIn(5, 16)
            val missesLeft = (maxMisses - state.bossMissCount).coerceAtLeast(0)
            Row(verticalAlignment = Alignment.CenterVertically) {
                repeat(maxMisses) { i ->
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .padding(2.dp)
                            .background(
                                if (i < missesLeft) Color.Red else Color.White.copy(0.15f),
                                CircleShape
                            )
                    )
                }
            }
            Text("Fallos restantes: $missesLeft/$maxMisses", color = Color.White.copy(0.6f), fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
fun PrestigeConfirmOverlay(state: GameState, viewModel: FishingViewModel, onConfirm: () -> Unit, onCancel: () -> Unit) {
    val minScore = viewModel.prestigeMinScore
    val canPrestige = state.score >= minScore
    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.8f)).clickable { onCancel() }, contentAlignment = Alignment.Center) {
        Card(
            modifier = Modifier.fillMaxWidth(0.85f).clickable(enabled = false) { },
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E))
        ) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.AutoAwesome, null, tint = Color.Magenta, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text("NUEVO CICLO", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Reiniciarás tu puntuación y equipo, pero ganarás un bono masivo de puntos permanente. Los precios de la tienda se reinician.",
                    color = Color.White.copy(0.7f), textAlign = TextAlign.Center, fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                val scoreColor = if (canPrestige) Color(0xFF2ECC71) else Color(0xFFE74C3C)
                Surface(color = scoreColor.copy(0.15f), shape = RoundedCornerShape(12.dp)) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("MÍNIMO REQUERIDO", color = scoreColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(formatPoints(minScore) + " pts", color = scoreColor, fontWeight = FontWeight.Black, fontSize = 16.sp)
                        if (!canPrestige) Text("Tienes ${formatPoints(state.score)} pts", color = Color.White.copy(0.5f), fontSize = 11.sp)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("BONO ACTUAL", color = Color.Gray, fontSize = 10.sp)
                        Text("x${String.format("%.2f", state.prestigeMultiplier)}", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Icon(Icons.Default.ArrowForward, null, tint = Color.Cyan)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("NUEVO BONO", color = Color.Cyan, fontSize = 10.sp)
                        Text("x${String.format("%.2f", state.prestigeMultiplier * 1.05f)}", color = Color.Cyan, fontWeight = FontWeight.Black)
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
                Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth(), enabled = canPrestige, colors = ButtonDefaults.buttonColors(containerColor = Color.Magenta, disabledContainerColor = Color.Gray.copy(0.4f))) {
                    Text("REINICIAR Y MEJORAR", fontWeight = FontWeight.Black)
                }
                TextButton(onClick = onCancel) { Text("CONTINUAR PESCANDO", color = Color.White.copy(0.5f)) }
            }
        }
    }
}

@Composable
fun MapSelectorOverlay(state: GameState, onSelect: (Int) -> Unit, onClose: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.9f)).clickable { onClose() }, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("DESTINOS DE PESCA", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
            Spacer(modifier = Modifier.height(24.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 32.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                itemsIndexed(GameConfig.biomes) { index, biome ->
                    val unlocked = state.maxBiomeReached >= index
                    Card(
                        modifier = Modifier.width(260.dp).height(400.dp).clickable(enabled = unlocked) { onSelect(index) },
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF16213E))
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(biome.waterColorTop, Color.Black))))
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text(biome.name.uppercase(), color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp)
                                Text(biome.description, color = Color.White.copy(0.6f), fontSize = 13.sp)
                                Spacer(modifier = Modifier.weight(1f))
                                if (!unlocked) {
                                    Icon(Icons.Default.Lock, null, tint = Color.Red, modifier = Modifier.align(Alignment.CenterHorizontally).size(48.dp))
                                } else {
                                    Button(onClick = { onSelect(index) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color.Cyan)) {
                                        Text("VIAJAR", color = Color.Black, fontWeight = FontWeight.Black)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GameTopHUD(state: GameState, viewModel: FishingViewModel) {
    Row(modifier = Modifier.fillMaxWidth().padding(16.dp).statusBarsPadding(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = { viewModel.addPoints(100_000L) },
            modifier = Modifier.size(36.dp).background(Color(0xFFFFD700).copy(alpha = 0.2f), CircleShape)
        ) { Text("💰", fontSize = 16.sp) }
        Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
            Text("${formatPoints(state.score)} Pts", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
            Text("BONUS: x${String.format("%.2f", state.prestigeMultiplier)}", color = Color.Cyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = { viewModel.requestPrestige() }, modifier = Modifier.background(Color.Magenta.copy(0.2f), CircleShape)) {
                Icon(Icons.Default.AutoAwesome, null, tint = Color.White)
            }
        }
    }
}

@Composable
fun CollectionOverlay(
    state: GameState,
    viewModel: FishingViewModel,
    onFishClick: (FishType) -> Unit,
    onClose: () -> Unit
) {
    var showRanking by remember { mutableStateOf(false) }

    if (showRanking) {
        RankingOverlay(viewModel = viewModel, onClose = { showRanking = false })
        return
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.9f)).clickable { onClose() }, contentAlignment = Alignment.Center) {
        Card(
            modifier = Modifier.fillMaxWidth(0.9f).fillMaxHeight(0.85f).clickable(enabled = false) {},
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F3460)),
            shape = RoundedCornerShape(32.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("ENCICLOPEDIA", color = Color.White, fontWeight = FontWeight.Black, fontSize = 24.sp)
                    Button(
                        onClick = { showRanking = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Leaderboard, null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("RANKING", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 11.sp)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(GameConfig.fishTypes.values.toList()) { type ->
                        val caught = state.caughtSpecies.contains(type.name)
                        Box(
                            modifier = Modifier.aspectRatio(1f).clip(RoundedCornerShape(16.dp))
                                .background(if (caught) Color(0xFF16213E) else Color.Black.copy(0.3f))
                                .clickable(enabled = caught) { onFishClick(type) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (caught) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(modifier = Modifier.size(40.dp).background(type.color, CircleShape))
                                    Text(type.name, color = Color.White, fontSize = 10.sp, textAlign = TextAlign.Center)
                                }
                            } else {
                                Text("?", color = Color.White.copy(0.1f), fontSize = 32.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RankingOverlay(viewModel: FishingViewModel, onClose: () -> Unit) {
    val rankingJugadores by viewModel.rankingJugadores.collectAsState()
    val rankingPeces by viewModel.rankingPeces.collectAsState()
    val rankingLoading by viewModel.rankingLoading.collectAsState()

    var selectedTab by remember { mutableStateOf(0) }
    var fishSeleccionado by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { viewModel.cargarRankingJugadores() }
    LaunchedEffect(fishSeleccionado) {
        fishSeleccionado?.let { viewModel.cargarRankingPorPez(it) }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.95f)).clickable { onClose() },
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.88f).clickable(enabled = false) {},
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0A1628)),
            shape = RoundedCornerShape(32.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("RANKING GLOBAL", color = Color(0xFFFFD700), fontWeight = FontWeight.Black, fontSize = 22.sp)
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, null, tint = Color.White)
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Tabs
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color(0xFF16213E),
                    contentColor = Color.White
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = {
                            selectedTab = 0
                            viewModel.cargarRankingJugadores()
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.EmojiEvents, null, modifier = Modifier.size(16.dp))
                            Text("JUGADORES", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 }
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.SetMeal, null, modifier = Modifier.size(16.dp))
                            Text("PECES", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                if (rankingLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFFFFD700))
                    }
                } else when (selectedTab) {
                    0 -> RankingJugadoresTab(rankingJugadores)
                    1 -> RankingPecesTab(
                        rankingPeces = rankingPeces,
                        fishSeleccionado = fishSeleccionado,
                        onFishSelect = { fishSeleccionado = it }
                    )
                }
            }
        }
    }
}

@Composable
fun ColumnScope.RankingJugadoresTab(ranking: List<RankingJugador>) {
    if (ranking.isEmpty()) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text("Sin datos aún", color = Color.White.copy(0.4f), fontSize = 14.sp)
        }
        return
    }
    androidx.compose.foundation.lazy.LazyColumn(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        itemsIndexed(ranking) { index, entry ->
            val medalColor = when (index) {
                0 -> Color(0xFFFFD700)
                1 -> Color(0xFFC0C0C0)
                2 -> Color(0xFFCD7F32)
                else -> Color.White.copy(0.6f)
            }
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (index < 3) Color(0xFF1A2B3C) else Color(0xFF0D1621)
                ),
                shape = RoundedCornerShape(12.dp),
                border = if (index == 0) BorderStroke(1.dp, Color(0xFFFFD700).copy(0.5f)) else null
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "#${index + 1}",
                        color = medalColor,
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        modifier = Modifier.width(36.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(entry.username, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        if (entry.prestigeLevel > 0) {
                            Text(
                                "Prestigio ${entry.prestigeLevel} · x${String.format("%.2f", entry.prestigeMultiplier)}",
                                color = Color.Magenta,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Text(
                        formatPoints(entry.puntuacion),
                        color = medalColor,
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp
                    )
                    Text(" pts", color = Color.White.copy(0.5f), fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
fun ColumnScope.RankingPecesTab(
    rankingPeces: List<RankingPez>,
    fishSeleccionado: String?,
    onFishSelect: (String) -> Unit
) {
    val fishTypes = GameConfig.fishTypes.values.toList()

    // Selector de pez
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        itemsIndexed(fishTypes) { _, type ->
            val selected = fishSeleccionado == type.name
            Surface(
                modifier = Modifier.clickable { onFishSelect(type.name) },
                shape = RoundedCornerShape(20.dp),
                color = if (selected) type.color else Color(0xFF16213E),
                border = if (selected) BorderStroke(2.dp, Color.White) else BorderStroke(1.dp, Color.White.copy(0.15f))
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(modifier = Modifier.size(20.dp).background(type.color, CircleShape))
                    Spacer(Modifier.height(4.dp))
                    Text(
                        type.name,
                        color = if (selected) Color.Black else Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    Spacer(Modifier.height(12.dp))

    if (fishSeleccionado == null) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text("Selecciona un pez para ver su ranking", color = Color.White.copy(0.4f), fontSize = 13.sp, textAlign = TextAlign.Center)
        }
        return
    }

    if (rankingPeces.isEmpty()) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Sin registros para", color = Color.White.copy(0.4f), fontSize = 13.sp)
                Text(fishSeleccionado, color = Color.White.copy(0.6f), fontWeight = FontWeight.Bold)
            }
        }
        return
    }

    androidx.compose.foundation.lazy.LazyColumn(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        itemsIndexed(rankingPeces) { index, entry ->
            val medalColor = when (index) {
                0 -> Color(0xFFFFD700)
                1 -> Color(0xFFC0C0C0)
                2 -> Color(0xFFCD7F32)
                else -> Color.White.copy(0.6f)
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1621)),
                shape = RoundedCornerShape(12.dp),
                border = if (index == 0) BorderStroke(1.dp, Color(0xFFFFD700).copy(0.5f)) else null
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "#${index + 1}",
                        color = medalColor,
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        modifier = Modifier.width(36.dp)
                    )
                    Text(entry.username, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text(
                        "${String.format("%.2f", entry.pesoMaximo)} kg",
                        color = medalColor,
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}

@Composable
fun FishDetailOverlay(fishType: FishType, state: GameState, onClose: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.8f)).clickable { onClose() }, contentAlignment = Alignment.Center) {
        Card(modifier = Modifier.fillMaxWidth(0.85f).clickable(enabled = false) {}, shape = RoundedCornerShape(32.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF16213E))) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(modifier = Modifier.size(100.dp).background(fishType.color, CircleShape).border(4.dp, Color.White.copy(0.1f), CircleShape))
                Spacer(modifier = Modifier.height(16.dp))
                Text(fishType.name.uppercase(), fontSize = 26.sp, fontWeight = FontWeight.Black, color = Color.White)
                Text(fishType.rarityTier, color = Color.Cyan, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))
                Text(fishType.description, color = Color.White.copy(0.7f), textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    DetailStat("CAPTURA", "${state.speciesCounts[fishType.name] ?: 0}")
                    DetailStat("PESO MÁX", "${String.format("%.1f", state.maxWeights[fishType.name] ?: 0f)}kg")
                }
            }
        }
    }
}

@Composable
fun DetailStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Color.White.copy(0.4f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(value, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
fun UpgradeCardSmall(upgrade: UpgradeConfig, level: Int, cost: Int, currentValue: Float, canAfford: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(enabled = cost != -1) { onClick() },
        colors = CardDefaults.cardColors(containerColor = if (canAfford) Color(0xFF1A2B3C) else Color(0xFF0D1621)),
        border = if (canAfford) BorderStroke(1.dp, Color.Cyan.copy(0.5f)) else null
    ) {
        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(upgrade.name, fontSize = 10.sp, color = Color.Cyan, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Nivel $level", fontSize = 9.sp, color = Color.White.copy(0.6f))
            val displayValue = if (upgrade.unit == "x") String.format("%.2f", currentValue) else "${currentValue.toInt()}"
            Text("$displayValue${upgrade.unit}", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Black)
            Spacer(modifier = Modifier.height(4.dp))
            Text(if (cost == -1) "MAX" else "Coste: ${formatPoints(cost.toLong())}", color = if (canAfford) Color.Yellow else Color.Gray, fontWeight = FontWeight.Bold, fontSize = 9.sp)
        }
    }
}

@Composable
fun BoxScope.DepthMeter(state: GameState) {
    val currentMeters = ((state.hookY - 400f).coerceAtLeast(0f) / GameConfig.M2PX_BASE).roundToInt()
    val maxMeters = (GameConfig.upgrades["depth"]?.values?.get(state.upgLevels["depth"] ?: 0) ?: 40f).toInt()
    Box(modifier = Modifier.fillMaxHeight().width(60.dp).padding(vertical = 120.dp).align(Alignment.CenterStart)) {
        Box(modifier = Modifier.fillMaxHeight().width(4.dp).background(Color.White.copy(0.1f)).align(Alignment.Center))
        val progress = if(maxMeters > 0) (currentMeters.toFloat() / maxMeters).coerceIn(0f, 1f) else 0f
        Box(modifier = Modifier.align(Alignment.TopCenter).offset(y = (progress * 300).dp).size(12.dp).background(Color.Cyan, CircleShape))
        Text("${currentMeters}m", modifier = Modifier.align(Alignment.TopCenter).offset(y = (-30).dp), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun PowerUpIndicatorList(activePowerUps: Map<PowerUpType, Long>) {
    val now = System.currentTimeMillis()
    Column(modifier = Modifier.padding(top = 100.dp, end = 16.dp).fillMaxWidth(), horizontalAlignment = Alignment.End) {
        activePowerUps.forEach { (type, expiry) ->
            val remaining = (expiry - now) / 1000
            if (remaining > 0) {
                Surface(color = Color.Black.copy(0.6f), shape = RoundedCornerShape(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                    Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        val icon = when(type) {
                            PowerUpType.SPEED -> "🚀"
                            PowerUpType.MAGNET -> "🧲"
                            PowerUpType.SHIELD -> "🛡️"
                            PowerUpType.GOLD -> "💰"
                        }
                        Text(icon, fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("${remaining}s", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun GameToast(msg: String) {
    Surface(color = Color(0xEE0A1628), shape = RoundedCornerShape(999.dp), border = BorderStroke(2.dp, Color.Cyan), modifier = Modifier.padding(top = 80.dp)) {
        Text(text = msg, color = Color.White, modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp), fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
fun GachaButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onClick() }) {
        Surface(modifier = Modifier.size(54.dp), shape = RoundedCornerShape(16.dp), color = Color(0xFF1A222C), border = BorderStroke(1.dp, Color.White.copy(0.1f))) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.padding(14.dp))
        }
        Text(label, color = Color.White.copy(0.7f), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun GameCanvas(state: GameState, fishList: List<Fish>, powerUps: List<PowerUp>, biome: Biome) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val scale = size.width / 1080f
        val surfaceY = 400f * scale
        val camOffset = state.camY * scale
        drawRect(color = biome.skyColor, size = Size(size.width, surfaceY - camOffset))
        val biomeLimit = surfaceY + ((state.currentBiomeIndex + 1) * 300 * GameConfig.M2PX_BASE * scale)
        val bossZoneStart = biomeLimit - 600f * scale
        drawRect(
            brush = Brush.verticalGradient(listOf(biome.waterColorTop, biome.waterColorBottom, if(state.hookY * scale > bossZoneStart) Color.Black else biome.waterColorBottom), startY = (surfaceY - camOffset).coerceAtLeast(0f)),
            topLeft = Offset(0f, (surfaceY - camOffset).coerceAtLeast(0f)),
            size = Size(size.width, size.height)
        )
        fishList.forEach { fish ->
            val fy = (fish.y * scale) - camOffset
            if (!fish.isCaught && fy > surfaceY - camOffset) {
                val fw = fish.type.width * scale
                val fh = fish.type.height * scale
                val cx = fish.x * scale
                val facingLeft = fish.vx < 0f

                withTransform({
                    translate(left = cx, top = fy)
                    if (facingLeft) scale(scaleX = -1f, scaleY = 1f)
                }) {
                    if (fish.isRare) {
                        drawOval(Color.Yellow.copy(alpha = 0.35f),
                            topLeft = Offset(-(fw + 6f * scale), -(fh + 6f * scale)),
                            size = Size((fw + 6f * scale) * 2f, (fh + 6f * scale) * 2f))
                    }
                    val tailPath = Path().apply {
                        moveTo(-fw, 0f)
                        lineTo(-fw - fh * 1.1f,  fh * 0.85f)
                        lineTo(-fw - fh * 1.1f, -fh * 0.85f)
                        close()
                    }
                    drawPath(tailPath, fish.type.color.copy(alpha = 0.85f))
                    drawOval(fish.type.color, topLeft = Offset(-fw, -fh), size = Size(fw * 2f, fh * 2f))
                    val eyeR = (fh * 0.28f).coerceAtLeast(3f * scale)
                    val eyeX = fw * 0.5f
                    val eyeY = -fh * 0.3f
                    drawCircle(Color.White, radius = eyeR, center = Offset(eyeX, eyeY))
                    drawCircle(Color.Black, radius = eyeR * 0.5f, center = Offset(eyeX + eyeR * 0.15f, eyeY))
                    if (fish.isRare) drawOval(Color.Yellow, topLeft = Offset(-fw, -fh), size = Size(fw * 2f, fh * 2f), style = Stroke(width = 3f * scale))
                }
            }
        }
        if (state.gamePhase != "MENU") {
            val hX = state.hookX * scale
            val hY = (state.hookY * scale) - camOffset
            val lineColor = if (state.isTurbo) Color.Cyan else Color.White
            val surfaceScreenY = surfaceY - camOffset
            val boatCenterX = size.width / 2f
            val boatTopY = surfaceScreenY - 18f * scale
            val boatHalfTopW = 55f * scale
            val boatHalfBotW = 35f * scale
            val rodTipX = boatCenterX + 10f * scale
            val rodTipY = boatTopY - 10f * scale

            if (surfaceScreenY >= -30f) {
                val hullPath = Path().apply {
                    moveTo(boatCenterX - boatHalfTopW, boatTopY)
                    lineTo(boatCenterX + boatHalfTopW, boatTopY)
                    lineTo(boatCenterX + boatHalfBotW, surfaceScreenY)
                    lineTo(boatCenterX - boatHalfBotW, surfaceScreenY)
                    close()
                }
                drawPath(hullPath, Color(0xFF8B5E3C))
                drawLine(Color(0xFFA0522D),
                    Offset(boatCenterX - boatHalfTopW, boatTopY),
                    Offset(boatCenterX + boatHalfTopW, boatTopY),
                    strokeWidth = 3f * scale)
                drawLine(Color(0xFF6B4226),
                    Offset(boatCenterX, boatTopY),
                    Offset(rodTipX, rodTipY),
                    strokeWidth = 2.5f * scale)
            }

            val lineStartX = if (surfaceScreenY >= -30f) rodTipX else boatCenterX
            val lineStartY = if (surfaceScreenY >= -30f) rodTipY else surfaceScreenY.coerceAtLeast(0f)
            drawLine(lineColor.copy(0.7f), Offset(lineStartX, lineStartY), Offset(hX, hY), strokeWidth = 3f)
            drawCircle(lineColor, radius = 12f * scale, center = Offset(hX, hY))
        }
    }
}
