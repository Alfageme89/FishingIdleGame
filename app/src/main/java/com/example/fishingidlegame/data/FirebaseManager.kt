package com.example.fishingidlegame.data

import com.google.firebase.firestore.FirebaseFirestore

class FirebaseManager {

    private val db = FirebaseFirestore.getInstance()

    fun guardarPuntuacion(
        userId: String,
        username: String,
        puntuacion: Long
    ) {

        val datos = hashMapOf(
            "userId" to userId,
            "username" to username,
            "puntuacion" to puntuacion
        )

        db.collection("puntuaciones")
            .document(userId)
            .set(datos)
    }

    fun guardarPesoPez(
        userId: String,
        username: String,
        pez: String,
        peso: Float
    ) {

        val datos = hashMapOf(
            "userId" to userId,
            "username" to username,
            "pez" to pez,
            "pesoMaximo" to peso
        )

        db.collection("pesosCompetitivos")
            .document(userId + "_" + pez)
            .set(datos)
    }
}