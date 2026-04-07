package com.example.fishingidlegame.config

import androidx.compose.ui.graphics.Color

object GameConfig {
    const val CAST_SPEED = 18f // Aumentado para mayor fluidez
    const val REEL_SPEED_BASE = 5.0f
    const val REEL_SPEED_FULL = 20.0f
    const val NUM_FISH = 30 // Más vida en el mar
    const val M2PX_BASE = 15f // Escala mejorada

    val fishTypes = listOf(
        FishTypeConfig("Sardina",     Color(0xFFBDC3C7), 15,   20f, 10f, 0.5f,  "🐟"),
        FishTypeConfig("Trucha",      Color(0xFFE07B54), 30,   32f, 18f, 1.5f,  "🐠"),
        FishTypeConfig("Carpa",       Color(0xFF7AB648), 60,   40f, 22f, 3.5f,  "🐡"),
        FishTypeConfig("Salmón",      Color(0xFFF4845F), 120,  45f, 20f, 5.0f,  "🐟"),
        FishTypeConfig("Lucio",       Color(0xFF5B8DD9), 250,  55f, 18f, 8.0f,  "🦈"),
        FishTypeConfig("Pez Espada",  Color(0xFF4ECDC4), 600,  75f, 15f, 12.0f, "🗡️"),
        FishTypeConfig("Tiburón",     Color(0xFF636E72), 1500, 95f, 35f, 25.0f, "🦈"),
        FishTypeConfig("Kraken",      Color(0xFFD63031), 5000, 120f, 80f, 60.0f,"🐙"),
        FishTypeConfig("Leviatán",    Color(0xFF2D3436), 15000, 180f, 90f, 150.0f, "🐳")
    )

    val upgrades = mapOf(
        "depth" to UpgradeConfig(
            id = "depth", name = "Carrete Pro", icon = "🧵", unit = "m",
            levels = listOf(100, 300, 800, 2000, 5000, 12000, 30000, 75000),
            values = listOf(30f, 60f, 120f, 250f, 500f, 1000f, 2500f, 6000f, 15000f)
        ),
        "weight" to UpgradeConfig(
            id = "weight", name = "Red Reforzada", icon = "🕸️", unit = "kg",
            levels = listOf(150, 450, 1200, 3500, 8000, 20000, 50000, 150000),
            values = listOf(10f, 25f, 60f, 150f, 400f, 1000f, 3000f, 10000f, 50000f)
        ),
        "speed" to UpgradeConfig( // Nueva mejora: Velocidad
            id = "speed", name = "Motor", icon = "⚙️", unit = "x",
            levels = listOf(200, 600, 1800, 5000, 15000, 45000),
            values = listOf(1.0f, 1.2f, 1.5f, 2.0f, 2.8f, 4.0f, 6.0f)
        ),
        "luck" to UpgradeConfig( // Nueva mejora: Suerte (peces raros)
            id = "luck", name = "Cebo Dorado", icon = "✨", unit = "%",
            levels = listOf(500, 2000, 10000, 50000),
            values = listOf(0f, 5f, 15f, 35f, 75f)
        )
    )
}

data class FishTypeConfig(
    val name: String,
    val color: Color,
    val pts: Int,
    val w: Float,
    val h: Float,
    val kg: Float,
    val icon: String // Añadido para UI
)

data class UpgradeConfig(
    val id: String,
    val name: String,
    val icon: String,
    val unit: String,
    val levels: List<Int>,
    val values: List<Float>
)
