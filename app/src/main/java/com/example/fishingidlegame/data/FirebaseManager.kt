package com.example.fishingidlegame.data

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class FirebaseManager {

    private val db = FirebaseFirestore.getInstance()

    suspend fun guardarPuntuacion(
        userId: String,
        username: String,
        puntuacion: Long
    ) = withContext(Dispatchers.IO) {

        val datos = hashMapOf(
            "userId" to userId,
            "username" to username,
            "puntuacion" to puntuacion,
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

        val docId = "${userId}_$pez"

        val datos = hashMapOf(
            "userId" to userId,
            "username" to username,
            "pez" to pez,
            "pesoMaximo" to peso,
            "timestamp" to System.currentTimeMillis()
        )

        db.collection("pesosCompetitivos")
            .document(docId)
            .set(datos)
            .await()
    }
}