package com.example.fishingidlegame.data
import com.google.firebase.firestore.FirebaseFirestore
class FirebaseRepository {

    private val db = FirebaseFirestore.getInstance()

    fun saveUser(email: String, username: String) {

        val user = hashMapOf(
            "email" to email,
            "username" to username
        )

        db.collection("users")
            .document(email)
            .set(user)
    }

    fun saveScore(
        userEmail: String,
        username: String,
        score: Long
    ) {

        val data = hashMapOf(
            "userEmail" to userEmail,
            "username" to username,
            "score" to score
        )

        db.collection("scores")
            .document(userEmail)
            .set(data)
    }

    fun saveFishWeight(
        userEmail: String,
        fishName: String,
        maxWeight: Float
    ) {

        val data = hashMapOf(
            "userEmail" to userEmail,
            "fishName" to fishName,
            "maxWeight" to maxWeight
        )

        db.collection("fish_weights")
            .document(userEmail.replace(".", "_") + "_" + fishName)
            .set(data)
    }
}