package com.example.fishingidlegame.data

import android.content.Context
import android.content.SharedPreferences
import com.example.fishingidlegame.model.GameState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GameRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("fishing_prefs", Context.MODE_PRIVATE)

    private var userPrefix = "guest"

    fun setUser(email: String) {
        userPrefix = email.replace("@", "_at_").replace(".", "_").replace("+", "_")
    }

    private fun k(key: String) = "${userPrefix}_$key"

    suspend fun saveProgress(state: GameState) = withContext(Dispatchers.IO) {
        prefs.edit().apply {
            putLong(k("score"), state.score)
            putInt(k("totalFish"), state.totalFishCaught)
            putFloat(k("prestigeMult"), state.prestigeMultiplier)
            putInt(k("prestigeLevel"), state.prestigeLevel)
            putLong(k("lifetimeScore"), state.totalLifetimeScore)
            putInt(k("currentBiome"), state.currentBiomeIndex)
            putInt(k("maxBiome"), state.maxBiomeReached)
            putBoolean(k("musicEnabled"), state.musicEnabled)
            putBoolean(k("sfxEnabled"), state.sfxEnabled)

            putStringSet(k("caughtSpecies"), state.caughtSpecies)
            state.speciesCounts.forEach { (name, count) -> putInt(k("count_$name"), count) }
            state.maxWeights.forEach { (name, weight) -> putFloat(k("weight_$name"), weight) }

            state.upgLevels.forEach { (key, level) -> putInt(k("upg_$key"), level) }
            apply()
        }
    }

    suspend fun loadProgress(): GameState = withContext(Dispatchers.IO) {
        val caughtSpecies = prefs.getStringSet(k("caughtSpecies"), emptySet()) ?: emptySet()
        val levels = mutableMapOf<String, Int>()
        listOf("depth", "weight", "steering", "turbo", "boss", "bait").forEach { key ->
            levels[key] = prefs.getInt(k("upg_$key"), 0)
        }

        val counts = mutableMapOf<String, Int>()
        val weights = mutableMapOf<String, Float>()
        caughtSpecies.forEach { name ->
            counts[name] = prefs.getInt(k("count_$name"), 0)
            weights[name] = prefs.getFloat(k("weight_$name"), 0f)
        }

        GameState(
            score = prefs.getLong(k("score"), 0L),
            totalFishCaught = prefs.getInt(k("totalFish"), 0),
            upgLevels = levels,
            currentBiomeIndex = prefs.getInt(k("currentBiome"), 0),
            maxBiomeReached = prefs.getInt(k("maxBiome"), 0),
            caughtSpecies = caughtSpecies,
            speciesCounts = counts,
            maxWeights = weights,
            prestigeMultiplier = prefs.getFloat(k("prestigeMult"), 1.0f),
            prestigeLevel = prefs.getInt(k("prestigeLevel"), 0),
            totalLifetimeScore = prefs.getLong(k("lifetimeScore"), 0L),
            musicEnabled = prefs.getBoolean(k("musicEnabled"), true),
            sfxEnabled = prefs.getBoolean(k("sfxEnabled"), true)
        )
    }
}
