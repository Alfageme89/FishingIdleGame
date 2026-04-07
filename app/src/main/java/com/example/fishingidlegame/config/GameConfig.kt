package com.example.fishingidlegame.config

import androidx.compose.ui.graphics.Color

object GameConfig {
    const val CAST_SPEED = 15f
    const val REEL_SPEED_BASE = 4.5f
    const val REEL_SPEED_FULL = 16.0f
    const val NUM_FISH = 20
    const val M2PX_BASE = 12f // px por metro

    val fishTypes = listOf(
        FishTypeConfig("Trucha",      Color(0xFFE07B54), 10,   28f, 16f, 1.5f),
        FishTypeConfig("Carpa",       Color(0xFF7AB648), 15,   34f, 18f, 3.0f),
        FishTypeConfig("Lucio",       Color(0xFF5B8DD9), 30,   42f, 14f, 5.0f),
        FishTypeConfig("Salmón",      Color(0xFFF4845F), 50,   38f, 17f, 4.0f),
        FishTypeConfig("Piraña",      Color(0xFFE63946), 40,   26f, 20f, 2.0f),
        FishTypeConfig("Rape",        Color(0xFF9B72CF), 100,  30f, 28f, 3.5f),
        FishTypeConfig("Pez Luna",    Color(0xFFA0C4FF), 80,   44f, 44f, 12.0f),
        FishTypeConfig("Tiburón",     Color(0xFF6B8FA8), 200,  56f, 22f, 18.0f),
        FishTypeConfig("Pez Espada",  Color(0xFF4ECDC4), 350,  60f, 12f, 8.0f),
        FishTypeConfig("Calamar Gig.",Color(0xFFC77DFF), 500,  48f, 36f, 15.0f),
        FishTypeConfig("Dunkleosteus",Color(0xFF8D6E63), 1000, 64f, 34f, 19.0f)
    )

    val upgrades = mapOf(
        "depth" to UpgradeConfig(
            id = "depth", name = "Profundidad", icon = "🌊", unit = "m",
            levels = listOf(50, 120, 220, 380, 600, 900, 1350, 2000),
            values = listOf(20f, 35f, 55f, 80f, 115f, 160f, 220f, 300f, 400f)
        ),
        "weight" to UpgradeConfig(
            id = "weight", name = "Límite Peso", icon = "⚖️", unit = "kg",
            levels = listOf(60, 150, 280, 460, 750, 1100, 1600, 2400),
            values = listOf(20f, 30f, 42f, 58f, 78f, 100f, 130f, 165f, 210f)
        ),
        "pts" to UpgradeConfig(
            id = "pts", name = "Puntuación", icon = "⭐", unit = "x",
            levels = listOf(80, 180, 320, 520, 850, 1300, 1900, 2800),
            values = listOf(1.0f, 1.3f, 1.7f, 2.2f, 2.8f, 3.5f, 4.5f, 5.8f, 7.5f)
        )
    )
}

data class FishTypeConfig(
    val name: String,
    val color: Color,
    val pts: Int,
    val w: Float,
    val h: Float,
    val kg: Float
)

data class UpgradeConfig(
    val id: String,
    val name: String,
    val icon: String,
    val unit: String,
    val levels: List<Int>,
    val values: List<Float>
)
