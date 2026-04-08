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
    private var activeMaxDepth = 40f
    private var activeMaxKg = 15f
    private var activePtsMult = 1f
    private var steeringPower = 0.05f

    private val surfaceY = 400f
    private val worldWidth = 1080f

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
            spawnFishForCurrentDepth()
        }
    }

    private fun spawnFishForCurrentDepth() {
        val currentMaxY = surfaceY + (activeMaxDepth * GameConfig.M2PX_BASE)
        _fishList.update { 
            List(GameConfig.NUM_FISH) { createRandomFish(currentMaxY) }
        }
    }

    private fun createRandomFish(maxY: Float): Fish {
        val currentBiome = GameConfig.biomes[_state.value.currentBiomeIndex]
        val fishName = currentBiome.fishTypeNames.random()
        val typeConfig = GameConfig.fishTypes[fishName] ?: GameConfig.fishTypes.values.first()
        val multiplier = (70..180).random() / 100f
        
        return Fish(
            type = typeConfig,
            x = (50..(worldWidth.toInt() - 50)).random().toFloat(),
            y = (surfaceY.toInt() + 100..maxY.toInt()).random().toFloat(),
            vx = ((-250..250).random() / 100f),
            vy = ((-80..80).random() / 100f),
            kg = typeConfig.baseKg * multiplier,
            pts = (typeConfig.basePoints * multiplier * _state.value.prestigeMultiplier).toInt(),
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
        if (currentState.gamePhase == "BOSS_FIGHT") {
            updateBossFightLogic()
        } else {
            val newCamY = currentState.camY + (currentState.camYTarget - currentState.camY) * 0.12f
            
            // Movimiento suave del anzuelo hacia el objetivo X
            val newHookX = currentState.hookX + (currentState.hookXTarget - currentState.hookX) * steeringPower
            
            _state.update { it.copy(hookX = newHookX) }
            updateStandardGame(currentState, newCamY)
        }
    }

    private fun updateStandardGame(currentState: GameState, newCamY: Float) {
        _fishList.update { list ->
            list.map { fish ->
                if (fish.isCaught) {
                    fish.copy(x = currentState.hookX, y = currentState.hookY + 25f)
                } else {
                    var nx = fish.x + fish.vx
                    var ny = fish.y + fish.vy
                    var nvx = fish.vx
                    var nvy = fish.vy
                    if (nx < 0 || nx > worldWidth) nvx *= -1
                    if (ny < surfaceY || ny > surfaceY + 10000f) nvy *= -1
                    fish.copy(x = nx, y = ny, vx = nvx, vy = nvy)
                }
            }
        }

        when (currentState.gamePhase) {
            "FISHING" -> {
                val newHookY = currentState.hookY + GameConfig.CAST_SPEED
                var camTarget = (newHookY - 500f).coerceAtLeast(0f)
                val biomeLimit = surfaceY + ((currentState.currentBiomeIndex + 1) * 300 * GameConfig.M2PX_BASE)
                val upgradeLimit = surfaceY + (activeMaxDepth * GameConfig.M2PX_BASE)

                if (newHookY >= biomeLimit && !currentState.bossActive) {
                    startBossFight()
                } else if (newHookY >= upgradeLimit) {
                    _state.update { it.copy(gamePhase = "REELING", camYTarget = camTarget, camY = newCamY) }
                } else {
                    _state.update { it.copy(hookY = newHookY, camYTarget = camTarget, camY = newCamY) }
                    checkCollisions()
                }
            }
            "REELING" -> {
                val isFast = currentState.currentKg >= activeMaxKg
                val reelSpeed = if (isFast) GameConfig.REEL_SPEED_FULL else GameConfig.REEL_SPEED_BASE
                val newHookY = currentState.hookY - reelSpeed
                val camTarget = (newHookY - 400f).coerceAtLeast(0f)

                if (newHookY <= surfaceY - 100f) {
                    finishRound()
                } else {
                    _state.update { it.copy(hookY = newHookY, camYTarget = camTarget, camY = newCamY) }
                    if (currentState.currentKg < activeMaxKg) checkCollisions()
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
                    val dy = fish.y - (currentState.hookY + 20f)
                    val dist = sqrt(dx * dx + dy * dy)
                    
                    if (dist < 50f) {
                        val newTotalWeight = currentState.currentKg + fish.kg
                        if (newTotalWeight <= activeMaxKg) {
                            val earned = (fish.pts * activePtsMult).toLong()
                            
                            // Registrar captura para colección
                            val newSpecies = currentState.caughtSpecies.toMutableSet()
                            newSpecies.add(fish.type.name)
                            val newCounts = currentState.speciesCounts.toMutableMap()
                            newCounts[fish.type.name] = (newCounts[fish.type.name] ?: 0) + 1

                            _state.update { it.copy(
                                currentKg = newTotalWeight,
                                score = it.score + earned,
                                totalLifetimeScore = it.totalLifetimeScore + earned,
                                totalFishCaught = it.totalFishCaught + 1,
                                caughtSpecies = newSpecies,
                                speciesCounts = newCounts
                            ) }
                            showToast("🐟 ${fish.type.name} +${earned}")
                            return@map fish.copy(isCaught = true)
                        } else {
                            _state.update { it.copy(weightFull = true) }
                        }
                    }
                }
                fish
            }
        }
    }

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
        showToast("🎣 ¡JEFE DETECTADO! ¡TOCA RÁPIDO!")
    }

    fun onPlayerTap() {
        if (_state.value.gamePhase == "BOSS_FIGHT") {
            tensionValue += 10f // Aumentado impacto del toque
        }
    }

    fun setHookTarget(x: Float) {
        if (_state.value.gamePhase == "FISHING" || _state.value.gamePhase == "REELING") {
            _state.update { it.copy(hookXTarget = x) }
        }
    }

    fun forceReel() {
        if (_state.value.gamePhase == "FISHING") {
            _state.update { it.copy(gamePhase = "REELING") }
        }
    }

    private fun updateBossFightLogic() {
        // Balanceo: el primer boss es mucho más fácil
        val isFirstBoss = _state.value.currentBiomeIndex == 0
        val baseDrop = if (isFirstBoss) 0.35f else 0.6f
        val dropSpeed = baseDrop + (_state.value.currentBiomeIndex * 0.4f)
        
        tensionValue -= dropSpeed
        tensionValue += (Math.random().toFloat() - 0.5f) * 1.5f

        // Margen más amplio para el primer boss (30..90 vs 40..80)
        val range = if (isFirstBoss) 30f..90f else 40f..80f
        
        if (tensionValue in range) {
            val damage = 0.5f * (1 + (_state.value.upgLevels["steering"] ?: 0) * 0.2f)
            bossStamina -= damage
        }

        if (tensionValue >= 100f) {
            showToast("💥 ¡LÍNEA ROTA!")
            finishRound()
        } else if (tensionValue <= 0f) {
            showToast("💨 ¡SE ESCAPÓ!")
            finishRound()
        } else if (bossStamina <= 0f) {
            winBossFight()
        }

        _state.update { it.copy(bossHealth = bossStamina, bossTension = tensionValue) }
    }

    private fun winBossFight() {
        val biome = GameConfig.biomes[_state.value.currentBiomeIndex]
        val reward = GameConfig.bosses[biome.bossName]?.reward ?: 5000L
        _state.update { it.copy(
            gamePhase = "REELING",
            bossActive = false,
            score = it.score + reward,
            totalLifetimeScore = it.totalLifetimeScore + reward,
            nextBiomeUnlocked = true,
            maxBiomeReached = (_state.value.maxBiomeReached).coerceAtLeast(_state.value.currentBiomeIndex + 1)
        ) }
        showToast("🏆 ¡${biome.bossName} CAPTURADO! +$reward")
        saveUserData()
    }

    fun launchHook(startX: Float) {
        if (_state.value.gamePhase == "MENU") {
            _state.update { it.copy(
                gamePhase = "FISHING",
                hookX = startX,
                hookXTarget = startX,
                hookY = surfaceY - 50f,
                currentKg = 0f,
                weightFull = false,
                camYTarget = 0f
            ) }
        }
    }

    fun changeBiome(index: Int) {
        if (index <= _state.value.maxBiomeReached) {
            _state.update { it.copy(currentBiomeIndex = index, showMapSelector = false) }
            spawnFishForCurrentDepth()
            saveUserData()
        }
    }

    fun toggleCollection(show: Boolean) { _state.update { it.copy(showCollection = show) } }
    fun toggleMapSelector(show: Boolean) { _state.update { it.copy(showMapSelector = show) } }

    private fun finishRound() {
        _state.update { it.copy(gamePhase = "MENU", bossActive = false, camYTarget = 0f) }
        spawnFishForCurrentDepth()
        checkBiomeUnlocked()
        saveUserData()
    }

    private fun checkBiomeUnlocked() {
        val currentState = _state.value
        val nextIndex = currentState.currentBiomeIndex + 1
        if (nextIndex < GameConfig.biomes.size && currentState.nextBiomeUnlocked) {
            _state.update { it.copy(
                currentBiomeIndex = nextIndex, 
                nextBiomeUnlocked = false,
                maxBiomeReached = nextIndex.coerceAtLeast(currentState.maxBiomeReached)
            ) }
            showToast("🌎 NUEVO BIOMA: ${GameConfig.biomes[nextIndex].name}")
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
            } else {
                showToast("❌ No tienes puntos suficientes")
            }
        }
    }

    private fun updateActiveStats() {
        val levels = _state.value.upgLevels
        activeMaxDepth = GameConfig.upgrades["depth"]?.values?.get(levels["depth"] ?: 0) ?: 40f
        activeMaxKg = GameConfig.upgrades["weight"]?.values?.get(levels["weight"] ?: 0) ?: 15f
        activePtsMult = GameConfig.upgrades["pts"]?.values?.get(levels["pts"] ?: 0) ?: 1f
        steeringPower = GameConfig.upgrades["steering"]?.values?.get(levels["steering"] ?: 0) ?: 0.05f
    }

    private fun saveUserData() {
        viewModelScope.launch { repository.saveProgress(_state.value) }
    }

    private fun showToast(msg: String) {
        toastJob?.cancel()
        _state.update { it.copy(toastMessage = msg) }
        toastJob = viewModelScope.launch { delay(2000); _state.update { it.copy(toastMessage = null) } }
    }
}
