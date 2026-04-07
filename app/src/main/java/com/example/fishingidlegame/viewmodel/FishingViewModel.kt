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
import kotlin.random.Random

class FishingViewModel(private val repository: GameRepository) : ViewModel() {

    private val _state = MutableStateFlow(GameState())
    val state = _state.asStateFlow()

    private val _fishList = MutableStateFlow<List<Fish>>(emptyList())
    val fishList = _fishList.asStateFlow()

    private var toastJob: Job? = null
    private var activeMaxDepth = 30f
    private var activeMaxKg = 10f
    private var activeSpeedMult = 1f
    private var activeLuck = 0f

    private val surfaceY = 400f
    private val worldWidth = 480f

    init {
        loadUserData()
        spawnInitialFish()
        startGameLoop()
    }

    private fun showToast(message: String) {
        toastJob?.cancel()
        _state.update { it.copy(toastMessage = message) }
        toastJob = viewModelScope.launch {
            delay(2200)
            _state.update { it.copy(toastMessage = null) }
        }
    }

    private fun loadUserData() {
        viewModelScope.launch {
            val savedState = repository.loadProgress()
            _state.update { savedState }
            updateActiveStats()
        }
    }

    private fun saveUserData() {
        viewModelScope.launch { repository.saveProgress(_state.value) }
    }

    private fun spawnInitialFish() {
        _fishList.value = List(GameConfig.NUM_FISH) { createRandomFish() }
    }

    private fun createRandomFish(): Fish {
        // Lógica de rareza basada en suerte
        val luckRoll = Random.nextFloat() * 100
        val availableFish = GameConfig.fishTypes.filter { type ->
            // Peces más raros aparecen a mayor profundidad o con más suerte
            // Implementación simple: los últimos de la lista son más difíciles de encontrar
            val index = GameConfig.fishTypes.indexOf(type)
            val threshold = 100 - (index * 10) + (activeLuck * 0.5f)
            luckRoll < threshold || index < 3
        }
        
        val typeConfig = availableFish.random()
        val multiplier = (80..150).random() / 100f
        val actualKg = typeConfig.kg * multiplier
        val actualPts = (typeConfig.pts * multiplier).toInt()

        // Profundidad aleatoria basada en el pez (los grandes están más profundo)
        val index = GameConfig.fishTypes.indexOf(typeConfig)
        val minY = surfaceY + (index * 200f)
        val maxY = surfaceY + (index * 800f) + 1000f

        return Fish(
            type = FishType(typeConfig.name, typeConfig.color, typeConfig.pts, typeConfig.w, typeConfig.h, typeConfig.kg, typeConfig.icon),
            x = (20..460).random().toFloat(),
            y = (minY..maxY).random().toFloat(),
            vx = ((-250..250).random() / 100f) * (1 + (index * 0.1f)),
            vy = ((-50..50).random() / 100f),
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
        val newCamY = currentState.camY + (currentState.camYTarget - currentState.camY) * 0.12f

        _fishList.update { list ->
            list.map { fish ->
                if (fish.isCaught) {
                    fish.copy(x = currentState.hookX, y = currentState.hookY + 20f)
                } else {
                    var nx = fish.x + fish.vx
                    var ny = fish.y + fish.vy
                    var nvx = fish.vx
                    var nvy = fish.vy
                    if (nx < 10 || nx > worldWidth - 10) nvx *= -1
                    // Limitar peces al rango de profundidad visible o un poco más
                    if (ny < surfaceY + 20 || ny > surfaceY + 20000) nvy *= -1 
                    fish.copy(x = nx, y = ny, vx = nvx, vy = nvy)
                }
            }
        }

        when (currentState.gamePhase) {
            "FISHING" -> {
                val castSpeed = GameConfig.CAST_SPEED * activeSpeedMult
                val newHookY = currentState.hookY + castSpeed
                var camTarget = currentState.camYTarget
                if (newHookY - newCamY > 550f) camTarget = newHookY - 550f

                val maxDepthPx = surfaceY + (activeMaxDepth * GameConfig.M2PX_BASE)
                if (newHookY >= maxDepthPx) {
                    _state.update { it.copy(hookY = maxDepthPx, gamePhase = "REELING", camYTarget = camTarget, camY = newCamY) }
                    showToast("🔼 ¡MÁXIMA PROFUNDIDAD!")
                } else {
                    _state.update { it.copy(hookY = newHookY, camYTarget = camTarget, camY = newCamY) }
                    checkCollisions()
                }
            }
            "REELING" -> {
                val fillPct = (currentState.currentKg / activeMaxKg).coerceIn(0f, 1f)
                val reelSpeed = (GameConfig.REEL_SPEED_BASE + (GameConfig.REEL_SPEED_FULL - GameConfig.REEL_SPEED_BASE) * (1 - fillPct)) * activeSpeedMult
                val newHookY = currentState.hookY - reelSpeed
                var camTarget = currentState.camYTarget
                if (newHookY - newCamY < 250f) camTarget = (newHookY - 250f).coerceAtLeast(0f)

                if (newHookY <= surfaceY - 40f) {
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
                    // Colisión más generosa para "pro" feel
                    if (dist < 35f) {
                        val newTotalWeight = currentState.currentKg + fish.kg
                        if (newTotalWeight <= activeMaxKg) {
                            val earned = fish.pts.toLong()
                            _state.update { it.copy(
                                currentKg = newTotalWeight,
                                score = it.score + earned,
                                weightFull = newTotalWeight >= activeMaxKg * 0.95f,
                                totalFishCaught = it.totalFishCaught + 1
                            ) }
                            showToast("${fish.type.icon} ${fish.type.name} +${earned}pts")
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
        val caughtCount = _fishList.value.count { it.isCaught }
        if (caughtCount > 0) {
            showToast("💰 ¡Captura vendida!")
        }
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
                saveUserData()
                showToast("⚡ ${upgrade.name} UP!")
            } else {
                showToast("❌ No hay fondos")
            }
        }
    }

    private fun updateActiveStats() {
        val levels = _state.value.upgLevels
        activeMaxDepth = GameConfig.upgrades["depth"]?.values?.get(levels["depth"] ?: 0) ?: 30f
        activeMaxKg = GameConfig.upgrades["weight"]?.values?.get(levels["weight"] ?: 0) ?: 10f
        activeSpeedMult = GameConfig.upgrades["speed"]?.values?.get(levels["speed"] ?: 0) ?: 1f
        activeLuck = GameConfig.upgrades["luck"]?.values?.get(levels["luck"] ?: 0) ?: 0f
    }
}
