package com.example.fishingidlegame.model

import androidx.compose.ui.graphics.Color

data class Biome(
    val name: String,
    val skyColor: Color,
    val waterColorTop: Color,
    val waterColorBottom: Color,
    val minDepthRequired: Float,
    val fishTypeNames: List<String>,
    val bossName: String
)

data class Boss(
    val name: String,
    val color: Color,
    val maxHealth: Float,
    val size: Float,
    val reward: Long
)

data class FishType(
    val name: String,
    val color: Color,
    val basePoints: Int,
    val width: Float,
    val height: Float,
    val baseKg: Float
)

data class Fish(
    val type: FishType,
    val id: Double = Math.random(),
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var kg: Float,
    var pts: Int,
    var multiplier: Float,
    var isCaught: Boolean = false
)

data class GameState(
    val score: Long = 0,
    val totalFishCaught: Int = 0,
    val currentKg: Float = 0f,
    val upgLevels: Map<String, Int> = mapOf(
        "depth" to 0, 
        "weight" to 0, 
        "pts" to 0, 
        "crew" to 0,
        "steering" to 0
    ),
    val gamePhase: String = "MENU", 
    val hookX: Float = 540f,
    val hookXTarget: Float = 540f,
    val hookY: Float = 0f,
    val camY: Float = 0f,
    val camYTarget: Float = 0f,
    val weightFull: Boolean = false,
    val toastMessage: String? = null,
    
    val currentBiomeIndex: Int = 0,
    val maxBiomeReached: Int = 0,
    val caughtSpecies: Set<String> = emptySet(),
    val speciesCounts: Map<String, Int> = emptyMap(),
    
    val prestigeMultiplier: Float = 1.0f,
    val totalLifetimeScore: Long = 0,
    val lastTimestamp: Long = System.currentTimeMillis(),
    
    val bossActive: Boolean = false,
    val bossHealth: Float = 0f,
    val bossMaxHealth: Float = 100f,
    val bossTension: Float = 50f,
    val nextBiomeUnlocked: Boolean = false,
    
    val showCollection: Boolean = false,
    val showMapSelector: Boolean = false
)
