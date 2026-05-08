package com.example.fishingidlegame.data

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.security.MessageDigest

// email is used as Firestore document ID (plain, not hashed). Password is hashed.
data class User(val email: String, val firebaseId: String, val username: String)

class UserRepository(context: Context) {
    private val db = FirebaseFirestore.getInstance()
    private val firebaseManager = FirebaseManager()
    private val prefs: SharedPreferences =
        context.getSharedPreferences("fishing_session", Context.MODE_PRIVATE)

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    // doc ID in Firestore = sanitized email (replace . with _)
    private fun emailToDocId(email: String) = email.replace(".", "_")

    suspend fun register(email: String, username: String, password: String): Result<User> =
        withContext(Dispatchers.IO) {
            runCatching {
                val key = email.trim().lowercase()
                val docId = emailToDocId(key)
                val passwordHash = sha256(password)
                val trimmedUsername = username.trim()

                val existing = db.collection("accounts").document(docId).get().await()
                if (existing.exists()) throw Exception("Este correo ya está registrado")

                val usernameTaken = db.collection("accounts")
                    .whereEqualTo("usernameLower", trimmedUsername.lowercase())
                    .get().await()
                if (!usernameTaken.isEmpty) throw Exception("Ese nombre de usuario ya está en uso")

                val data = hashMapOf(
                    "email" to key,
                    "passwordHash" to passwordHash,
                    "username" to trimmedUsername,
                    "usernameLower" to trimmedUsername.lowercase()
                )
                db.collection("accounts").document(docId).set(data).await()

                firebaseManager.inicializarUsuario(docId, trimmedUsername)

                saveSession(key, docId, trimmedUsername)
                User(key, docId, trimmedUsername)
            }
        }

    suspend fun login(email: String, password: String): Result<User> =
        withContext(Dispatchers.IO) {
            runCatching {
                val key = email.trim().lowercase()
                val docId = emailToDocId(key)
                val passwordHash = sha256(password)

                val doc = db.collection("accounts").document(docId).get().await()
                if (!doc.exists()) throw Exception("Correo no encontrado")

                val storedHash = doc.getString("passwordHash") ?: ""
                if (passwordHash != storedHash) throw Exception("Contraseña incorrecta")

                val username = doc.getString("username") ?: key

                saveSession(key, docId, username)
                User(key, docId, username)
            }
        }

    fun getActiveUser(): User? {
        val key = prefs.getString("session_email", null) ?: return null
        val docId = prefs.getString("session_firebase_id", null) ?: return null
        val username = prefs.getString("session_username", null) ?: return null
        return User(key, docId, username)
    }

    fun logout() {
        prefs.edit().clear().apply()
    }

    private fun saveSession(email: String, docId: String, username: String) {
        prefs.edit()
            .putString("session_email", email)
            .putString("session_firebase_id", docId)
            .putString("session_username", username)
            .apply()
    }
}
