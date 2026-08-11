package com.kitchennotes.app

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.UUID

data class Ingredient(val name: String, val amount: String = "", val unit: String = "") {
    fun toJson() = JSONObject().put("name", name).put("amount", amount).put("unit", unit)
    companion object { fun fromJson(o: JSONObject) = Ingredient(o.optString("name"), o.optString("amount"), o.optString("unit")) }
}

data class RecipeStep(val text: String, val imagePath: String = "") {
    fun toJson() = JSONObject().put("text", text).put("imagePath", imagePath)
    companion object {
        fun fromJson(o: JSONObject) = RecipeStep(o.optString("text"), o.optString("imagePath"))
    }
}

data class ReferenceVideo(
    val url: String,
    val title: String = "抖音参考视频",
    val coverUrl: String = "",
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toJson() = JSONObject().put("url", url).put("title", title).put("coverUrl", coverUrl).put("updatedAt", updatedAt)
    companion object { fun fromJson(o: JSONObject) = ReferenceVideo(o.optString("url"), o.optString("title", "抖音参考视频"), o.optString("coverUrl"), o.optLong("updatedAt")) }
}

data class Recipe(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val coverPath: String = "",
    val ingredients: List<Ingredient> = emptyList(),
    val cuisineTags: Set<String> = emptySet(),
    val featureTags: Set<String> = emptySet(),
    val steps: List<RecipeStep> = emptyList(),
    val notes: String = "",
    val videos: List<ReferenceVideo> = emptyList(),
    val favorite: Boolean = false,
    val cookCount: Int = 0,
    val lastCookedAt: Long = 0,
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun searchableText() = listOf(name, ingredients.joinToString { it.name }, cuisineTags.joinToString(), featureTags.joinToString()).joinToString(" ").lowercase()
    fun toJson() = JSONObject().apply {
        put("id", id); put("name", name); put("coverPath", coverPath); put("notes", notes)
        put("favorite", favorite); put("cookCount", cookCount); put("lastCookedAt", lastCookedAt); put("updatedAt", updatedAt)
        put("ingredients", JSONArray(ingredients.map { it.toJson() }))
        put("cuisineTags", JSONArray(cuisineTags)); put("featureTags", JSONArray(featureTags)); put("steps", JSONArray(steps.map { it.toJson() })); put("videos", JSONArray(videos.map { it.toJson() }))
    }
    companion object {
        fun fromJson(o: JSONObject): Recipe {
            val legacyPhotos = o.strings("processPhotos")
            val stepArray = o.optJSONArray("steps") ?: JSONArray()
            val parsedSteps = List(stepArray.length()) { index ->
                val item = stepArray.opt(index)
                when (item) {
                    is JSONObject -> RecipeStep.fromJson(item).let { step ->
                        if (step.imagePath.isBlank()) step.copy(imagePath = legacyPhotos.getOrElse(index) { "" }) else step
                    }
                    else -> RecipeStep(stepArray.optString(index), legacyPhotos.getOrElse(index) { "" })
                }
            }.filter { it.text.isNotBlank() || it.imagePath.isNotBlank() }
            return Recipe(
                id = o.getString("id"), name = o.getString("name"), coverPath = o.optString("coverPath"),
                ingredients = o.objects("ingredients") { Ingredient.fromJson(it) },
                cuisineTags = o.strings("cuisineTags").toSet(), featureTags = o.strings("featureTags").toSet(),
                steps = parsedSteps, notes = o.optString("notes"), videos = o.objects("videos") { ReferenceVideo.fromJson(it) },
                favorite = o.optBoolean("favorite"), cookCount = o.optInt("cookCount"), lastCookedAt = o.optLong("lastCookedAt"), updatedAt = o.optLong("updatedAt")
            )
        }
    }
}

private fun JSONObject.strings(key: String): List<String> = optJSONArray(key)?.let { array -> List(array.length()) { array.optString(it) } } ?: emptyList()
private fun <T> JSONObject.objects(key: String, map: (JSONObject) -> T): List<T> = optJSONArray(key)?.let { array -> List(array.length()) { map(array.getJSONObject(it)) } } ?: emptyList()

val defaultCuisineTags = listOf("鲁菜", "川菜", "粤菜", "湘菜", "凉菜", "热菜", "主食", "汤羹")
val defaultFeatureTags = listOf("下饭", "低脂", "快手", "微辣", "家常", "宴客")

private val sharedUrlPattern = Regex("https://[^\\s]+", RegexOption.IGNORE_CASE)
private val sharedUrlTrailingPunctuation = charArrayOf('.', ',', ';', ':', '!', '?', '。', '，', '；', '：', '！', '？', '）', ')', '】', ']', '》', '>', '”', '"', '\'')

fun extractSharedHttpsUrl(text: String): String? {
    if (text.isBlank() || text.length > 32_768) return null
    return sharedUrlPattern.find(text)?.value
        ?.trimEnd(*sharedUrlTrailingPunctuation)
        ?.takeIf { candidate ->
            runCatching {
                val uri = URI(candidate)
                uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()
            }.getOrDefault(false)
        }
}
