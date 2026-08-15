package com.kitchennotes.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONArray
import org.json.JSONObject

const val DEFAULT_ARK_ENDPOINT = "https://ark.cn-beijing.volces.com/api/v3/chat/completions"
const val DEFAULT_ARK_MODEL = "doubao-seed-2-0-lite-260215"
const val DEEPSEEK_ENDPOINT = "https://api.deepseek.com/chat/completions"
const val DEFAULT_DEEPSEEK_MODEL = "deepseek-v4-flash"
const val CUSTOM_AI_PROVIDER = "自定义兼容接口"
const val CUSTOM_AI_MODEL = "自定义模型"

data class AiProviderPreset(val name: String, val endpoint: String, val models: List<String>)

val aiProviderPresets = listOf(
    AiProviderPreset("OpenAI", "https://api.openai.com/v1/chat/completions", listOf("gpt-5-mini", "gpt-4.1-mini")),
    AiProviderPreset("DeepSeek", DEEPSEEK_ENDPOINT, listOf("deepseek-v4-flash", "deepseek-v4-pro")),
    AiProviderPreset("阿里云百炼", "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions", listOf("qwen-plus", "qwen-turbo")),
    AiProviderPreset("火山方舟", DEFAULT_ARK_ENDPOINT, listOf(DEFAULT_ARK_MODEL))
)

data class AiServiceConfig(
    val endpoint: String = DEFAULT_ARK_ENDPOINT,
    val model: String = DEFAULT_ARK_MODEL,
    val apiKey: String = ""
) {
    val isComplete: Boolean get() = endpoint.startsWith("https://") && model.isNotBlank() && apiKey.isNotBlank()
}

/** The fields returned by the model before they are safely merged into a recipe draft. */
data class RecipeAnalysis(
    val name: String = "",
    val ingredients: List<Ingredient> = emptyList(),
    val steps: List<RecipeStep> = emptyList(),
    val notes: String = "",
    val cuisineTags: Set<String> = emptySet(),
    val featureTags: Set<String> = emptySet()
)

/**
 * Stores the provider endpoint and model normally, while encrypting the personal API key with
 * an AES key held by Android Keystore. The key is deliberately never included in backups.
 */
class AiServiceSettings(context: Context) {
    private val preferences = context.getSharedPreferences("ai_service", Context.MODE_PRIVATE)

    fun read(): AiServiceConfig = AiServiceConfig(
        endpoint = preferences.getString("endpoint", DEFAULT_ARK_ENDPOINT).orEmpty(),
        model = preferences.getString("model", DEFAULT_ARK_MODEL).orEmpty(),
        apiKey = preferences.getString("key_endpoint", null)
            ?.takeIf { it == preferences.getString("endpoint", DEFAULT_ARK_ENDPOINT) }
            ?.let { decrypt(preferences.getString("key_ciphertext", null), preferences.getString("key_iv", null)) }
            .orEmpty()
    )

    fun save(endpoint: String, model: String, apiKey: String?) {
        val normalizedEndpoint = endpoint.trim()
        val endpointChanged = preferences.getString("endpoint", DEFAULT_ARK_ENDPOINT) != normalizedEndpoint
        val editor = preferences.edit()
            .putString("endpoint", normalizedEndpoint)
            .putString("model", model.trim())
        if (apiKey != null) {
            if (apiKey.isBlank()) editor.remove("key_ciphertext").remove("key_iv")
            else {
                val encrypted = encrypt(apiKey.trim())
                editor.putString("key_ciphertext", encrypted.first).putString("key_iv", encrypted.second)
                    .putString("key_endpoint", normalizedEndpoint)
            }
        } else if (endpointChanged) editor.remove("key_ciphertext").remove("key_iv").remove("key_endpoint")
        editor.apply()
    }

    private fun encrypt(value: String): Pair<String, String> {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        return Base64.encodeToString(cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8)), Base64.NO_WRAP) to
            Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
    }

    private fun decrypt(ciphertext: String?, iv: String?): String {
        if (ciphertext.isNullOrBlank() || iv.isNullOrBlank()) return ""
        return runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey(),
                GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))
            )
            String(cipher.doFinal(Base64.decode(ciphertext, Base64.NO_WRAP)), StandardCharsets.UTF_8)
        }.getOrDefault("")
    }

    private fun secretKey(): javax.crypto.SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? javax.crypto.SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
            generateKey()
        }
    }

    private companion object { const val KEY_ALIAS = "savor_notes_ai_api_key" }
}

