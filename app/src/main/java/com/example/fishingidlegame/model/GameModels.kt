package com.example.fishingidlegame.model

import androidx.compose.ui.graphics.Color

data class FishType(
    val name: String,
    val color: Color,
    val basePoints: Int,
    val width: Float,
    val height: Float,
    val baseKg: Float,
    val icon: String
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
        "speed" to 0, 
        "luck" to 0
    ),
    val gamePhase: String = "MENU", // MENU, FISHING, REELING
    val hookX: Float = 0f,
    val hookY: Float = 0f,
    val camY: Float = 0f,
    val camYTarget: Float = 0f,
    val weightFull: Boolean = false,
    val toastMessage: String? = null
)
