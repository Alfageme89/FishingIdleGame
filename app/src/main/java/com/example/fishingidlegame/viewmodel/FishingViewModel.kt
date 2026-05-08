package com.example.fishingidlegame.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fishingidlegame.config.*
import com.example.fishingidlegame.model.*
import com.example.fishingidlegame.data.GameRepository
import com.example.fishingidlegame.data.FirebaseManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.math.*
import kotlin.random.Random

class FishingViewModel(private val repository: GameRepository) : ViewModel() {

    // ---------------- STATE ----------------
    private val _state = MutableStateFlow(GameState())
    val state = _state.asStateFlow()

    private val _fishList = MutableStateFlow<List<Fish>>(emptyList())
    val fishList = _fishList.asStateFlow()

    private val _powerUps = MutableStateFlow<List<PowerUp>>(emptyList())
    val powerUps = _powerUps.asStateFlow()

    // ---------------- FIREBASE ----------------
    private val firebaseManager = FirebaseManager()

    private val pendingFishWrites = mutableListOf<Pair<String, Float>>()
    private var pendingSync = false

    private var firebaseJob: Job? = null
    private var lastFirebaseFlush = 0L

    // ---------------- RANKING ----------------
    private val _rankingJugadores = MutableStateFlow<List<RankingJugador>>(emptyList())
    val rankingJugadores = _rankingJugadores.asStateFlow()

    private val _rankingPeces = MutableStateFlow<List<RankingPez>>(emptyList())
    val rankingPeces = _rankingPeces.asStateFlow()

    private val _rankingLoading = MutableStateFlow(false)
    val rankingLoading = _rankingLoading.asStateFlow()

    // ---------------- GAME VALUES ----------------
    private val surfaceY = 400f
    private val worldWidth = 1080f

    private var activeMaxDepth = 40f
    private var activeMaxKg = 5f
    private var steeringPower = 0.04f
    private var turboMult = 1.0f
    private var bossStability = 1.0f
    private var baitPower = 1.0f

    private var bossTensionDir = 1
    private val bossTensionSpeed = 1.5f
    private var bossTriggeredForCurrentDive = false
    private var bossWarningFrames = 0

    // ---------------- INIT ----------------
    init {
        loadUserData()
        startGameLoop()
        startPeriodicSave()
    }

    // ---------------- LOAD ----------------
    private fun loadUserData() {
        viewModelScope.launch {
            val saved = repository.loadProgress()
            _state.update { saved.copy(gamePhase = "MENU", hookY = 0f) }
            updateActiveStats()
            spawnWorldElements()
        }
    }

    // ---------------- FIREBASE SYNC ----------------
    private fun scheduleFirebaseSync(force: Boolean = false) {
        val now = System.currentTimeMillis()

        if (!force && now - lastFirebaseFlush < 2000) return

        firebaseJob?.cancel()

        firebaseJob = viewModelScope.launch {
            delay(1500)

            val user = repository.getCurrentUser()
            val username = repository.getCurrentUsername()
            val currentState = _state.value

            val fishBatch = pendingFishWrites.toList()
            pendingFishWrites.clear()
            pendingSync = false

            runCatching {
                firebaseManager.guardarPuntuacion(
                    user, username, currentState.score,
                    currentState.prestigeLevel, currentState.prestigeMultiplier
                )
                firebaseManager.guardarProgresoCompleto(user, username, currentState)
                fishBatch.forEach { (type, kg) ->
                    firebaseManager.guardarPesoPez(user, username, type, kg)
                }
            }

            lastFirebaseFlush = System.currentTimeMillis()
        }
    }

    // ---------------- SCORE ADD ----------------
    fun addPoints(amount: Long) {
        _state.update {
            it.copy(
                score = it.score + amount,
                totalLifetimeScore = it.totalLifetimeScore + amount
            )
        }

        pendingSync = true
        scheduleFirebaseSync()
    }

