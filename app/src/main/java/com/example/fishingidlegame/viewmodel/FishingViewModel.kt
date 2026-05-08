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
import java.util.*

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

    private var pendingScore = 0L
    private val pendingFishWrites = mutableListOf<Pair<String, Float>>()
    private var pendingScoreWrite = false

    private var firebaseJob: Job? = null
    private var lastFirebaseFlush = 0L

    // ---------------- GAME VALUES ----------------
    private val surfaceY = 400f
    private val worldWidth = 1080f

    private var activeMaxDepth = 40f
    private var activeMaxKg = 5f
    private var steeringPower = 0.04f
    private var turboMult = 1.0f
    private var bossStability = 1.0f
    private var baitPower = 1.0f

    // ---------------- INIT ----------------
    init {
        loadUserData()
        startGameLoop()
    }

    // ---------------- LOAD ----------------
    private fun loadUserData() {
        viewModelScope.launch {
            val saved = repository.loadProgress()
            _state.update { saved }
            updateActiveStats()
            spawnWorldElements()
        }
    }

    // ---------------- FIREBASE SYNC (ÚNICO SISTEMA) ----------------
    private fun scheduleFirebaseSync(force: Boolean = false) {
        val now = System.currentTimeMillis()

        if (!force && now - lastFirebaseFlush < 2000) return

        firebaseJob?.cancel()

        firebaseJob = viewModelScope.launch {
            delay(1500)

            val user = repository.getCurrentUser()
            val username = repository.getCurrentUsername()

            val score = pendingScore
            val fishBatch = pendingFishWrites.toList()

            pendingScore = 0L
            pendingFishWrites.clear()

            if (score > 0) {
                firebaseManager.guardarPuntuacion(user, username, score)
            }

            fishBatch.forEach { (type, kg) ->
                firebaseManager.guardarPesoPez(user, username, type, kg)
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

        pendingScore += amount
        pendingScoreWrite = true
        scheduleFirebaseSync()
    }

    // ---------------- COLLISIONS FIXED ----------------
    private fun checkCollisions() {
        val current = _state.value
        val catchRadius = if (current.activePowerUps.containsKey(PowerUpType.MAGNET)) 160f else 60f * baitPower

        _fishList.update { list ->
            list.map { fish ->
                if (fish.isCaught) return@map fish

                val dx = fish.x - current.hookX
                val dy = fish.y - (current.hookY + 20f)
                val dist = sqrt(dx * dx + dy * dy)

                if (dist < catchRadius) {

                    val newKg = current.currentKg + fish.kg

                    if (newKg <= activeMaxKg || current.activePowerUps.containsKey(PowerUpType.SHIELD)) {

                        val earned = fish.pts.toLong()

                        val newSpecies = current.caughtSpecies + fish.type.name
                        val newCounts = current.speciesCounts.toMutableMap().apply {
                            put(fish.type.name, (get(fish.type.name) ?: 0) + 1)
                        }

                        _state.update {
                            it.copy(
                                currentKg = newKg,
                                score = it.score + earned,
                                totalLifetimeScore = it.totalLifetimeScore + earned,
                                totalFishCaught = it.totalFishCaught + 1,
                                caughtSpecies = newSpecies,
                                speciesCounts = newCounts
                            )
                        }

                        // QUEUE FIREBASE (CORRECTO)
                        pendingScore += earned
                        pendingFishWrites.add(fish.type.name to fish.kg)
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
        val state = _state.value
        val newCamY = state.camY + (state.camYTarget - state.camY) * 0.15f

        _state.update { it.copy(camY = newCamY) }
    }

    // ---------------- WORLD ----------------
    private fun spawnWorldElements() {
        val totalFish = 100

        _fishList.value = List(totalFish) {
            Fish(
                type = GameConfig.fishTypes.values.random(),
                tier = FishTier.SMALL,
                x = (0..worldWidth.toInt()).random().toFloat(),
                y = (surfaceY.toInt()..2000).random().toFloat(),
                vx = 0f,
                vy = 0f,
                kg = 1f,
                pts = 10,
                multiplier = 1f,
                isRare = false
            )
        }
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
        activeMaxKg = get("weight", 5f)
        steeringPower = get("steering", 0.04f)
        turboMult = get("turbo", 1f)
        bossStability = get("boss", 1f)
        baitPower = get("bait", 1f)
    }

    // ---------------- SAVE ----------------
    private fun saveUserData() {
        viewModelScope.launch {
            repository.saveProgress(_state.value)
        }
    }
}