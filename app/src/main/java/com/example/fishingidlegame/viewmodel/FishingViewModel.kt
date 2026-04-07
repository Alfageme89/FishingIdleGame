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
        val typeConfig = GameConfig.fishTypes.random()
        val multiplier = (50..200).random() / 100f
        val actualKg = typeConfig.kg * multiplier
        val actualPts = (typeConfig.pts * multiplier).toInt()

        return Fish(
            type = FishType(typeConfig.name, typeConfig.color, typeConfig.pts, typeConfig.w, typeConfig.h, typeConfig.kg),
            x = (30..450).random().toFloat(),
            y = surfaceY + (50..3000).random().toFloat(),
            vx = ((-150..150).random() / 100f),
            vy = ((-30..30).random() / 100f),
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
                    if (ny < surfaceY + 10 || ny > surfaceY + (activeMaxDepth * GameConfig.M2PX_BASE) + 100) nvy *= -1
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
                    showToast("🔼 RECOGIENDO...")
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
                    if (dist < 30f) {
                        val newTotalWeight = currentState.currentKg + fish.kg
                        if (newTotalWeight <= activeMaxKg) {
                            val earned = (fish.pts * activePtsMult).toLong()
                            _state.update { it.copy(
                                currentKg = newTotalWeight,
                                score = it.score + earned,
                                weightFull = newTotalWeight >= activeMaxKg,
                                totalFishCaught = it.totalFishCaught + 1
                            ) }
                            showToast("🐟 ${fish.type.name} +${earned}pts")
                            return@map fish.copy(isCaught = true)
                        } else if (!currentState.weightFull) {
                            _state.update { it.copy(weightFull = true, gamePhase = "REELING") }
                            showToast("⚠️ ¡LLENO! Subiendo...")
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
                hookY = surfaceY - 50f,
                currentKg = 0f,
                weightFull = false,
                camYTarget = 0f
            ) }
        }
    }

    private fun finishRound() {
        val caughtCount = _fishList.value.count { it.isCaught }
        if (caughtCount > 0) {
            showToast("✅ ¡${caughtCount} peces capturados!")
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
                showToast("✨ ¡Mejora comprada!")
            } else {
                showToast("❌ No tienes puntos suficientes")
            }
        }
    }

    private fun updateActiveStats() {
        val levels = _state.value.upgLevels
        activeMaxDepth = GameConfig.upgrades["depth"]?.values?.get(levels["depth"] ?: 0) ?: 20f
        activeMaxKg = GameConfig.upgrades["weight"]?.values?.get(levels["weight"] ?: 0) ?: 20f
        activePtsMult = GameConfig.upgrades["pts"]?.values?.get(levels["pts"] ?: 0) ?: 1f
    }
}
