package com.example.fishingidlegame.data

import android.content.Context
import android.content.SharedPreferences
import com.example.fishingidlegame.model.GameState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GameRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("fishing_prefs_v2", Context.MODE_PRIVATE)

    suspend fun saveProgress(state: GameState) = withContext(Dispatchers.IO) {
        prefs.edit().apply {
            putLong("score", state.score)
            putInt("totalFish", state.totalFishCaught)
            // Guardar todos los niveles de upgrades dinámicamente
            state.upgLevels.forEach { (key, level) ->
                putInt("upg_$key", level)
            }
            apply()
        }
    }

    suspend fun loadProgress(): GameState = withContext(Dispatchers.IO) {
        val score = prefs.getLong("score", 0L)
        val totalFish = prefs.getInt("totalFish", 0)
        
        // Cargar upgrades conocidos
        val upgradeKeys = listOf("depth", "weight", "speed", "luck")
        val levels = upgradeKeys.associateWith { key ->
            prefs.getInt("upg_$key", 0)
        }
        
        GameState(
            score = score, 
            totalFishCaught = totalFish, 
            upgLevels = levels
        )
    }
}
