package com.example.fishingidlegame.data

import com.example.fishingidlegame.model.GameState
import com.example.fishingidlegame.model.RankingJugador
import com.example.fishingidlegame.model.RankingPez
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class FirebaseManager {

    private val db = FirebaseFirestore.getInstance()

    // ---- ESCRITURA ----

    suspend fun guardarPuntuacion(
        userId: String,
        username: String,
        puntuacion: Long,
        prestigeLevel: Int = 0,
        prestigeMultiplier: Float = 1.0f
    ) = withContext(Dispatchers.IO) {
        val datos = hashMapOf(
            "userId" to userId,
            "username" to username,
            "puntuacion" to puntuacion,
            "prestigeLevel" to prestigeLevel,
            "prestigeMultiplier" to prestigeMultiplier.toDouble(),
            "timestamp" to System.currentTimeMillis()
        )
        db.collection("puntuaciones")
            .document(userId)
            .set(datos)
            .await()
    }

    suspend fun guardarPesoPez(
        userId: String,
        username: String,
        pez: String,
        peso: Float
    ) = withContext(Dispatchers.IO) {
        val docId = "${userId}_${pez.replace(" ", "_")}"
        val ref = db.collection("pesosCompetitivos").document(docId)
        runCatching {
            val existing = ref.get().await()
            val storedMax = existing.getDouble("pesoMaximo") ?: 0.0
            if (peso.toDouble() > storedMax) {
                val datos = hashMapOf(
                    "userId" to userId,
                    "username" to username,
                    "pez" to pez,
                    "pesoMaximo" to peso.toDouble(),
                    "timestamp" to System.currentTimeMillis()
                )
                ref.set(datos).await()
            }
        }
    }

    suspend fun guardarProgresoCompleto(
        userId: String,
        username: String,
        state: GameState
    ) = withContext(Dispatchers.IO) {
        val datos = hashMapOf(
            "userId" to userId,
            "username" to username,
            "score" to state.score,
            "totalFishCaught" to state.totalFishCaught,
            "prestigeMultiplier" to state.prestigeMultiplier.toDouble(),
            "prestigeLevel" to state.prestigeLevel,
            "totalLifetimeScore" to state.totalLifetimeScore,
            "currentBiomeIndex" to state.currentBiomeIndex,
            "maxBiomeReached" to state.maxBiomeReached,
            "caughtSpecies" to state.caughtSpecies.toList(),
            "speciesCounts" to state.speciesCounts,
            "maxWeights" to state.maxWeights.mapValues { it.value.toDouble() },
            "upgLevels" to state.upgLevels,
            "musicEnabled" to state.musicEnabled,
            "sfxEnabled" to state.sfxEnabled,
            "lastUpdated" to System.currentTimeMillis()
        )
        db.collection("user_progress")
            .document(userId)
            .set(datos)
            .await()
    }

    // Crea los documentos iniciales del usuario solo si no existen aún.
    fun inicializarUsuario(userId: String, username: String) {
        val timestamp = System.currentTimeMillis()

        val scoreRef = db.collection("puntuaciones").document(userId)
        scoreRef.get().addOnSuccessListener { doc ->
            if (!doc.exists()) {
                scoreRef.set(
                    hashMapOf(
                        "userId" to userId,
                        "username" to username,
                        "puntuacion" to 0L,
                        "prestigeLevel" to 0,
                        "prestigeMultiplier" to 1.0,
                        "timestamp" to timestamp
                    )
                )
            }
        }

        val progressRef = db.collection("user_progress").document(userId)
        progressRef.get().addOnSuccessListener { doc ->
            if (!doc.exists()) {
                progressRef.set(
                    hashMapOf(
                        "userId" to userId,
                        "username" to username,
                        "score" to 0L,
                        "totalFishCaught" to 0,
                        "prestigeMultiplier" to 1.0,
                        "prestigeLevel" to 0,
                        "totalLifetimeScore" to 0L,
                        "currentBiomeIndex" to 0,
                        "maxBiomeReached" to 0,
                        "caughtSpecies" to emptyList<String>(),
                        "speciesCounts" to emptyMap<String, Int>(),
                        "maxWeights" to emptyMap<String, Double>(),
                        "upgLevels" to mapOf(
                            "depth" to 0, "weight" to 0, "steering" to 0,
                            "turbo" to 0, "boss" to 0, "bait" to 0
                        ),
                        "musicEnabled" to true,
                        "sfxEnabled" to true,
                        "lastUpdated" to timestamp
                    )
                )
            }
        }
    }

    // ---- CONSULTAS DE RANKING ----

    suspend fun obtenerRankingPuntuaciones(): List<RankingJugador> = withContext(Dispatchers.IO) {
        runCatching {
            db.collection("puntuaciones")
                .orderBy("puntuacion", Query.Direction.DESCENDING)
                .limit(20)
                .get()
                .await()
                .documents
                .map { doc ->
                    RankingJugador(
                        username = doc.getString("username") ?: "?",
                        puntuacion = doc.getLong("puntuacion") ?: 0L,
                        prestigeLevel = (doc.getLong("prestigeLevel") ?: 0L).toInt(),
                        prestigeMultiplier = (doc.getDouble("prestigeMultiplier") ?: 1.0).toFloat()
                    )
                }
        }.getOrElse { emptyList() }
    }

    suspend fun obtenerRankingPorPez(fishName: String): List<RankingPez> = withContext(Dispatchers.IO) {
        runCatching {
            db.collection("pesosCompetitivos")
                .whereEqualTo("pez", fishName)
                .get()
                .await()
                .documents
                .mapNotNull { doc ->
                    val peso = doc.getDouble("pesoMaximo") ?: return@mapNotNull null
                    RankingPez(
                        username = doc.getString("username") ?: "?",
                        pez = doc.getString("pez") ?: fishName,
                        pesoMaximo = peso.toFloat()
                    )
                }
                .sortedByDescending { it.pesoMaximo }
                .take(10)
        }.getOrElse { emptyList() }
    }
}
