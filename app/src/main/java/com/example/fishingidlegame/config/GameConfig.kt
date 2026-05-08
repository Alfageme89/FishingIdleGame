package com.example.fishingidlegame.config

import androidx.compose.ui.graphics.Color
import com.example.fishingidlegame.model.Biome
import com.example.fishingidlegame.model.FishType
import com.example.fishingidlegame.model.Boss

object GameConfig {
    const val CAST_SPEED = 6f
    const val REEL_SPEED_BASE = 4f
    const val REEL_SPEED_FULL = 14f
    const val NUM_FISH_PER_100M = 15 // Ajustamos cantidad por profundidad
    const val NUM_POWERUPS = 8
    const val M2PX_BASE = 25f 

    val biomes = listOf(
        Biome("Lago Sereno",    "Aguas tranquilas ideales para principiantes.",             Color(0xFF1e5fa8), Color(0xFF1898a8), Color(0xFF061220), 0f,    listOf("Trucha", "Carpa", "Lucio"),                          "Kraken"),
        Biome("Arrecife Coral", "Un festival de colores y biodiversidad marina.",           Color(0xFF5ba8d8), Color(0xFF4ecdc4), Color(0xFF123456), 100f,  listOf("Pez Payaso", "Salmón", "Piraña", "Pez Globo"),        "Cangrejo"),
        Biome("Océano Profundo","Donde la luz empieza a escasear y los gigantes habitan.", Color(0xFF0D1B2A), Color(0xFF1B263B), Color(0xFF010406), 300f,  listOf("Tiburón", "Pez Espada", "Rape", "Manta Raya"),         "Cangrejo Mecánico"),
        Biome("Abismo Abisal",  "Presiones extremas y criaturas de pesadilla.",            Color(0xFF000000), Color(0xFF010406), Color(0xFF000000), 800f,  listOf("Calamar Gig.", "Dunkleosteus", "Anguila Eléctrica"),   "Verdadero Kraken"),
        Biome("Zona Volcánica", "El mismísimo infierno bajo el mar.",                      Color(0xFF2B1010), Color(0xFF440000), Color(0xFF1A0000), 2000f, listOf("Pez Magma", "Dragón Marino", "KRAKEN"),                "Rey Cangrejo")
    )

    val bosses = mapOf(
        "Kraken"            to Boss("Kraken",            Color(0xFF1A3A5C), 200f,    110f, 600L,    triggerDepthM = 120f),
        "Cangrejo"          to Boss("Cangrejo",          Color(0xFFB84A00), 800f,    130f, 3500L,   triggerDepthM = 300f),
        "Cangrejo Mecánico" to Boss("Cangrejo Mecánico", Color(0xFF607080), 3000f,   190f, 18000L,  triggerDepthM = 500f),
        "Verdadero Kraken"  to Boss("Verdadero Kraken",  Color(0xFF0A1F40), 2700f,   280f, 90000L,  triggerDepthM = 750f),
        "Rey Cangrejo"      to Boss("Rey Cangrejo",      Color(0xFF8B0000), 4200f,   450f, 800000L, triggerDepthM = 1000f)
    )

