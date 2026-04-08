package com.example.fishingidlegame.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fishingidlegame.config.*
import com.example.fishingidlegame.model.*
import com.example.fishingidlegame.data.GameRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.sqrt

class FishingViewModel(private val repository: GameRepository) : ViewModel() {

    private val _state = MutableStateFlow(GameState())
    val state = _state.asStateFlow()

    private val _fishList = MutableStateFlow<List<Fish>>(emptyList())
    val fishList = _fishList.asStateFlow()

    private var toastJob: Job? = null
    private var activeMaxDepth = 20f
    private var activeMaxKg = 20f
    private var activePtsMult = 1f

    private val surfaceY = 400f
    private val worldWidth = 480f

    init {
        loadUserData()
        startGameLoop()
    }

    private fun loadUserData() {
        viewModelScope.launch {
            val savedState = repository.loadProgress()
            _state.update { savedState }
            updateActiveStats()
            checkBiomeUnlocked() // Verificar en qué bioma estamos según nuestra profundidad
            spawnInitialFish()
        }
    }

    private fun saveUserData() {
        viewModelScope.launch { repository.saveProgress(_state.value) }
    }

    private fun spawnInitialFish() {
        _fishList.value = List(GameConfig.NUM_FISH) { createRandomFish() }
    }

    private fun createRandomFish(): Fish {
        val currentBiome = GameConfig.biomes[_state.value.currentBiomeIndex]
        val fishName = currentBiome.fishTypeNames.random()
        val typeConfig = GameConfig.fishTypes[fishName] ?: GameConfig.fishTypes.values.first()
        
        // Variación de peso y puntos
        val multiplier = (50..220).random() / 100f
        val prestigeBonus = _state.value.prestigeMultiplier
        
        val actualKg = typeConfig.baseKg * multiplier
        val actualPts = (typeConfig.basePoints * multiplier * prestigeBonus).toInt()

        return Fish(
            type = typeConfig,
            x = (30..450).random().toFloat(),
            y = surfaceY + (100..4000).random().toFloat(), // Spawnean por debajo de la superficie
            vx = ((-180..180).random() / 100f),
            vy = ((-40..40).random() / 100f),
            kg = actualKg,
            pts = actualPts,
            multiplier = multiplier
        )
    }

    private fun startGameLoop() {
        viewModelScope.launch {
            while (true) {
                updateGame()
                delay(16)
            }
        }
    }

    private fun updateGame() {
        val currentState = _state.value
        val newCamY = currentState.camY + (currentState.camYTarget - currentState.camY) * 0.1f

        _fishList.update { list ->
            list.map { fish ->
                if (fish.isCaught) {
                    fish.copy(x = currentState.hookX, y = currentState.hookY + 20f)
                } else {
                    var nx = fish.x + fish.vx
                    var ny = fish.y + fish.vy
                    var nvx = fish.vx
                    var nvy = fish.vy
                    if (nx < 20 || nx > worldWidth - 20) nvx *= -1
                    // Los peces no suben de la superficie
                    if (ny < surfaceY + 20 || ny > surfaceY + 10000) nvy *= -1
                    fish.copy(x = nx, y = ny, vx = nvx, vy = nvy)
                }
            }
        }

        when (currentState.gamePhase) {
            "FISHING" -> {
                val newHookY = currentState.hookY + GameConfig.CAST_SPEED
                var camTarget = currentState.camYTarget
                if (newHookY - newCamY > 600f) camTarget = newHookY - 600f

                val maxDepthPx = surfaceY + (activeMaxDepth * GameConfig.M2PX_BASE)
                if (newHookY >= maxDepthPx) {
                    _state.update { it.copy(hookY = maxDepthPx, gamePhase = "REELING", camYTarget = camTarget, camY = newCamY) }
                } else {
                    _state.update { it.copy(hookY = newHookY, camYTarget = camTarget, camY = newCamY) }
                    checkCollisions()
                }
            }
            "REELING" -> {
                val fillPct = (currentState.currentKg / activeMaxKg).coerceIn(0f, 1f)
                val reelSpeed = GameConfig.REEL_SPEED_BASE + (GameConfig.REEL_SPEED_FULL - GameConfig.REEL_SPEED_BASE) * (fillPct * fillPct)
                val newHookY = currentState.hookY - reelSpeed
                var camTarget = currentState.camYTarget
                if (newHookY - newCamY < 300f) camTarget = (newHookY - 300f).coerceAtLeast(0f)

                if (newHookY <= surfaceY - 50f) {
                    finishRound()
                } else {
                    _state.update { it.copy(hookY = newHookY, camYTarget = camTarget, camY = newCamY) }
                    if (!currentState.weightFull) checkCollisions()
                }
            }
            else -> _state.update { it.copy(camY = newCamY) }
        }
    }

