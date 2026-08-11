package com.kitchennotes.app

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray

class RecipeRepository(context: Context) {
    private val prefs = context.getSharedPreferences("recipes", Context.MODE_PRIVATE)
    var recoveryAvailable: Boolean = false
        private set

    fun load(): List<Recipe> {
        val raw = prefs.getString("items", "[]").orEmpty()
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { Recipe.fromJson(array.getJSONObject(it)) }
        }.getOrElse {
            if (!prefs.contains(RECOVERY_KEY)) {
                prefs.edit { putString(RECOVERY_KEY, raw) }
            }
            recoveryAvailable = true
            emptyList()
        }
    }

    fun save(recipes: List<Recipe>) {
        prefs.edit { putString("items", JSONArray(recipes.map { it.toJson() }).toString()) }
    }

    fun merge(incoming: List<Recipe>): List<Recipe> {
        return RecipeMerger.merge(load(), incoming).also(::save)
    }
}

private const val RECOVERY_KEY = "items_recovery"

object RecipeMerger {
    fun merge(localRecipes: List<Recipe>, incoming: List<Recipe>): List<Recipe> {
        val local = localRecipes.associateBy { it.id }.toMutableMap()
        incoming.forEach { other ->
            val own = local[other.id]
            local[other.id] = when {
                own == null -> other
                other.updatedAt >= own.updatedAt -> other.copy(
                    cookCount = maxOf(own.cookCount, other.cookCount),
                    lastCookedAt = maxOf(own.lastCookedAt, other.lastCookedAt),
                    steps = mergeStepImages(other.steps, own.steps)
                )
                else -> own.copy(
                    cookCount = maxOf(own.cookCount, other.cookCount),
                    lastCookedAt = maxOf(own.lastCookedAt, other.lastCookedAt),
                    steps = mergeStepImages(own.steps, other.steps)
                )
            }
        }
        return local.values.sortedByDescending { it.updatedAt }
    }

    private fun mergeStepImages(primary: List<RecipeStep>, secondary: List<RecipeStep>): List<RecipeStep> =
        primary.mapIndexed { index, step ->
            if (step.imagePath.isNotBlank()) step
            else {
                val matching = secondary.firstOrNull {
                    it.text.trim() == step.text.trim() && it.imagePath.isNotBlank()
                } ?: secondary.getOrNull(index)?.takeIf {
                    it.text.trim() == step.text.trim() && it.imagePath.isNotBlank()
                }
                step.copy(imagePath = matching?.imagePath.orEmpty())
            }
        }
}
