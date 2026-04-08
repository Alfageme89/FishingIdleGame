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

    // Lógica de Boss Fight (Tug-of-War por toques)
    private var tensionValue = 50f
    private var bossStamina = 100f

    init {
        loadUserData()
        startGameLoop()
    }

    private fun loadUserData() {
        viewModelScope.launch {
            val savedState = repository.loadProgress()
            _state.update { savedState }
            updateActiveStats()
            checkBiomeUnlocked()
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
        val multiplier = (50..220).random() / 100f
        val actualKg = typeConfig.baseKg * multiplier
        val actualPts = (typeConfig.basePoints * multiplier * _state.value.prestigeMultiplier).toInt()

        return Fish(
            type = typeConfig,
            x = (30..450).random().toFloat(),
            y = surfaceY + (100..4000).random().toFloat(),
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
                applyCrewEarnings()
                delay(16)
            }
        }
    }

    private fun applyCrewEarnings() {
        val crewLevel = _state.value.upgLevels["crew"] ?: 0
        if (crewLevel > 0) {
            val earningsPerFrame = GameConfig.upgrades["crew"]!!.values[crewLevel] / 60f
            _state.update { it.copy(
                score = it.score + earningsPerFrame.toLong(),
                totalLifetimeScore = it.totalLifetimeScore + earningsPerFrame.toLong()
            ) }
        }
    }

    private fun updateGame() {
        val currentState = _state.value
        if (currentState.gamePhase == "BOSS_FIGHT") {
            updateBossFightLogic()
        } else {
            val newCamY = currentState.camY + (currentState.camYTarget - currentState.camY) * 0.1f
            updateStandardGame(currentState, newCamY)
        }
    }

    private fun updateStandardGame(currentState: GameState, newCamY: Float) {
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

                val biomeLimit = surfaceY + ((currentState.currentBiomeIndex + 1) * 300 * GameConfig.M2PX_BASE)
                
                if (newHookY >= biomeLimit && !currentState.bossActive) {
                    startBossFight()
                } else if (newHookY >= surfaceY + (activeMaxDepth * GameConfig.M2PX_BASE)) {
                    _state.update { it.copy(gamePhase = "REELING", camYTarget = camTarget, camY = newCamY) }
                } else {
                    _state.update { it.copy(hookY = newHookY, camYTarget = camTarget, camY = newCamY) }
                    checkCollisions()
                }
            }
            "REELING" -> {
                val reelSpeed = GameConfig.REEL_SPEED_BASE + (GameConfig.REEL_SPEED_FULL - GameConfig.REEL_SPEED_BASE) * 0.5f
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

    // ── LÓGICA DE BOSS REFORMULADA (TOQUES) ──
    private fun startBossFight() {
        val biome = GameConfig.biomes[_state.value.currentBiomeIndex]
        val bossConfig = GameConfig.bosses[biome.bossName] ?: return
        tensionValue = 50f
        bossStamina = bossConfig.maxHealth
        _state.update { it.copy(
            gamePhase = "BOSS_FIGHT",
            bossActive = true,
            bossHealth = bossStamina,
            bossMaxHealth = bossConfig.maxHealth
        ) }
        showToast("🎣 ¡HA PICADO UN MONSTRUO! ¡DA TOQUES!")
    }

    // Se llama cada vez que el usuario toca la pantalla
    fun onPlayerTap() {
        if (_state.value.gamePhase == "BOSS_FIGHT") {
            tensionValue += 6f // Impulso por toque
        }
    }

    private fun updateBossFightLogic() {
        // La tensión cae naturalmente (el boss tira)
        // La caída es más fuerte cuanto más profundo sea el bioma
        val dropSpeed = 0.8f + (_state.value.currentBiomeIndex * 0.3f)
        tensionValue -= dropSpeed

        // Oscilación aleatoria para simular lucha real
        tensionValue += (Math.random().toFloat() - 0.5f) * 2f

        // Solo hace daño en el "Sweet Spot" (entre 45 y 75)
        if (tensionValue in 45f..75f) {
            val damage = 0.5f * (1 + (_state.value.upgLevels["pts"] ?: 0) * 0.15f)
            bossStamina -= damage
        }

        // Comprobar límites
        if (tensionValue >= 100f) {
            showToast("💥 ¡SEDAL ROTO! ¡Demasiada fuerza!")
            finishRound()
        } else if (tensionValue <= 0f) {
            showToast("💨 ¡SE HA ESCAPADO! ¡Tira más!")
            finishRound()
        } else if (bossStamina <= 0f) {
            winBossFight()
        }

        _state.update { it.copy(bossHealth = bossStamina) }
    }

    private fun winBossFight() {
        val biome = GameConfig.biomes[_state.value.currentBiomeIndex]
        val reward = GameConfig.bosses[biome.bossName]?.reward ?: 0L
        showToast("🏆 ¡DERROTADO! Recompensa: $reward pts")
        _state.update { it.copy(
            gamePhase = "REELING",
            bossActive = false,
            score = it.score + reward,
            totalLifetimeScore = it.totalLifetimeScore + reward,
            nextBiomeUnlocked = true
        ) }
        saveUserData()
    }

    fun launchHook(startX: Float) {
        if (_state.value.gamePhase == "MENU") {
            _state.update { it.copy(gamePhase = "FISHING", hookX = startX, hookY = surfaceY - 60f, currentKg = 0f, weightFull = false) }
        }
    }

    private fun finishRound() {
        _state.update { it.copy(gamePhase = "MENU", bossActive = false) }
        _fishList.update { list ->
            list.filter { !it.isCaught } + List(GameConfig.NUM_FISH - list.count { !it.isCaught }) { createRandomFish() }
        }
        checkBiomeUnlocked()
        saveUserData()
    }

    private fun checkBiomeUnlocked() {
        val currentState = _state.value
        val nextBiomeIndex = currentState.currentBiomeIndex + 1
        if (nextBiomeIndex < GameConfig.biomes.size && currentState.nextBiomeUnlocked) {
            _state.update { it.copy(currentBiomeIndex = nextBiomeIndex, nextBiomeUnlocked = false) }
            showToast("🌎 NUEVO BIOMA: ${GameConfig.biomes[nextBiomeIndex].name}")
        }
    }

    fun buyUpgrade(key: String) {
        val upgrade = GameConfig.upgrades[key] ?: return
        val level = _state.value.upgLevels[key] ?: 0
        if (level < upgrade.levels.size) {
            val cost = upgrade.levels[level]
            if (_state.value.score >= cost) {
                _state.update { s ->
                    val newLevels = s.upgLevels.toMutableMap()
                    newLevels[key] = level + 1
                    s.copy(score = s.score - cost, upgLevels = newLevels)
                }
                updateActiveStats()
                saveUserData()
            }
        }
    }

    private fun updateActiveStats() {
        val levels = _state.value.upgLevels
        activeMaxDepth = GameConfig.upgrades["depth"]?.values?.get(levels["depth"] ?: 0) ?: 20f
        activeMaxKg = GameConfig.upgrades["weight"]?.values?.get(levels["weight"] ?: 0) ?: 20f
        activePtsMult = GameConfig.upgrades["pts"]?.values?.get(levels["pts"] ?: 0) ?: 1f
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
                            _state.update { it.copy(currentKg = newTotalWeight, score = it.score + earned, totalLifetimeScore = it.totalLifetimeScore + earned, totalFishCaught = it.totalFishCaught + 1) }
                            showToast("🐟 ${fish.type.name} +${earned}")
                            return@map fish.copy(isCaught = true)
                        }
                    }
                }
                fish
            }
        }
    }

    private fun showToast(msg: String) {
        toastJob?.cancel()
        _state.update { it.copy(toastMessage = msg) }
        toastJob = viewModelScope.launch { delay(2500); _state.update { it.copy(toastMessage = null) } }
    }
}