    // ---------------- COLLISIONS FIXED ----------------
    private fun checkCollisions() {
        val current = _state.value
        val magnetActive = (current.activePowerUps[PowerUpType.MAGNET] ?: 0L) > System.currentTimeMillis()
        val catchRadius = if (magnetActive) 160f else 60f * baitPower

        _fishList.update { list ->
            list.map { fish ->
                if (fish.isCaught) return@map fish

                val dx = fish.x - current.hookX
                val dy = fish.y - (current.hookY + 20f)
                val dist = sqrt(dx * dx + dy * dy)

                if (dist < catchRadius) {

                    val newKg = current.currentKg + fish.kg

                    val shieldActive = (current.activePowerUps[PowerUpType.SHIELD] ?: 0L) > System.currentTimeMillis()
                if (newKg <= activeMaxKg || shieldActive) {

                        val earned = fish.pts.toLong()

                        val newSpecies = current.caughtSpecies + fish.type.name
                        val newCounts = current.speciesCounts.toMutableMap().apply {
                            put(fish.type.name, (get(fish.type.name) ?: 0) + 1)
                        }
                        val prevMaxKg = current.maxWeights[fish.type.name] ?: 0f
                        val isNewMax = fish.kg > prevMaxKg
                        val newMaxWeights = if (isNewMax) {
                            current.maxWeights.toMutableMap().apply { put(fish.type.name, fish.kg) }
                        } else {
                            current.maxWeights
                        }

                        _state.update {
                            it.copy(
                                currentKg = newKg,
                                score = it.score + earned,
                                totalLifetimeScore = it.totalLifetimeScore + earned,
                                totalFishCaught = it.totalFishCaught + 1,
                                caughtSpecies = newSpecies,
                                speciesCounts = newCounts,
                                maxWeights = newMaxWeights,
                                toastMessage = "+${earned} pts  •  +${String.format("%.1f", fish.kg)} kg"
                            )
                        }
                        viewModelScope.launch {
                            delay(2000)
                            _state.update { s -> s.copy(toastMessage = null) }
                        }

                        if (isNewMax) {
                            pendingFishWrites.add(fish.type.name to fish.kg)
                        }
                        pendingSync = true
                        scheduleFirebaseSync()

                        return@map fish.copy(isCaught = true)
                    }
                }

                fish
            }
        }
    }

    // ---------------- GAME LOOP ----------------
    private fun startGameLoop() {
        viewModelScope.launch {
            while (true) {
                updateGame()
                delay(16)
            }
        }
    }

