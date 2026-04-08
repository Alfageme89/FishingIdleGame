package com.example.fishingidlegame.config

import androidx.compose.ui.graphics.Color
import com.example.fishingidlegame.model.Biome
import com.example.fishingidlegame.model.FishType
import com.example.fishingidlegame.model.Boss

object GameConfig {
    const val CAST_SPEED = 15f
    const val REEL_SPEED_BASE = 4.5f
    const val REEL_SPEED_FULL = 16.0f
    const val NUM_FISH = 25
    const val M2PX_BASE = 12f

    // ── BIOMAS CON BOSSES ──
    val biomes = listOf(
        Biome("Lago Sereno", Color(0xFF1e5fa8), Color(0xFF1898a8), Color(0xFF061220), 0f, listOf("Trucha", "Carpa", "Lucio"), "Siluro Gigante"),
        Biome("Arrecife Coral", Color(0xFF5ba8d8), Color(0xFF4ecdc4), Color(0xFF123456), 40f, listOf("Pez Payaso", "Salmón", "Piraña", "Pez Globo"), "Gran Tiburón Blanco"),
        Biome("Océano Profundo", Color(0xFF0D1B2A), Color(0xFF1B263B), Color(0xFF010406), 110f, listOf("Tiburón", "Pez Espada", "Rape", "Manta Raya"), "Megalodón"),
        Biome("Abismo Abisal", Color(0xFF000000), Color(0xFF010406), Color(0xFF000000), 280f, listOf("Calamar Gig.", "Dunkleosteus", "Anguila Eléctrica"), "Leviathán"),
        Biome("Zona Volcánica", Color(0xFF2B1010), Color(0xFF440000), Color(0xFF1A0000), 500f, listOf("Pez Magma", "Dragón Marino", "Kraken"), "EL KRAKEN")
    )

    // ── DATOS DE LOS BOSSES ──
    val bosses = mapOf(
        "Siluro Gigante" to Boss("Siluro Gigante", Color(0xFF4A4A4A), 100f, 100f, 1000L),
        "Gran Tiburón Blanco" to Boss("Gran Tiburón Blanco", Color(0xFF6B8FA8), 250f, 120f, 5000L),
        "Megalodón" to Boss("Megalodón", Color(0xFF2F4F4F), 600f, 180f, 25000L),
        "Leviathán" to Boss("Leviathán", Color(0xFF4B0082), 1500f, 250f, 100000L),
        "EL KRAKEN" to Boss("EL KRAKEN", Color(0xFF800000), 5000f, 400f, 1000000L)
    )

    // (Reutilizamos fishTypes y upgrades del archivo anterior...)
    val fishTypes = mapOf(
        "Trucha" to FishType("Trucha", Color(0xFFE07B54), 10, 28f, 16f, 1.5f),
        "Carpa" to FishType("Carpa", Color(0xFF7AB648), 15, 34f, 18f, 3.0f),
        "Lucio" to FishType("Lucio", Color(0xFF5B8DD9), 30, 42f, 14f, 5.0f),
        "Pez Payaso" to FishType("Pez Payaso", Color(0xFFFA8231), 45, 20f, 15f, 0.8f),
        "Salmón" to FishType("Salmón", Color(0xFFF4845F), 60, 38f, 17f, 4.0f),
        "Pez Globo" to FishType("Pez Globo", Color(0xFFFED330), 80, 25f, 25f, 2.5f),
        "Piraña" to FishType("Piraña", Color(0xFFE63946), 40, 26f, 20f, 2.0f),
        "Rape" to FishType("Rape", Color(0xFF9B72CF), 150, 30f, 28f, 3.5f),
        "Manta Raya" to FishType("Manta Raya", Color(0xFF4B7BEC), 250, 55f, 10f, 12.0f),
        "Tiburón" to FishType("Tiburón", Color(0xFF6B8FA8), 400, 56f, 22f, 18.0f),
        "Pez Espada" to FishType("Pez Espada", Color(0xFF4ECDC4), 600, 60f, 12f, 8.0f),
        "Calamar Gig." to FishType("Calamar Gig.", Color(0xFFC77DFF), 1200, 48f, 36f, 15.0f),
        "Anguila Eléctrica" to FishType("Anguila", Color(0xFFF7B731), 1500, 60f, 8f, 5.0f),
        "Dunkleosteus" to FishType("Dunkleosteus", Color(0xFF8D6E63), 2500, 64f, 34f, 19.0f),
        "Pez Magma" to FishType("Pez Magma", Color(0xFFFF4500), 5000, 50f, 30f, 10.0f),
        "Dragón Marino" to FishType("Dragón Marino", Color(0xFFFFD700), 12000, 70f, 40f, 25.0f),
        "Kraken" to FishType("KRAKEN", Color(0xFF800000), 50000, 120f, 80f, 200.0f)
    )

    val upgrades = mapOf(
        "depth" to UpgradeConfig("depth", "Profundidad", "🌊", "m", 
            listOf(50, 150, 400, 1000, 2500, 6000, 15000, 40000, 100000),
            listOf(20f, 45f, 80f, 130f, 200f, 300f, 450f, 650f, 1000f, 2000f)),
        "weight" to UpgradeConfig("weight", "Capacidad", "⚖️", "kg", 
            listOf(60, 200, 600, 1500, 4000, 10000, 25000, 60000, 150000),
            listOf(20f, 35f, 55f, 85f, 130f, 200f, 320f, 500f, 800f, 1500f)),
        "pts" to UpgradeConfig("pts", "Valor", "⭐", "x", 
            listOf(100, 300, 1000, 3000, 8000, 20000, 50000, 120000, 300000),
            listOf(1.0f, 1.5f, 2.2f, 3.5f, 5.5f, 9f, 15f, 25f, 45f, 100f)),
        "crew" to UpgradeConfig("crew", "Tripulación", "👨‍✈️", "/s", 
            listOf(500, 1500, 5000, 15000, 50000, 150000, 500000),
            listOf(0f, 10f, 50f, 250f, 1200f, 6000f, 35000f, 200000f))
    )
}

data class UpgradeConfig(val id: String, val name: String, val icon: String, val unit: String, val levels: List<Int>, val values: List<Float>)