    val fishTypes = mapOf(
        "Trucha" to FishType("Trucha", Color(0xFFE07B54), 5, 28f, 16f, 0.8f, 1.0f, "Común", "Pez ágil de río.", 0f, 50f),
        "Carpa" to FishType("Carpa", Color(0xFF7AB648), 8, 34f, 18f, 1.5f, 0.8f, "Común", "Tranquila y robusta.", 10f, 80f),
        "Lucio" to FishType("Lucio", Color(0xFF5B8DD9), 20, 42f, 14f, 3.0f, 0.4f, "Raro", "Depredador solitario.", 40f, 120f),
        
        "Pez Payaso" to FishType("Pez Payaso", Color(0xFFFA8231), 25, 20f, 15f, 0.3f, 1.0f, "Común", "Vive en anémonas.", 100f, 180f),
        "Salmón" to FishType("Salmón", Color(0xFFF4845F), 40, 38f, 17f, 2.0f, 0.7f, "Común", "Salta contra corriente.", 120f, 250f),
        "Piraña" to FishType("Piraña", Color(0xFFE63946), 60, 26f, 20f, 1.0f, 0.5f, "Raro", "Dientes afilados.", 150f, 300f),
        "Pez Globo" to FishType("Pez Globo", Color(0xFFFED330), 100, 25f, 25f, 1.2f, 0.3f, "Épico", "Se infla al sustarse.", 200f, 400f),

        "Manta Raya" to FishType("Manta Raya", Color(0xFF4B7BEC), 150, 55f, 10f, 8.0f, 0.6f, "Común", "Planea con elegancia.", 300f, 600f),
        "Tiburón" to FishType("Tiburón", Color(0xFF6B8FA8), 300, 56f, 22f, 12.0f, 0.4f, "Raro", "El rey de la costa.", 400f, 800f),
        "Pez Espada" to FishType("Pez Espada", Color(0xFF4ECDC4), 500, 60f, 12f, 6.0f, 0.2f, "Épico", "Rápido como saeta.", 500f, 1000f),
        "Rape" to FishType("Rape", Color(0xFF9B72CF), 800, 30f, 28f, 2.5f, 0.1f, "Legendario", "Linterna orgánica.", 700f, 1200f),

        "Anguila" to FishType("Anguila", Color(0xFFF7B731), 1200, 60f, 8f, 3.5f, 0.5f, "Raro", "¡Cuidado, descarga!", 800f, 1500f),
        "Calamar Gig." to FishType("Calamar Gig.", Color(0xFFC77DFF), 2500, 48f, 36f, 10.0f, 0.2f, "Épico", "Habita en tinieblas.", 1000f, 2000f),
        "Dunkleosteus" to FishType("Dunkleosteus", Color(0xFF8D6E63), 6000, 64f, 34f, 15.0f, 0.05f, "Legendario", "Mandíbulas de acero.", 1500f, 3000f),

        "Pez Magma" to FishType("Pez Magma", Color(0xFFFF4500), 15000, 50f, 30f, 6.0f, 0.3f, "Épico", "Sangre de lava.", 2000f, 4000f),
        "Dragón Marino" to FishType("Dragón Marino", Color(0xFFFFD700), 50000, 70f, 40f, 18.0f, 0.1f, "Legendario", "Guardián de tesoros.", 3000f, 6000f),
        "KRAKEN" to FishType("KRAKEN", Color(0xFF800000), 250000, 120f, 80f, 120.0f, 0.02f, "Divino", "Terror definitivo.", 5000f, 10000f)
    )

    val upgrades = mapOf(
        "depth" to UpgradeConfig("depth", "Profundidad", "🌊", "m",
            listOf(100, 600, 3000, 12000, 60000, 250000, 1200000),
            listOf(80f, 130f, 250f, 600f, 1200f, 2500f, 5000f, 10000f)),
        "weight" to UpgradeConfig("weight", "Capacidad", "⚖️", "kg",
            listOf(80, 500, 2500, 12000, 60000, 300000, 1500000, 8000000),
            listOf(30f, 80f, 200f, 500f, 1500f, 4000f, 12000f, 40000f, 120000f)),
        "steering" to UpgradeConfig("steering", "Giro", "☸️", "x",
            listOf(200, 2000, 20000, 200000),
            listOf(0.04f, 0.08f, 0.13f, 0.20f, 0.32f)),
        "turbo" to UpgradeConfig("turbo", "Turbo", "🚀", "x",
            listOf(250, 2500, 25000, 250000),
            listOf(1.0f, 1.5f, 2.2f, 3.5f, 5.0f)),
        "boss" to UpgradeConfig("boss", "Estabilidad", "⚓", "x",
            listOf(600, 6000, 60000, 600000),
            listOf(1.0f, 1.3f, 1.7f, 2.3f, 3.2f)),
        "bait" to UpgradeConfig("bait", "Cebo", "🪱", "x",
            listOf(1200, 10000, 80000, 600000),
            listOf(1.0f, 1.6f, 2.5f, 4.0f, 6.5f))
    )
}

data class UpgradeConfig(val id: String, val name: String, val icon: String, val unit: String, val levels: List<Int>, val values: List<Float>)