    private fun updateGame() {
        // Clean expired power-ups every frame
        val now = System.currentTimeMillis()
        val expired = _state.value.activePowerUps.filter { (_, expiry) -> expiry <= now }.keys
        if (expired.isNotEmpty()) {
            _state.update { it.copy(activePowerUps = it.activePowerUps - expired) }
        }

        val state = _state.value
        val newCamY = state.camY + (state.camYTarget - state.camY) * 0.15f

        // Boss phases: update and return early (hook/fish don't move)
        when (state.gamePhase) {
            "BOSS_WARNING" -> {
                bossWarningFrames++
                val countdown = (3 - bossWarningFrames / 60).coerceAtLeast(0)
                if (bossWarningFrames >= 180) {
                    bossWarningFrames = 0
                    _state.update { it.copy(camY = newCamY, gamePhase = "BOSS_FIGHT", bossActive = true, bossCountdown = 0) }
                } else {
                    _state.update { it.copy(camY = newCamY, bossCountdown = countdown) }
                }
                return
            }
            "BOSS_FIGHT" -> {
                if (state.bossHealth <= 0f) {
                    val biome = GameConfig.biomes[state.currentBiomeIndex]
                    val reward = GameConfig.bosses[biome.bossName]?.reward ?: 500L
                    _state.update { s ->
                        s.copy(
                            camY = newCamY,
                            score = s.score + reward,
                            totalLifetimeScore = s.totalLifetimeScore + reward,
                            gamePhase = "REELING",
                            bossActive = false,
                            maxBiomeReached = minOf(s.maxBiomeReached + 1, GameConfig.biomes.size - 1),
                            toastMessage = "¡Jefe derrotado! +${reward} pts"
                        )
                    }
                    viewModelScope.launch { delay(2500); _state.update { it.copy(toastMessage = null) } }
                    pendingSync = true
                    scheduleFirebaseSync()
                } else {
                    val dir = when {
                        state.bossTension >= 95f -> -1
                        state.bossTension <= 5f -> 1
                        else -> bossTensionDir
                    }
                    bossTensionDir = dir
                    val newTension = (state.bossTension + dir * bossTensionSpeed).coerceIn(0f, 100f)
                    val newSafe = (state.bossSafeZonePos + (Random.nextFloat() - 0.5f) * 0.3f).coerceIn(10f, 90f)
                    _state.update { it.copy(camY = newCamY, bossTension = newTension, bossSafeZonePos = newSafe) }
                }
                return
            }
        }

        // Hook physics (FISHING / REELING / MENU)
        val speedPowerActive = (state.activePowerUps[PowerUpType.SPEED] ?: 0L) > now
        val speedMult = when {
            state.isTurbo && speedPowerActive -> turboMult * 2f
            state.isTurbo -> turboMult
            speedPowerActive -> 2f
            else -> 1f
        }
        val newHookX = (state.hookX + (state.hookXTarget - state.hookX) * (steeringPower * speedMult).coerceAtMost(1f))
            .coerceIn(0f, worldWidth)

        val maxHookY = surfaceY + activeMaxDepth * GameConfig.M2PX_BASE
        var newHookY = state.hookY
        var newGamePhase = state.gamePhase
        var newCurrentKg = state.currentKg
        var newCamYTarget = state.camYTarget
        var newBossHealth = state.bossHealth
        var newBossMaxHealth = state.bossMaxHealth
        var newBossCountdown = state.bossCountdown

        when (state.gamePhase) {
            "FISHING" -> {
                newHookY = (state.hookY + GameConfig.CAST_SPEED * speedMult).coerceAtMost(maxHookY)
                newCamYTarget = (newHookY - 700f).coerceAtLeast(0f)
                val biomeForBoss = GameConfig.biomes[state.currentBiomeIndex]
                val bossForTrigger = GameConfig.bosses[biomeForBoss.bossName]
                val bossTriggerY = if (bossForTrigger != null)
                    surfaceY + bossForTrigger.triggerDepthM * GameConfig.M2PX_BASE
                else
                    maxHookY
                if (!bossTriggeredForCurrentDive && newHookY >= bossTriggerY) {
                    bossTriggeredForCurrentDive = true
                    if (bossForTrigger != null) {
                        newGamePhase = "BOSS_WARNING"
                        newBossHealth = bossForTrigger.maxHealth
                        newBossMaxHealth = bossForTrigger.maxHealth
                        newBossCountdown = 3
                        bossWarningFrames = 0
                    } else if (newHookY >= maxHookY || state.currentKg >= activeMaxKg) {
                        newGamePhase = "REELING"
                    }
                } else if (newHookY >= maxHookY || state.currentKg >= activeMaxKg) {
                    newGamePhase = "REELING"
                }
            }
            "REELING" -> {
                val baseSpeed = if (state.currentKg >= activeMaxKg) GameConfig.REEL_SPEED_FULL else GameConfig.REEL_SPEED_BASE
                val reelingSpeed = baseSpeed * speedMult
                newHookY = (state.hookY - reelingSpeed).coerceAtLeast(surfaceY)
                newCamYTarget = (newHookY - 700f).coerceAtLeast(0f)
                if (newHookY <= surfaceY) {
                    newGamePhase = "MENU"
                    newCurrentKg = 0f
                    newCamYTarget = 0f
                    bossTriggeredForCurrentDive = false
                }
            }
        }

        _state.update {
            it.copy(
                camY = newCamY,
                camYTarget = newCamYTarget,
                hookX = newHookX,
                hookY = newHookY,
                gamePhase = newGamePhase,
                currentKg = newCurrentKg,
                bossHealth = newBossHealth,
                bossMaxHealth = newBossMaxHealth,
                bossCountdown = newBossCountdown
            )
        }

        // Viewport-based fish: cull off-screen, move alive, spawn in visible zone
        val viewBottom = newCamY + 2200f
        val cullAbove = newCamY - 300f
        val cullBelow = viewBottom + 600f   // remove fish that fell far below viewport
        // Spawn zone: below hook position so fish appear ahead, not behind
        val spawnTop = maxOf(surfaceY + 20f, newHookY + 200f)
        val spawnBottom = minOf(spawnTop + 1400f, viewBottom)

        _fishList.update { list ->
            list.mapNotNull { fish ->
                if (fish.isCaught) {
                    if (fish.y < cullAbove) null else fish
                } else {
                    if (fish.y < cullAbove || fish.y > cullBelow) null
                    else {
                        var nx = fish.x + fish.vx
                        var ny = fish.y + fish.vy
                        var nvx = fish.vx
                        var nvy = fish.vy
                        if (nx < 0f || nx > worldWidth) { nvx = -nvx; nx = nx.coerceIn(0f, worldWidth) }
                        if (ny < surfaceY + 20f) { nvy = -nvy; ny = surfaceY + 20f }
                        if (ny > viewBottom + 200f) { nvy = -nvy; ny = viewBottom + 200f }
                        fish.copy(x = nx, y = ny, vx = nvx, vy = nvy)
                    }
                }
            }
        }

        val aliveCount = _fishList.value.count { !it.isCaught }
        if (aliveCount < 12 && spawnTop < spawnBottom) {
            val toSpawn = 25 - aliveCount
            _fishList.update { list -> list + List(toSpawn) { createRandomFishInZone(spawnTop, spawnBottom) } }
        }

        checkCollisions()
    }

