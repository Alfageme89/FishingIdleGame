package com.example.fishingidlegame.model

import androidx.compose.ui.graphics.Color

data class Biome(
    val name: String,
    val skyColor: Color,
    val waterColorTop: Color,
    val waterColorBottom: Color,
    val minDepthRequired: Float,
    val fishTypeNames: List<String>,
    val bossName: String // El nombre del Boss de este bioma
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
    val upgLevels: Map<String, Int> = mapOf("depth" to 0, "weight" to 0, "pts" to 0, "crew" to 0),
    val gamePhase: String = "MENU", // MENU, FISHING, REELING, BOSS_FIGHT
    val hookX: Float = 240f,
    val hookY: Float = 0f,
    val camY: Float = 0f,
    val camYTarget: Float = 0f,
    val weightFull: Boolean = false,
    val toastMessage: String? = null,
    
    val currentBiomeIndex: Int = 0,
    val prestigeMultiplier: Float = 1.0f,
    val totalLifetimeScore: Long = 0,
    val lastTimestamp: Long = System.currentTimeMillis(),
    
    // ── NUEVO: ESTADO DE BOSS ──
    val bossActive: Boolean = false,
    val bossHealth: Float = 0f,
    val bossMaxHealth: Float = 100f,
    val nextBiomeUnlocked: Boolean = false
)
