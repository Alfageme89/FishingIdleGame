package com.example.fishingidlegame.model

import androidx.compose.ui.graphics.Color

data class Biome(
    val name: String,
    val description: String,
    val skyColor: Color,
    val waterColorTop: Color,
    val waterColorBottom: Color,
    val minDepthRequired: Float,
    val fishTypeNames: List<String>,
    val bossName: String,
    val imageRes: String = "" 
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
    val baseKg: Float,
    val rarity: Float = 1.0f,
    val rarityTier: String = "Común",
    val description: String = "",
    val minSpawnDepth: Float = 0f,
    val maxSpawnDepth: Float = 10000f
)

enum class FishTier(val label: String, val weightMult: Float, val scoreMult: Float, val color: Color) {
    SMALL("Pequeño", 0.4f, 0.5f, Color(0xFFBDC3C7)),
    MEDIUM("Mediano", 1.0f, 1.0f, Color(0xFF2ECC71)),
    ADULT("Adulto", 2.5f, 3.5f, Color(0xFFF1C40F)),
    ELDER("Anciano", 6.0f, 12.0f, Color(0xFFE74C3C))
}

data class PowerUp(
    val id: Double = Math.random(),
    val type: PowerUpType,
    var x: Float,
    var y: Float
)

enum class PowerUpType { SPEED, MAGNET, SHIELD, GOLD }

data class Fish(
    val type: FishType,
    val tier: FishTier,
    val id: Double = Math.random(),
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var kg: Float,
    var pts: Int,
    var multiplier: Float,
    var isCaught: Boolean = false,
    var isRare: Boolean = false
)

data class GameState(
    val score: Long = 0,
    val totalFishCaught: Int = 0,
    val currentKg: Float = 0f,
    val upgLevels: Map<String, Int> = mapOf(
        "depth" to 0, 
        "weight" to 0, 
        "steering" to 0,
        "turbo" to 0,
        "boss" to 0,
        "bait" to 0 
    ),
    val gamePhase: String = "MENU", 
    val hookX: Float = 540f,
    val hookXTarget: Float = 540f,
    val hookY: Float = 0f,
    val isTurbo: Boolean = false,
    val camY: Float = 0f,
    val camYTarget: Float = 0f,
    val weightFull: Boolean = false,
    val toastMessage: String? = null,
    
    val currentBiomeIndex: Int = 0,
    val maxBiomeReached: Int = 0,
    val caughtSpecies: Set<String> = emptySet(),
    val speciesCounts: Map<String, Int> = emptyMap(),
    val maxWeights: Map<String, Float> = emptyMap(),
    
    val prestigeMultiplier: Float = 1.0f,
    val prestigeLevel: Int = 0,
    val totalLifetimeScore: Long = 0,
    val lastTimestamp: Long = System.currentTimeMillis(),
    
    val bossActive: Boolean = false,
    val bossHealth: Float = 0f,
    val bossMaxHealth: Float = 100f,
    val bossTension: Float = 50f,
    val bossSafeZonePos: Float = 50f,
    val nextBiomeUnlocked: Boolean = false,
    
    val bossWarningActive: Boolean = false,
    val bossCountdown: Int = 0,
    
    val showCollection: Boolean = false,
    val showMapSelector: Boolean = false,
    val showPrestigeConfirm: Boolean = false,
    val showShop: Boolean = false,
    val showSettings: Boolean = false,
    val showResetConfirm: Boolean = false,
    
    val musicEnabled: Boolean = true,
    val sfxEnabled: Boolean = true,
    
    val activePowerUps: Map<PowerUpType, Long> = emptyMap()
)