    // ---------------- WORLD ----------------
    private fun spawnWorldElements() {
        val spawnTop = surfaceY + 800f
        val spawnBottom = surfaceY + 2200f
        _fishList.value = List(25) { createRandomFishInZone(spawnTop, spawnBottom) }
    }

    private fun createRandomFishInZone(viewTop: Float, viewBottom: Float): Fish {
        val biome = GameConfig.biomes[_state.value.currentBiomeIndex]
        val fishType = biome.fishTypeNames.random()
            .let { GameConfig.fishTypes[it] ?: GameConfig.fishTypes.values.random() }
        val tier = FishTier.values().random()
        val spawnY = if (viewTop.toInt() < viewBottom.toInt())
            (viewTop.toInt()..viewBottom.toInt()).random().toFloat()
        else viewTop

        return Fish(
            type = fishType,
            tier = tier,
            x = (0..worldWidth.toInt()).random().toFloat(),
            y = spawnY,
            vx = (Random.nextFloat() - 0.5f) * 2f,
            vy = (Random.nextFloat() - 0.5f) * 2f,
            kg = fishType.baseKg * tier.weightMult,
            pts = (fishType.basePoints * tier.scoreMult).toInt(),
            multiplier = _state.value.prestigeMultiplier,
            isRare = Random.nextFloat() < fishType.rarity * 0.1f
        )
    }

    // ---------------- STATS ----------------
    private fun updateActiveStats() {
        val lvls = _state.value.upgLevels

        fun get(k: String, def: Float): Float {
            val u = GameConfig.upgrades[k] ?: return def
            val l = lvls[k] ?: 0
            return u.values.getOrElse(l) { u.values.last() }
        }

        activeMaxDepth = get("depth", 40f)
        activeMaxKg = get("weight", 30f)
        steeringPower = get("steering", 0.04f)
        turboMult = get("turbo", 1f)
        bossStability = get("boss", 1f)
        baitPower = get("bait", 1f)
    }

    // ---------------- RANKING LOAD ----------------
    fun cargarRankingJugadores() {
        viewModelScope.launch {
            _rankingLoading.value = true
            _rankingJugadores.value = firebaseManager.obtenerRankingPuntuaciones()
            _rankingLoading.value = false
        }
    }

