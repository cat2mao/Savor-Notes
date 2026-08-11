package com.kitchennotes.app

import android.content.Context
import android.net.Uri
import java.io.File
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class BackupManager(private val context: Context) {
    fun exportTo(
        target: Uri,
        recipes: List<Recipe>,
        customTags: Set<String>,
        removedDefaultTags: Set<String>
    ) {
        context.contentResolver.openOutputStream(target)?.use { out ->
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry("recipes.json")); zip.write(org.json.JSONArray(recipes.map { it.toJson() }).toString().toByteArray()); zip.closeEntry()
                zip.putNextEntry(ZipEntry("manifest.json")); zip.write(
                    org.json.JSONObject()
                        .put("version", 2)
                        .put("customTags", org.json.JSONArray(customTags))
                        .put("removedDefaultTags", org.json.JSONArray(removedDefaultTags))
                        .toString()
                        .toByteArray()
                ); zip.closeEntry()
                recipes.flatMap { recipe -> listOf(recipe.coverPath) + recipe.steps.map { it.imagePath } }.filter { it.isNotBlank() }.distinct().forEach { path ->
                    val file = File(path); if (file.exists()) {
                        zip.putNextEntry(ZipEntry("images/${file.name}")); file.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
                    }
                }
            }
        } ?: error("无法写入备份位置")
    }

    fun importFrom(source: Uri): BackupPayload {
        var json: String? = null
        var manifestJson: String? = null
        val restoredImages = mutableMapOf<String, String>()
        ZipInputStream(context.contentResolver.openInputStream(source) ?: error("无法打开备份文件")).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entry ->
                if (entry.name == "recipes.json") json = zip.readEntryText()
                else if (entry.name == "manifest.json") manifestJson = zip.readEntryText()
                else if (entry.name.startsWith("images/")) {
                    val output = File(context.filesDir, "images/${entry.name.substringAfterLast('/')}").apply { parentFile?.mkdirs() }
                    output.outputStream().use { zip.copyTo(it) }
                    restoredImages[output.name] = output.absolutePath
                }
                zip.closeEntry()
            }
        }
        val array = org.json.JSONArray(json ?: error("备份中没有菜谱数据"))
        val recipes = List(array.length()) {
            val recipe = Recipe.fromJson(array.getJSONObject(it))
            recipe.copy(
                coverPath = restoredImages[File(recipe.coverPath).name] ?: recipe.coverPath,
                steps = recipe.steps.map { step ->
                    step.copy(imagePath = restoredImages[File(step.imagePath).name] ?: step.imagePath)
                }
            )
        }
        val customTags = manifestJson?.let { org.json.JSONObject(it).optJSONArray("customTags") }?.let { tags -> List(tags.length()) { tags.optString(it) }.filter { it.isNotBlank() }.toSet() }.orEmpty()
        val removedDefaultTags = manifestJson?.let { org.json.JSONObject(it).optJSONArray("removedDefaultTags") }?.let { tags -> List(tags.length()) { tags.optString(it) }.filter { it.isNotBlank() }.toSet() }.orEmpty()
        return BackupPayload(recipes, customTags, removedDefaultTags)
    }

    fun copyImage(uri: Uri): String {
        val destination = imageFile("gallery")
        val input = context.contentResolver.openInputStream(uri) ?: error("无法读取所选图片")
        input.use { stream -> destination.outputStream().use { stream.copyTo(it) } }
        return destination.absolutePath
    }

    fun createCameraCapture(): CameraCapture {
        val destination = imageFile("camera")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", destination)
        return CameraCapture(uri, destination.absolutePath)
    }

    fun deleteImage(path: String) {
        if (path.isBlank()) return
        val imagesDir = File(context.filesDir, "images").canonicalFile
        val target = File(path).canonicalFile
        if (target.parentFile == imagesDir && target.isFile) target.delete()
    }

    private fun imageFile(source: String) = File(
        context.filesDir,
        "images/${UUID.randomUUID()}_$source.jpg"
    ).apply { parentFile?.mkdirs() }
}

data class BackupPayload(
    val recipes: List<Recipe>,
    val customTags: Set<String>,
    val removedDefaultTags: Set<String> = emptySet()
)
data class CameraCapture(val uri: Uri, val path: String)

private fun ZipInputStream.readEntryText(): String = ByteArrayOutputStream().use { output ->
    copyTo(output)
    output.toString(Charsets.UTF_8.name())
}