    private fun checkCollisions() {
        val currentState = _state.value
        _fishList.update { list ->
            list.map { fish ->
                if (!fish.isCaught) {
                    val dx = fish.x - currentState.hookX
                    val dy = fish.y - currentState.hookY
                    val dist = sqrt(dx * dx + dy * dy)
                    if (dist < 35f) {
                        val newTotalWeight = currentState.currentKg + fish.kg
                        if (newTotalWeight <= activeMaxKg) {
                            val earned = (fish.pts * activePtsMult).toLong()
                            _state.update { it.copy(
                                currentKg = newTotalWeight,
                                score = it.score + earned,
                                totalLifetimeScore = it.totalLifetimeScore + earned,
                                weightFull = newTotalWeight >= activeMaxKg * 0.9f,
                                totalFishCaught = it.totalFishCaught + 1
                            ) }
                            showToast("🐟 ${fish.type.name} +${earned} pts")
                            return@map fish.copy(isCaught = true)
                        } else if (!currentState.weightFull) {
                            _state.update { it.copy(weightFull = true, gamePhase = "REELING") }
                            showToast("⚠️ ¡RED LLENA!")
                        }
                    }
                }
                fish
            }
        }
    }

    fun launchHook(startX: Float) {
        if (_state.value.gamePhase == "MENU") {
            _state.update { it.copy(
                gamePhase = "FISHING",
                hookX = startX,
                hookY = surfaceY - 60f,
                currentKg = 0f,
                weightFull = false,
                camYTarget = 0f
            ) }
        }
    }

    private fun finishRound() {
        _state.update { it.copy(gamePhase = "MENU", camYTarget = 0f) }
        _fishList.update { list ->
            list.filter { !it.isCaught } + List(GameConfig.NUM_FISH - list.count { !it.isCaught }) { createRandomFish() }
        }
        saveUserData()
    }

    fun buyUpgrade(upgradeKey: String) {
        val upgrade = GameConfig.upgrades[upgradeKey] ?: return
        val currentLevel = _state.value.upgLevels[upgradeKey] ?: 0
        
        if (currentLevel < upgrade.levels.size) {
            val cost = upgrade.levels[currentLevel]
            if (_state.value.score >= cost) {
                _state.update { s ->
                    val newLevels = s.upgLevels.toMutableMap()
                    newLevels[upgradeKey] = currentLevel + 1
                    s.copy(score = s.score - cost, upgLevels = newLevels)
                }
                updateActiveStats()
                checkBiomeUnlocked()
                saveUserData()
            }
        }
    }

    private fun checkBiomeUnlocked() {
        val currentDepth = activeMaxDepth
        var bestBiomeIndex = 0
        GameConfig.biomes.forEachIndexed { index, biome ->
            if (currentDepth >= biome.minDepthRequired) {
                bestBiomeIndex = index
            }
        }
        
        if (bestBiomeIndex != _state.value.currentBiomeIndex) {
            _state.update { it.copy(currentBiomeIndex = bestBiomeIndex) }
            showToast("✨ Nuevo Bioma: ${GameConfig.biomes[bestBiomeIndex].name}")
            // Limpiar peces viejos y spawnear los del nuevo bioma
            spawnInitialFish()
        }
    }

    // ── MECÁNICA DE PRESTIGIO (RESET) ──
    fun resetForPrestige() {
        val currentState = _state.value
        // Cálculo de nuevo multiplicador: 1.0 + (Puntos totales / 5000)
        val newMultiplier = 1.0f + (currentState.totalLifetimeScore / 5000f)
        
        _state.update { 
            GameState(
                prestigeMultiplier = newMultiplier,
                totalLifetimeScore = currentState.totalLifetimeScore,
                score = 0, // Reset de puntos actuales
                totalFishCaught = 0
            )
        }
        updateActiveStats()
        spawnInitialFish()
        saveUserData()
        showToast("🌀 PRESTIGIO: x${"%.2f".format(newMultiplier)} permanente")
    }

    private fun updateActiveStats() {
        val levels = _state.value.upgLevels
        activeMaxDepth = GameConfig.upgrades["depth"]?.values?.get(levels["depth"] ?: 0) ?: 20f
        activeMaxKg = GameConfig.upgrades["weight"]?.values?.get(levels["weight"] ?: 0) ?: 20f
        activePtsMult = GameConfig.upgrades["pts"]?.values?.get(levels["pts"] ?: 0) ?: 1f
    }

    private fun showToast(message: String) {
        toastJob?.cancel()
        _state.update { it.copy(toastMessage = message) }
        toastJob = viewModelScope.launch {
            delay(2500)
            _state.update { it.copy(toastMessage = null) }
        }
    }
}