    fun cargarRankingPorPez(fishName: String) {
        viewModelScope.launch {
            _rankingLoading.value = true
            _rankingPeces.value = firebaseManager.obtenerRankingPorPez(fishName)
            _rankingLoading.value = false
        }
    }

    // ---------------- SAVE ----------------
    fun saveUserData() {
        viewModelScope.launch {
            repository.saveProgress(_state.value)
        }
        scheduleFirebaseSync(force = true)
    }

    // ---------------- PERIODIC SAVE ----------------
    private fun startPeriodicSave() {
        viewModelScope.launch {
            while (true) {
                delay(30_000)
                repository.saveProgress(_state.value)
                if (pendingSync) {
                    scheduleFirebaseSync(force = true)
                }
            }
        }
    }

    // ---------------- UI CALLBACKS ----------------
    fun switchUser(email: String, firebaseId: String, username: String) {
        repository.setUser(email, firebaseId, username)
        loadUserData()
    }

    fun onPlayerTap() {
        val state = _state.value
        if (state.gamePhase == "BOSS_FIGHT" && state.bossActive) {
            val tension = state.bossTension
            val safeZone = state.bossSafeZonePos
            if (kotlin.math.abs(tension - safeZone) < 15f) {
                val damage = 10f * bossStability * baitPower
                _state.update { it.copy(bossHealth = (it.bossHealth - damage).coerceAtLeast(0f)) }
            }
        }
    }

    fun setHookTarget(x: Float) {
        val constrainedX = x.coerceIn(0f, worldWidth)
        _state.update { it.copy(hookXTarget = constrainedX) }
    }

    fun setTurbo(active: Boolean) {
        _state.update { it.copy(isTurbo = active) }
        if (active) {
            // Turbo mechanics can be added here
        }
    }

    fun forceReel() {
        val state = _state.value
        if (state.gamePhase == "FISHING" || state.gamePhase == "REELING") {
            _state.update { it.copy(gamePhase = "REELING") }
        }
    }

    fun toggleCollection(show: Boolean) {
        _state.update { it.copy(showCollection = show) }
    }

    fun changeBiome(biomeIndex: Int) {
        val maxUnlocked = _state.value.maxBiomeReached
        if (biomeIndex <= maxUnlocked) {
            _state.update { it.copy(currentBiomeIndex = biomeIndex, showMapSelector = false) }
            spawnWorldElements()
        }
        pendingSync = true
        scheduleFirebaseSync()
    }

    fun toggleMapSelector(show: Boolean) {
        _state.update { it.copy(showMapSelector = show) }
    }

    val prestigeMinScore: Long get() {
        val level = _state.value.prestigeLevel
        return when (level) {
            0 -> 10_000L
            1 -> 50_000L
            2 -> 200_000L
            else -> 1_000_000L
        }
    }

    fun canPrestige(): Boolean = _state.value.score >= prestigeMinScore

    fun confirmPrestige() {
        val state = _state.value
        if (state.score < prestigeMinScore) return
        val newLevel = state.prestigeLevel + 1
        val newMultiplier = state.prestigeMultiplier * 1.05f

        _state.update {
            it.copy(
                prestigeLevel = newLevel,
                prestigeMultiplier = newMultiplier,
                score = 0,
                currentKg = 0f,
                totalFishCaught = 0,
                gamePhase = "MENU",
                showPrestigeConfirm = false,
                shopPriceMultipliers = emptyMap(),
                currentBiomeIndex = 0,
                maxBiomeReached = 0,
                upgLevels = mapOf(
                    "depth" to 0, "weight" to 0, "steering" to 0,
                    "turbo" to 0, "boss" to 0, "bait" to 0
                )
            )
        }
        updateActiveStats()
        pendingSync = true
        scheduleFirebaseSync(force = true)
    }

    fun closePrestigeConfirm() {
        _state.update { it.copy(showPrestigeConfirm = false) }
    }

