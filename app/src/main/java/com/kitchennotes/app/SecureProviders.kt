package com.kitchennotes.app

import android.content.Context
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class ProviderConfig(val id: String, val name: String, val baseUrl: String, val apiKey: String = "", val model: String = "") {
    fun toPublicJson() = JSONObject().put("id", id).put("name", name).put("baseUrl", baseUrl).put("model", model)
    companion object { fun fromJson(o: JSONObject, key: String) = ProviderConfig(o.getString("id"), o.getString("name"), o.getString("baseUrl"), key, o.optString("model")) }
}

val providerTemplates = listOf(
    ProviderConfig("dashscope", "通义千问", "https://dashscope.aliyuncs.com/compatible-mode/v1"),
    ProviderConfig("deepseek", "DeepSeek", "https://api.deepseek.com"),
    ProviderConfig("zhipu", "智谱 AI", "https://open.bigmodel.cn/api/paas/v4"),
    ProviderConfig("minimax", "MiniMax", "https://api.minimax.chat/v1"),
    ProviderConfig("moonshot", "月之暗面", "https://api.moonshot.cn/v1")
)

class ProviderStore(context: Context) {
    private val secure = EncryptedSharedPreferences.create(context, "ai_secrets", MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(), EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    fun load(): List<ProviderConfig> = runCatching {
        val arr = JSONArray(secure.getString("providers", "[]")); List(arr.length()) { n ->
            val public = arr.getJSONObject(n); ProviderConfig.fromJson(public, secure.getString("key_${public.getString("id")}", "") ?: "")
        }
    }.getOrDefault(emptyList())
    fun save(items: List<ProviderConfig>) {
        secure.edit {
            putString("providers", JSONArray(items.map { it.toPublicJson() }).toString())
            items.forEach { putString("key_${it.id}", it.apiKey) }
        }
    }
    fun default(): ProviderConfig? = load().firstOrNull { it.id == secure.getString("default_provider", "") }
    fun setDefault(id: String) { secure.edit { putString("default_provider", id) } }
}

object AiClient {
    fun test(config: ProviderConfig): Result<List<String>> = runCatching {
        require(config.apiKey.isNotBlank()) { "请先填写 API Key" }
        val connection = (URL(config.baseUrl.trimEnd('/') + "/models").openConnection() as HttpURLConnection).apply { requestMethod = "GET"; setRequestProperty("Authorization", "Bearer ${config.apiKey}"); connectTimeout = 10_000; readTimeout = 10_000 }
        require(connection.responseCode in 200..299) { "服务返回 ${connection.responseCode}" }
        val body = connection.inputStream.bufferedReader().readText(); val data = JSONObject(body).optJSONArray("data") ?: JSONArray()
        List(data.length()) { data.getJSONObject(it).optString("id") }.filter { it.isNotBlank() }
    }
}