/** Calls an OpenAI-compatible Chat Completions endpoint to normalize copied recipe text. */
class OpenAiRecipeAnalyzer(private val config: AiServiceConfig) {
    fun analyzeCopiedText(source: String): RecipeAnalysis {
        require(config.isComplete) { "请先在设置中配置可用的 AI 服务、模型和 API Key" }
        require(source.isNotBlank()) { "请先粘贴需要整理的菜谱文字" }
        require(source.length <= 100_000) { "文字内容过长，请控制在 10 万字以内" }
        val prompt = """
            你是一位严谨的中文菜谱整理助手。请根据用户粘贴的菜谱相关文字整理菜谱。
            只输出一个 JSON 对象，不要 Markdown、解释或代码围栏。不要猜测原文没有明确给出的用量；未知用量填空字符串。
            JSON 格式必须是：
            {"name":"","ingredients":[{"name":"","amount":"","unit":"","category":"主料|配料|佐料"}],"steps":[""],"notes":"","cuisineTags":[""],"featureTags":[""]}
            cuisineTags 和 featureTags 仅从以下标签中选择：${(defaultCuisineTags + defaultFeatureTags).joinToString("、")}。

            以下是待整理内容：
            $source
        """.trimIndent()
        return parseRecipeAnalysis(complete(prompt))
    }

    fun testConnection() {
        if (complete("请只回复：连接成功").isBlank()) error("服务返回了空内容")
    }

    private fun complete(prompt: String): String {
        require(config.isComplete) { "接口地址、模型或 API Key 不完整" }
        val body = JSONObject().apply {
            put("model", config.model)
            put("stream", false)
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", prompt)
            }))
        }
        val connection = (URL(config.endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 180_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Authorization", "Bearer ${config.apiKey}")
        }
        return try {
            connection.outputStream.use { it.write(body.toString().toByteArray(StandardCharsets.UTF_8)) }
            val response = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            val text = response?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (connection.responseCode !in 200..299) error("AI 服务返回 ${connection.responseCode}：${text.take(240)}")
            JSONObject(text).optJSONArray("choices")?.optJSONObject(0)
                ?.optJSONObject("message")?.optString("content").orEmpty()
        } finally {
            connection.disconnect()
        }
    }
}

internal fun parseRecipeAnalysis(content: String): RecipeAnalysis {
    val jsonText = content.removePrefix("```json").removePrefix("```").removeSuffix("```").trim().let { raw ->
        raw.substring(raw.indexOf('{').takeIf { it >= 0 } ?: 0, raw.lastIndexOf('}').takeIf { it >= 0 }?.plus(1) ?: raw.length)
    }
    val json = runCatching { JSONObject(jsonText) }.getOrElse { error("AI 未返回可识别的菜谱数据，请重试") }
    val ingredients = json.optJSONArray("ingredients").toObjects { item ->
        Ingredient(
            item.optString("name").trim(), item.optString("amount").trim(), item.optString("unit").trim(),
            IngredientCategory.from(item.optString("category"))
        )
    }.filter { it.name.isNotBlank() }.take(40)
    val steps = json.optJSONArray("steps").toStrings().map { RecipeStep(it.trim()) }.filter { it.text.isNotBlank() }.take(30)
    val allowedTags = (defaultCuisineTags + defaultFeatureTags).toSet()
    val cuisine = json.optJSONArray("cuisineTags").toStrings().map(String::trim).filter { it in defaultCuisineTags }.toSet()
    val feature = json.optJSONArray("featureTags").toStrings().map(String::trim).filter { it in allowedTags - defaultCuisineTags }.toSet()
    val result = RecipeAnalysis(json.optString("name").trim(), ingredients, steps, json.optString("notes").trim(), cuisine, feature)
    if (result.name.isBlank() && result.ingredients.isEmpty() && result.steps.isEmpty()) error("AI 未能整理出菜谱，请检查粘贴的分析内容")
    return result
}

private fun JSONArray?.toStrings(): List<String> = this?.let { array -> List(array.length()) { array.optString(it) } } ?: emptyList()
private fun <T> JSONArray?.toObjects(transform: (JSONObject) -> T): List<T> = this?.let { array ->
    List(array.length()) { index -> array.optJSONObject(index)?.let(transform) }.filterNotNull()
} ?: emptyList()
