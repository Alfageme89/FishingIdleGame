package com.example.fishingidlegame.data

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest


data class User(val email: String, val username: String)

class UserRepository(context: Context) {
    private val firebaseRepository = FirebaseRepository()
    private val prefs: SharedPreferences =
        context.getSharedPreferences("fishing_users", Context.MODE_PRIVATE)

    private fun hash(password: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(password.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun register(email: String, username: String, password: String): Result<User> {
        val key = email.trim().lowercase()
        val trimmedUsername = username.trim()
        val emails = prefs.getStringSet("registered_emails", emptySet()) ?: emptySet()
        val usernames = prefs.getStringSet("registered_usernames", emptySet()) ?: emptySet()

        if (emails.contains(key)) {
            return Result.failure(Exception("Este correo ya está registrado"))
        }
        if (usernames.any { it.equals(trimmedUsername, ignoreCase = true) }) {
            return Result.failure(Exception("Ese nombre de usuario ya está en uso"))
        }

        prefs.edit().apply {
            putStringSet("registered_emails", emails + key)
            putStringSet("registered_usernames", usernames + trimmedUsername.lowercase())
            putString("user_${key}_username", trimmedUsername)
            putString("user_${key}_hash", hash(password))
            apply()
        }
        saveActiveUser(key)

        firebaseRepository.saveUser(
            key,
            trimmedUsername
        )

        return Result.success(User(key, trimmedUsername))
    }

    fun login(email: String, password: String): Result<User> {
        val key = email.trim().lowercase()
        val emails = prefs.getStringSet("registered_emails", emptySet()) ?: emptySet()
        if (!emails.contains(key)) {
            return Result.failure(Exception("Correo no encontrado"))
        }
        val stored = prefs.getString("user_${key}_hash", "") ?: ""
        if (hash(password) != stored) {
            return Result.failure(Exception("Contraseña incorrecta"))
        }
        val username = prefs.getString("user_${key}_username", key) ?: key
        saveActiveUser(key)
        return Result.success(User(key, username))
    }

    fun getActiveUser(): User? {
        val key = prefs.getString("active_user", null) ?: return null
        val username = prefs.getString("user_${key}_username", null) ?: return null
        return User(key, username)
    }

    fun logout() {
        prefs.edit().remove("active_user").apply()
    }

    private fun saveActiveUser(key: String) {
        prefs.edit().putString("active_user", key).apply()
    }
}