    fun toggleShop(show: Boolean) {
        _state.update { it.copy(showShop = show) }
    }

    fun buyConsumable(type: PowerUpType, baseCost: Long) {
        val state = _state.value
        val key = type.name
        val mult = state.shopPriceMultipliers[key] ?: 1f
        val actualCost = (baseCost * mult).toLong()
        if (state.score >= actualCost) {
            val newMult = mult * 1.5f
            val newMultipliers = state.shopPriceMultipliers.toMutableMap().apply { put(key, newMult) }
            _state.update { it.copy(score = it.score - actualCost, shopPriceMultipliers = newMultipliers) }
            val expiry = System.currentTimeMillis() + 15000L
            when (type) {
                PowerUpType.SPEED  -> _state.update { it.copy(activePowerUps = it.activePowerUps + (PowerUpType.SPEED  to expiry)) }
                PowerUpType.MAGNET -> _state.update { it.copy(activePowerUps = it.activePowerUps + (PowerUpType.MAGNET to expiry)) }
                PowerUpType.SHIELD -> _state.update { it.copy(activePowerUps = it.activePowerUps + (PowerUpType.SHIELD to expiry)) }
                PowerUpType.GOLD   -> addPoints((actualCost * 0.25f).toLong())
            }
            pendingSync = true
            scheduleFirebaseSync()
        }
    }

    fun confirmReset() {
        _state.update {
            it.copy(
                score = 0,
                totalFishCaught = 0,
                currentKg = 0f,
                upgLevels = mapOf(
                    "depth" to 0,
                    "weight" to 0,
                    "steering" to 0,
                    "turbo" to 0,
                    "boss" to 0,
                    "bait" to 0
                ),
                caughtSpecies = emptySet(),
                speciesCounts = emptyMap(),
                maxWeights = emptyMap(),
                gamePhase = "FISHING",
                showResetConfirm = false,
                shopPriceMultipliers = emptyMap()
            )
        }
        pendingSync = true
        scheduleFirebaseSync(force = true)
    }

    fun cancelReset() {
        _state.update { it.copy(showResetConfirm = false) }
    }

    fun launchHook(targetY: Float) {
        val state = _state.value
        if (state.gamePhase == "MENU" || state.gamePhase == "FISHING") {
            bossTriggeredForCurrentDive = false
            _state.update { it.copy(gamePhase = "FISHING", hookY = surfaceY) }
        }
    }

    fun toggleSettings(show: Boolean) {
        _state.update { it.copy(showSettings = show) }
    }

    fun buyUpgrade(upgradeId: String) {
        val state = _state.value
        val upgrade = GameConfig.upgrades[upgradeId] ?: return
        val currentLevel = state.upgLevels[upgradeId] ?: 0
        val cost = upgrade.levels.getOrElse(currentLevel) { -1L }.toLong()

        if (cost != -1L && state.score >= cost) {
            _state.update {
                it.copy(
                    score = it.score - cost,
                    upgLevels = it.upgLevels.toMutableMap().apply { put(upgradeId, currentLevel + 1) }
                )
            }
            updateActiveStats()
            pendingSync = true
            scheduleFirebaseSync()
        }
    }

    fun toggleMusic() {
        _state.update { it.copy(musicEnabled = !it.musicEnabled) }
    }

    fun toggleSFX() {
        _state.update { it.copy(sfxEnabled = !it.sfxEnabled) }
    }

    fun requestReset() {
        _state.update { it.copy(showResetConfirm = true) }
    }

    fun requestPrestige() {
        _state.update { it.copy(showPrestigeConfirm = true) }
    }
}

fun formatPoints(points: Long): String {
    return when {
        points >= 1_000_000_000 -> String.format("%.2fB", points / 1_000_000_000.0)
        points >= 1_000_000 -> String.format("%.2fM", points / 1_000_000.0)
        points >= 1_000 -> String.format("%.2fK", points / 1_000.0)
        else -> points.toString()
    }
}