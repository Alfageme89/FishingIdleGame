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
import kotlin.math.roundToInt
import kotlin.math.pow
import java.util.*

class FishingViewModel(private val repository: GameRepository) : ViewModel() {

    private val _state = MutableStateFlow(GameState())
    val state = _state.asStateFlow()

    private val _fishList = MutableStateFlow<List<Fish>>(emptyList())
    val fishList = _fishList.asStateFlow()

    private val _powerUps = MutableStateFlow<List<PowerUp>>(emptyList())
    val powerUps = _powerUps.asStateFlow()

    private var toastJob: Job? = null
    private var activeMaxDepth = 40f
    private var activeMaxKg = 5f
    private var steeringPower = 0.04f
    private var turboMult = 1.0f
    private var bossStability = 1.0f
    private var baitPower = 1.0f

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
            spawnWorldElements()
        }
    }

    fun requestPrestige() {
        val currentState = _state.value
        val currentPrestigeLevel = ((currentState.prestigeMultiplier - 1f) / 0.15f).roundToInt()
        val requiredScore = (100000 * (currentPrestigeLevel + 1).toDouble().pow(1.5)).toLong()
        
        val canPrestige = currentState.maxBiomeReached >= 1 || currentState.score >= requiredScore
        
        if (canPrestige) {
            _state.update { it.copy(showPrestigeConfirm = true) }
        } else {
            showToast("🔒 Necesitas ${formatPoints(requiredScore)} pts para el siguiente nivel")
        }
    }

    fun confirmPrestige() {
        viewModelScope.launch {
            val currentState = _state.value
            val biomeBonus = currentState.maxBiomeReached * 0.05f
            val speciesBonus = (currentState.caughtSpecies.size / 2) * 0.01f
            val calculatedIncrement = 0.15f + biomeBonus + speciesBonus
            
            val newPrestige = currentState.prestigeMultiplier + calculatedIncrement
            
            val newState = GameState(
                prestigeMultiplier = newPrestige,
                prestigeLevel = currentState.prestigeLevel + 1,
                maxBiomeReached = currentState.maxBiomeReached,
                caughtSpecies = currentState.caughtSpecies,
                speciesCounts = currentState.speciesCounts,
                maxWeights = currentState.maxWeights,
                score = 0,
                upgLevels = mapOf(
                    "depth" to 0, "weight" to 0, "steering" to 0,
                    "turbo" to 0, "boss" to 0, "bait" to 0 
                ),
                musicEnabled = currentState.musicEnabled,
                sfxEnabled = currentState.sfxEnabled
            )
            
            repository.saveProgress(newState)
            _state.update { newState }
            updateActiveStats()
            spawnWorldElements()
            showToast("🚀 EXPEDICIÓN MEJORADA: +${(calculatedIncrement * 100).roundToInt()}% BONO")
        }
    }

    private fun spawnWorldElements() {
        val currentState = _state.value
        val currentMaxY = surfaceY + (activeMaxDepth * GameConfig.M2PX_BASE)
        val numFish = ((activeMaxDepth / 100f) * GameConfig.NUM_FISH_PER_100M).toInt().coerceAtLeast(25)

        val biomeLimit = surfaceY + ((currentState.currentBiomeIndex + 1) * 300 * GameConfig.M2PX_BASE)
        val bossNoFishZone = 500f

        _fishList.update { 
            List(numFish) { 
                val fish = createRandomFish(activeMaxDepth)
                if (fish.y > biomeLimit - bossNoFishZone) {
                    fish.y -= bossNoFishZone + (50..200).random().toFloat()
                }
                fish
            } 
        }
        _powerUps.update { List(GameConfig.NUM_POWERUPS) { createRandomPowerUp(currentMaxY) } }
    }

    private fun createRandomFish(maxMeters: Float): Fish {
        val availableTypes = GameConfig.fishTypes.values.filter { it.minSpawnDepth <= maxMeters }
        val typeConfig = availableTypes.randomOrNull() ?: GameConfig.fishTypes.values.first()
        
        val roll = Math.random() * baitPower
        val tier = when {
            roll > 1.8 -> FishTier.ELDER
            roll > 1.4 -> FishTier.ADULT
            roll > 0.8 -> FishTier.MEDIUM
            else -> FishTier.SMALL
        }

        val isRare = Math.random() < (typeConfig.rarity * 0.15f)
        val finalKg = typeConfig.baseKg * tier.weightMult * (if(isRare) 2.2f else 1f)
        val finalPts = (typeConfig.basePoints * tier.scoreMult * _state.value.prestigeMultiplier * (if(isRare) 6 else 1)).toInt()

        val spawnMinY = surfaceY + (typeConfig.minSpawnDepth * GameConfig.M2PX_BASE)
        val spawnMaxY = surfaceY + (maxMeters * GameConfig.M2PX_BASE)

        return Fish(
            type = typeConfig,
            tier = tier,
            x = (50..(worldWidth.toInt() - 50)).random().toFloat(),
            y = (spawnMinY.toInt()..spawnMaxY.toInt()).random().toFloat(),
            vx = ((-250..250).random() / 100f),
            vy = ((-80..80).random() / 100f),
            kg = finalKg,
            pts = finalPts,
            multiplier = tier.scoreMult,
            isRare = isRare
        )
    }

    private fun createRandomPowerUp(maxY: Float): PowerUp {
        return PowerUp(
            type = PowerUpType.values().random(),
            x = (100..(worldWidth.toInt() - 100)).random().toFloat(),
            y = (surfaceY.toInt() + 500..maxY.toInt()).random().toFloat()
        )
    }

    private fun startGameLoop() {
        viewModelScope.launch {
            while (true) {
                updateGame()
                updatePowerUpsExpiry()
                delay(16)
            }
        }
    }

    private fun updatePowerUpsExpiry() {
        val now = System.currentTimeMillis()
        val currentActive = _state.value.activePowerUps
        if (currentActive.isEmpty()) return
        val newActive = currentActive.filter { it.value > now }
        if (newActive.size != currentActive.size) {
            _state.update { it.copy(activePowerUps = newActive) }
        }
    }

    private fun updateGame() {
        val currentState = _state.value
        when (currentState.gamePhase) {
            "BOSS_WARNING" -> { }
            "BOSS_FIGHT" -> updateBossFightLogic()
            else -> {
                val newCamY = currentState.camY + (currentState.camYTarget - currentState.camY) * 0.15f
                val steer = if (currentState.isTurbo) steeringPower * 0.5f else steeringPower
                val newHookX = currentState.hookX + (currentState.hookXTarget - currentState.hookX) * steer
                _state.update { it.copy(hookX = newHookX) }
                updateStandardGame(currentState, newCamY)
            }
        }
    }

    private fun updateStandardGame(currentState: GameState, newCamY: Float) {
        val biomeLimit = surfaceY + ((currentState.currentBiomeIndex + 1) * 300 * GameConfig.M2PX_BASE)

        _fishList.update { list ->
            list.map { fish ->
                if (fish.isCaught) {
                    fish.copy(x = currentState.hookX, y = currentState.hookY + 25f)
                } else {
                    var nx = fish.x + fish.vx
                    var ny = fish.y + fish.vy
                    if (nx < 0 || nx > worldWidth) fish.vx *= -1
                    fish.copy(x = nx, y = ny)
                }
            }
        }

        when (currentState.gamePhase) {
            "FISHING" -> {
                val speed = if (currentState.isTurbo) GameConfig.CAST_SPEED * turboMult * 1.5f else GameConfig.CAST_SPEED
                val bonusSpeed = if (currentState.activePowerUps.containsKey(PowerUpType.SPEED)) 2.0f else 1.0f
                val newHookY = currentState.hookY + (speed * bonusSpeed)
                
                var camTarget = (newHookY - 500f).coerceAtLeast(0f)
                val upgradeLimit = surfaceY + (activeMaxDepth * GameConfig.M2PX_BASE)

                if (newHookY >= biomeLimit && !currentState.bossActive) {
                    triggerBossWarning()
                } else if (newHookY >= upgradeLimit) {
                    _state.update { it.copy(gamePhase = "REELING", camYTarget = camTarget, camY = newCamY) }
                } else {
                    _state.update { it.copy(hookY = newHookY, camYTarget = camTarget, camY = newCamY) }
                    checkCollisions()
                }
            }
            "REELING" -> {
                val baseReel = if (currentState.currentKg >= activeMaxKg) GameConfig.REEL_SPEED_FULL else GameConfig.REEL_SPEED_BASE
                val turboSpeed = if (currentState.isTurbo) turboMult * 1.5f else 1.0f
                val bonusSpeed = if (currentState.activePowerUps.containsKey(PowerUpType.SPEED)) 1.5f else 1.0f
                
                val newHookY = currentState.hookY - (baseReel * turboSpeed * bonusSpeed)
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

    private fun triggerBossWarning() {
        viewModelScope.launch {
            _state.update { it.copy(gamePhase = "BOSS_WARNING", bossWarningActive = true, bossCountdown = 3) }
            repeat(3) { i ->
                delay(1000)
                _state.update { it.copy(bossCountdown = 2 - i) }
            }
            startBossFight()
        }
    }

    private fun checkCollisions() {
        val currentState = _state.value
        val hasMagnet = currentState.activePowerUps.containsKey(PowerUpType.MAGNET)
        val hasShield = currentState.activePowerUps.containsKey(PowerUpType.SHIELD)
        val catchRadius = if (hasMagnet) 160f else 60f * baitPower

        _powerUps.update { list ->
            list.filter { pu ->
                val dx = (pu.x - currentState.hookX).toDouble()
                val dy = (pu.y - (currentState.hookY + 20f)).toDouble()
                val dist = sqrt(dx * dx + dy * dy)
                if (dist < 70f) { activatePowerUp(pu.type); false } else true
            }
        }

        _fishList.update { list ->
            list.map { fish ->
                if (!fish.isCaught) {
                    val dx = (fish.x - currentState.hookX).toDouble()
                    val dy = (fish.y - (currentState.hookY + 20f)).toDouble()
                    val dist = sqrt(dx * dx + dy * dy)
                    if (dist < catchRadius) {
                        val newTotalWeight = currentState.currentKg + fish.kg
                        if (newTotalWeight <= activeMaxKg || hasShield) {
                            val isGold = currentState.activePowerUps.containsKey(PowerUpType.GOLD)
                            val earned = (fish.pts * (if(isGold) 2 else 1)).toLong()
                            
                            val newSpecies = currentState.caughtSpecies.toMutableSet().apply { add(fish.type.name) }
                            val newCounts = currentState.speciesCounts.toMutableMap().apply { put(fish.type.name, (get(fish.type.name) ?: 0) + 1) }
                            val newMaxWeights = currentState.maxWeights.toMutableMap().apply { if (fish.kg > (get(fish.type.name) ?: 0f)) put(fish.type.name, fish.kg) }

                            _state.update { it.copy(
                                currentKg = if(hasShield) it.currentKg else newTotalWeight,
                                score = it.score + earned,
                                totalLifetimeScore = it.totalLifetimeScore + earned,
                                totalFishCaught = it.totalFishCaught + 1,
                                caughtSpecies = newSpecies,
                                speciesCounts = newCounts,
                                maxWeights = newMaxWeights
                            ) }
                            
                            showToast("${if(fish.isRare) "✨" else "🐟"} ${fish.type.name} (${fish.tier.label}) ${String.format("%.1f", fish.kg)}kg +${earned}")
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

    private fun activatePowerUp(type: PowerUpType) {
        val duration = 12000L
        val newMap = _state.value.activePowerUps.toMutableMap()
        newMap[type] = System.currentTimeMillis() + duration
        _state.update { it.copy(activePowerUps = newMap) }
        showToast("⚡ POWER-UP: ${type.name}!")
    }

    private fun startBossFight() {
        val biome = GameConfig.biomes[_state.value.currentBiomeIndex]
        val bossConfig = GameConfig.bosses[biome.bossName] ?: return
        tensionValue = 50f
        bossStamina = bossConfig.maxHealth / baitPower
        _state.update { it.copy(
            gamePhase = "BOSS_FIGHT",
            bossActive = true,
            bossHealth = bossStamina,
            bossMaxHealth = bossStamina,
            bossWarningActive = false,
            isTurbo = false
        ) }
    }

    fun onPlayerTap() {
        if (_state.value.gamePhase == "BOSS_FIGHT") {
            tensionValue += 10f * bossStability
        }
    }

    fun setHookTarget(x: Float) { _state.update { it.copy(hookXTarget = x) } }
    fun setTurbo(active: Boolean) { _state.update { it.copy(isTurbo = active) } }
    fun forceReel() { if (_state.value.gamePhase == "FISHING") _state.update { it.copy(gamePhase = "REELING", isTurbo = false) } }
    fun addPoints(amount: Long) { _state.update { it.copy(score = it.score + amount, totalLifetimeScore = it.totalLifetimeScore + amount) } }

    private fun updateBossFightLogic() {
        val baseDrop = 0.5f + (_state.value.currentBiomeIndex * 0.4f)
        val dropSpeed = (baseDrop + (Math.sin(System.currentTimeMillis() / 500.0).toFloat() * 0.5f)) / bossStability
        
        tensionValue -= dropSpeed
        val range = 35f..85f
        if (tensionValue in range) {
            val damage = (0.4f + (_state.value.upgLevels["steering"] ?: 0) * 0.15f) * baitPower
            bossStamina -= damage
        }

        if (tensionValue >= 100f || tensionValue <= 0f) {
            showToast(if(tensionValue >= 100f) "💥 ¡LÍNEA ROTA!" else "💨 ¡SE ESCAPÓ!")
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
            gamePhase = "REELING", bossActive = false, score = it.score + reward,
            totalLifetimeScore = it.totalLifetimeScore + reward, nextBiomeUnlocked = true,
            maxBiomeReached = (_state.value.maxBiomeReached).coerceAtLeast(_state.value.currentBiomeIndex + 1)
        ) }
        showToast("🏆 ¡${biome.bossName} CAPTURADO! +$reward")
        saveUserData()
    }

    fun launchHook(startX: Float) {
        if (_state.value.gamePhase == "MENU") {
            _state.update { it.copy(gamePhase = "FISHING", hookX = startX, hookXTarget = startX, hookY = surfaceY - 50f, currentKg = 0f, weightFull = false, camYTarget = 0f, isTurbo = false, activePowerUps = emptyMap()) }
            spawnWorldElements()
        }
    }

    fun changeBiome(index: Int) {
        if (index <= _state.value.maxBiomeReached) {
            _state.update { it.copy(currentBiomeIndex = index, showMapSelector = false) }
            spawnWorldElements()
            saveUserData()
        }
    }

    fun toggleCollection(show: Boolean) { _state.update { it.copy(showCollection = show) } }
    fun toggleMapSelector(show: Boolean) { _state.update { it.copy(showMapSelector = show) } }
    fun toggleShop(show: Boolean) { _state.update { it.copy(showShop = show) } }
    fun toggleSettings(show: Boolean) { _state.update { it.copy(showSettings = show) } }
    fun closePrestigeConfirm() { _state.update { it.copy(showPrestigeConfirm = false) } }

    fun toggleMusic() { _state.update { it.copy(musicEnabled = !it.musicEnabled) }; saveUserData() }
    fun toggleSFX() { _state.update { it.copy(sfxEnabled = !it.sfxEnabled) }; saveUserData() }
    
    fun requestReset() { _state.update { it.copy(showResetConfirm = true) } }
    fun cancelReset() { _state.update { it.copy(showResetConfirm = false) } }
    fun confirmReset() {
        viewModelScope.launch {
            val newState = GameState()
            repository.saveProgress(newState)
            _state.value = newState
            updateActiveStats()
            spawnWorldElements()
            showToast("⚠️ TODO REINICIADO")
        }
    }

    fun buyConsumable(type: PowerUpType, cost: Long) {
        if (_state.value.score >= cost) {
            _state.update { it.copy(score = it.score - cost) }
            activatePowerUp(type)
            saveUserData()
        } else {
            showToast("💸 No tienes suficientes puntos")
        }
    }

    private fun finishRound() {
        _state.update { it.copy(gamePhase = "MENU", bossActive = false, bossWarningActive = false, camYTarget = 0f, isTurbo = false) }
        spawnWorldElements()
        checkBiomeUnlocked()
        saveUserData()
    }

    private fun checkBiomeUnlocked() {
        val currentState = _state.value
        val nextIndex = currentState.currentBiomeIndex + 1
        if (nextIndex < GameConfig.biomes.size && currentState.nextBiomeUnlocked) {
            _state.update { it.copy(currentBiomeIndex = nextIndex, nextBiomeUnlocked = false, maxBiomeReached = nextIndex.coerceAtLeast(currentState.maxBiomeReached)) }
            showToast("🌎 NUEVO BIOMA: ${GameConfig.biomes[nextIndex].name}")
        }
    }

    fun buyUpgrade(key: String) {
        val upgrade = GameConfig.upgrades[key] ?: return
        val level = _state.value.upgLevels[key] ?: 0
        if (level < upgrade.levels.size) {
            val cost = upgrade.levels[level]
            if (_state.value.score >= cost) {
                _state.update { it.copy(score = it.score - cost, upgLevels = it.upgLevels.toMutableMap().apply { put(key, level + 1) }) }
                updateActiveStats()
                saveUserData()
            }
        }
    }

    private fun updateActiveStats() {
        val lvls = _state.value.upgLevels
        fun gv(k: String, def: Float): Float {
            val u = GameConfig.upgrades[k] ?: return def
            val l = lvls[k] ?: 0
            val values = u.values
            return if (l < values.size) values[l] else values.last()
        }
        activeMaxDepth = gv("depth", 40f)
        activeMaxKg = gv("weight", 5f)
        steeringPower = gv("steering", 0.04f)
        turboMult = gv("turbo", 1.0f)
        bossStability = gv("boss", 1.0f)
        baitPower = gv("bait", 1.0f)
    }

    private fun saveUserData() { viewModelScope.launch { repository.saveProgress(_state.value) } }
    private fun showToast(msg: String) {
        toastJob?.cancel()
        _state.update { it.copy(toastMessage = msg) }
        toastJob = viewModelScope.launch { delay(2000); _state.update { it.copy(toastMessage = null) } }
    }
}

fun formatPoints(pts: Long): String {
    if (pts < 1000) return pts.toString()
    val exp = (Math.log(pts.toDouble()) / Math.log(1000.0)).toInt()
    val suffixes = arrayOf("k", "M", "B", "T", "P", "E")
    val value = pts / Math.pow(1000.0, exp.toDouble())
    return String.format(Locale.getDefault(), "%.2f%s", value, suffixes[exp - 1])
}
