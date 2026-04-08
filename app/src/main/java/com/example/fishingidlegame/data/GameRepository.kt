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
            putLong("lastTime", System.currentTimeMillis())
            // Guardar niveles de upgrades
            state.upgLevels.forEach { (key, level) ->
                putInt("upg_$key", level)
            }
            apply()
        }
    }

    suspend fun loadProgress(): GameState = withContext(Dispatchers.IO) {
        val score = prefs.getLong("score", 0L)
        val totalFish = prefs.getInt("totalFish", 0)
        val prestigeMult = prefs.getFloat("prestigeMult", 1.0f)
        val lifetimeScore = prefs.getLong("lifetimeScore", 0L)
        val lastTime = prefs.getLong("lastTime", System.currentTimeMillis())
        
        val levels = mapOf(
            "depth" to prefs.getInt("upg_depth", 0),
            "weight" to prefs.getInt("upg_weight", 0),
            "pts" to prefs.getInt("upg_pts", 0),
            "crew" to prefs.getInt("upg_crew", 0)
        )
        GameState(
            score = score,
            totalFishCaught = totalFish,
            upgLevels = levels,
            prestigeMultiplier = prestigeMult,
            totalLifetimeScore = lifetimeScore,
            lastTimestamp = lastTime
        )
    }
}
