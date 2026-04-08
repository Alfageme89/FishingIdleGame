package com.example.fishingidlegame.data

import android.content.Context
import android.content.SharedPreferences
import com.example.fishingidlegame.model.GameState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GameRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("fishing_prefs", Context.MODE_PRIVATE)

    suspend fun saveProgress(state: GameState) = withContext(Dispatchers.IO) {
        prefs.edit().apply {
            putLong("score", state.score)
            putInt("totalFish", state.totalFishCaught)
            putFloat("prestigeMult", state.prestigeMultiplier)
            putLong("lifetimeScore", state.totalLifetimeScore)
            putInt("currentBiome", state.currentBiomeIndex)
            putInt("maxBiome", state.maxBiomeReached)
            
            putStringSet("caughtSpecies", state.caughtSpecies)
            state.speciesCounts.forEach { (name, count) -> putInt("count_$name", count) }
            state.maxWeights.forEach { (name, weight) -> putFloat("weight_$name", weight) }

            state.upgLevels.forEach { (key, level) -> putInt("upg_$key", level) }
            apply()
        }
    }

    suspend fun loadProgress(): GameState = withContext(Dispatchers.IO) {
        val caughtSpecies = prefs.getStringSet("caughtSpecies", emptySet()) ?: emptySet()
        val levels = mutableMapOf<String, Int>()
        listOf("depth", "weight", "pts", "crew", "steering", "turbo", "boss").forEach { key ->
            levels[key] = prefs.getInt("upg_$key", 0)
        }

        val counts = mutableMapOf<String, Int>()
        val weights = mutableMapOf<String, Float>()
        caughtSpecies.forEach { name ->
            counts[name] = prefs.getInt("count_$name", 0)
            weights[name] = prefs.getFloat("weight_$name", 0f)
        }
        
        GameState(
            score = prefs.getLong("score", 0L),
            totalFishCaught = prefs.getInt("totalFish", 0),
            upgLevels = levels,
            currentBiomeIndex = prefs.getInt("currentBiome", 0),
            maxBiomeReached = prefs.getInt("maxBiome", 0),
            caughtSpecies = caughtSpecies,
            speciesCounts = counts,
            maxWeights = weights,
            prestigeMultiplier = prefs.getFloat("prestigeMult", 1.0f),
            totalLifetimeScore = prefs.getLong("lifetimeScore", 0L)
        )
    }
}
