package com.kitchennotes.app

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.rememberNavigationSuiteScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

class MainActivity : ComponentActivity() {
    private var sharedUrl by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        acceptShareIntent(intent)
        setContent {
            val factory = viewModelFactory {
                initializer { RecipeViewModel(application, createSavedStateHandle()) }
            }
            val vm: RecipeViewModel = viewModel(factory = factory)
            SavorNotesTheme {
                SavorNotesApp(vm, sharedUrl) { sharedUrl = null }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        acceptShareIntent(intent)
    }

    private fun acceptShareIntent(intent: Intent?) {
        sharedUrl = if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            extractSharedHttpsUrl(intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty())
        } else {
            null
        }
    }
}

class RecipeViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {
    private val repo = RecipeRepository(getApplication<Application>())
    private val backup = BackupManager(getApplication<Application>())
    private val providerStore = ProviderStore(getApplication<Application>())

    var recipes by mutableStateOf(repo.load())
        private set
    var customTags by mutableStateOf(
        getApplication<Application>().getSharedPreferences("recipes", Context.MODE_PRIVATE)
            .getStringSet("custom_tags", emptySet()) ?: emptySet()
    )
        private set
    var removedDefaultTags by mutableStateOf(
        getApplication<Application>().getSharedPreferences("recipes", Context.MODE_PRIVATE)
            .getStringSet("removed_default_tags", emptySet()) ?: emptySet()
    )
        private set
    val availableDefaultTags: Set<String>
        get() = (defaultCuisineTags + defaultFeatureTags).toSet() - removedDefaultTags
    var providers by mutableStateOf(providerStore.load())
        private set
    var message by mutableStateOf<String?>(
        if (repo.recoveryAvailable) "检测到损坏的菜谱数据，原始内容已保留为恢复副本" else null
    )

    var editorDraft by mutableStateOf(
        savedStateHandle.get<String>(EDITOR_DRAFT_KEY)?.let { raw ->
            runCatching { Recipe.fromJson(org.json.JSONObject(raw)) }.getOrNull()
        }
    )
        private set
    private var editorBaseline: Recipe? = savedStateHandle.get<String>(EDITOR_BASELINE_KEY)?.let { raw ->
        runCatching { Recipe.fromJson(org.json.JSONObject(raw)) }.getOrNull()
    } ?: editorDraft?.let { draft -> recipes.firstOrNull { it.id == draft.id } ?: draft }
    var stepImageCandidate by mutableStateOf(savedStateHandle.get<String>(STEP_IMAGE_CANDIDATE_KEY).orEmpty())
        private set
    val hasUnsavedChanges: Boolean
        get() = editorDraft != editorBaseline

    private var pendingCameraPath: String?
        get() = savedStateHandle[PENDING_CAMERA_PATH]
        set(value) { savedStateHandle[PENDING_CAMERA_PATH] = value }
    private var pendingCameraTarget: String?
        get() = savedStateHandle[PENDING_CAMERA_TARGET]
        set(value) { savedStateHandle[PENDING_CAMERA_TARGET] = value }

    fun beginEditing(recipe: Recipe?, sharedVideoUrl: String? = null) {
        val initial = recipe ?: Recipe(
                name = "",
                videos = sharedVideoUrl?.let { listOf(ReferenceVideo(it)) }.orEmpty()
            )
        setBaseline(initial)
        setDraft(initial)
        replaceStepImageCandidate("")
    }

    fun updateDraft(change: (Recipe) -> Recipe) {
        editorDraft?.let { setDraft(change(it)) }
    }

    fun discardDraft() {
        val candidates = editorDraft?.imagePaths().orEmpty() + stepImageCandidate
        setDraft(null)
        setBaseline(null)
        stepImageCandidate = ""
        savedStateHandle[STEP_IMAGE_CANDIDATE_KEY] = null
        cleanupUnreferenced(candidates, recipes)
    }

    fun saveDraft(): String? {
        val draft = editorDraft ?: return null
        if (draft.name.isBlank()) {
            message = "请填写菜名"
            return null
        }
        if (draft.ingredients.any { it.name.isBlank() }) {
            message = "食材名称不能为空；用量和单位可以不填"
            return null
        }
        save(draft)
        discardDraft()
        return draft.id
    }

    fun save(recipe: Recipe) {
        val old = recipes.firstOrNull { it.id == recipe.id }
        val saved = recipe.copy(updatedAt = System.currentTimeMillis())
        val updated = (recipes.filterNot { it.id == saved.id } + saved)
            .sortedByDescending { it.updatedAt }
        repo.save(updated)
        recipes = updated
        cleanupUnreferenced(old?.imagePaths().orEmpty() - saved.imagePaths(), updated)
    }

    fun delete(id: String) {
        val removed = recipes.firstOrNull { it.id == id } ?: return
        val updated = recipes.filterNot { it.id == id }
        repo.save(updated)
        recipes = updated
        cleanupUnreferenced(removed.imagePaths(), updated)
        if (editorDraft?.id == id) discardDraft()
    }

    fun cooked(recipe: Recipe) = save(
        recipe.copy(
            cookCount = recipe.cookCount + 1,
            lastCookedAt = System.currentTimeMillis()
        )
    )

    fun favorite(recipe: Recipe) = save(recipe.copy(favorite = !recipe.favorite))

    fun addTag(tag: String) {
        val normalized = tag.trim()
        if (normalized.isNotBlank()) replaceCustomTags(customTags + normalized)
    }

    fun removeTag(tag: String) {
        val updatedRecipes = recipes.map { recipe ->
            if (tag in recipe.featureTags) {
                recipe.copy(
                    featureTags = recipe.featureTags - tag,
                    updatedAt = System.currentTimeMillis()
                )
            } else recipe
        }
        repo.save(updatedRecipes)
        recipes = updatedRecipes
        updateDraft { it.copy(featureTags = it.featureTags - tag) }
        replaceCustomTags(customTags - tag)
    }

    fun removeDefaultTag(tag: String) {
        val allDefaults = (defaultCuisineTags + defaultFeatureTags).toSet()
        if (tag !in allDefaults || tag in removedDefaultTags) return
        applyRemovedDefaultTags(removedDefaultTags + tag)
    }

    fun renameTag(old: String, new: String) {
        val normalized = new.trim()
        if (normalized.isBlank() || normalized == old) return
        val updatedRecipes = recipes.map { recipe ->
            if (old in recipe.featureTags) {
                recipe.copy(
                    featureTags = recipe.featureTags - old + normalized,
                    updatedAt = System.currentTimeMillis()
                )
            } else recipe
        }
        repo.save(updatedRecipes)
        recipes = updatedRecipes
        replaceCustomTags(customTags - old + normalized)
    }

    private fun replaceCustomTags(tags: Set<String>) {
        customTags = tags
        getApplication<Application>().getSharedPreferences("recipes", Context.MODE_PRIVATE)
            .edit { putStringSet("custom_tags", tags) }
    }

    private fun applyRemovedDefaultTags(tags: Set<String>) {
        val validTags = tags.intersect((defaultCuisineTags + defaultFeatureTags).toSet())
        removedDefaultTags = validTags
        getApplication<Application>().getSharedPreferences("recipes", Context.MODE_PRIVATE)
            .edit { putStringSet("removed_default_tags", validTags) }

        val now = System.currentTimeMillis()
        val updatedRecipes = recipes.map { recipe ->
            val cuisineTags = recipe.cuisineTags - validTags
            val featureTags = recipe.featureTags - validTags
            if (cuisineTags != recipe.cuisineTags || featureTags != recipe.featureTags) {
                recipe.copy(cuisineTags = cuisineTags, featureTags = featureTags, updatedAt = now)
            } else recipe
        }
        if (updatedRecipes != recipes) {
            repo.save(updatedRecipes)
            recipes = updatedRecipes
        }
        updateDraft { draft ->
            draft.copy(
                cuisineTags = draft.cuisineTags - validTags,
                featureTags = draft.featureTags - validTags
            )
        }
    }

    fun copyImage(uri: Uri, target: PhotoTarget) = runIo(
        "图片保存失败",
        { backup.copyImage(uri) }
    ) { path ->
        if (target == PhotoTarget.COVER) applyCoverPhoto(path) else replaceStepImageCandidate(path)
    }

    fun prepareCamera(target: PhotoTarget): Uri {
        pendingCameraPath?.let(backup::deleteImage)
        val capture = backup.createCameraCapture()
        pendingCameraPath = capture.path
        pendingCameraTarget = target.name
        return capture.uri
    }

    fun finishCamera(success: Boolean) {
        val path = pendingCameraPath
        val target = pendingCameraTarget?.let { runCatching { PhotoTarget.valueOf(it) }.getOrNull() }
        if (success && path != null && target != null) {
            if (target == PhotoTarget.COVER) applyCoverPhoto(path) else replaceStepImageCandidate(path)
        }
        else path?.let(backup::deleteImage)
        pendingCameraPath = null
        pendingCameraTarget = null
    }

    fun replaceStepImageCandidate(path: String) {
        val previous = stepImageCandidate
        stepImageCandidate = path
        savedStateHandle[STEP_IMAGE_CANDIDATE_KEY] = path.ifBlank { null }
        if (previous.isNotBlank() && previous != path) {
            cleanupUnreferenced(setOf(previous), recipes + listOfNotNull(editorDraft, editorBaseline))
        }
    }

    fun consumeStepImageCandidate(): String = stepImageCandidate.also {
        stepImageCandidate = ""
        savedStateHandle[STEP_IMAGE_CANDIDATE_KEY] = null
    }

    private fun applyCoverPhoto(path: String) {
        updateDraft { draft -> draft.copy(coverPath = path) }
    }

    fun export(uri: Uri) = runIo(
        "备份失败",
        { backup.exportTo(uri, recipes, customTags, removedDefaultTags) }
    ) { message = "备份完成" }

    fun import(uri: Uri) = runIo("恢复失败", {
        val payload = backup.importFrom(uri)
        val merged = repo.merge(payload.recipes)
        payload to merged
    }) { (payload, merged) ->
        recipes = merged
        replaceCustomTags(customTags + payload.customTags)
        applyRemovedDefaultTags(removedDefaultTags + payload.removedDefaultTags)
        message = "已智能合并 ${payload.recipes.size} 条菜谱"
    }

    fun provider(id: String): ProviderConfig =
        providers.firstOrNull { it.id == id }
            ?: providerTemplates.firstOrNull { it.id == id }
            ?: ProviderConfig(id, "自定义兼容服务", "")

    fun saveProvider(config: ProviderConfig) {
        providers = providers.filterNot { it.id == config.id } + config
        providerStore.save(providers)
    }

    fun makeDefault(id: String) {
        providerStore.setDefault(id)
        message = "已设为默认模型服务"
    }

    fun testProvider(config: ProviderConfig, result: (List<String>) -> Unit) = runIo(
        "连通失败",
        { AiClient.test(config).getOrThrow() }
    ) { models ->
        result(models)
        message = "连通成功，找到 ${models.size} 个模型"
    }

    fun resolveVideo(input: String, result: (ReferenceVideo) -> Unit) = runIo("视频解析失败", {
        val url = extractSharedHttpsUrl(input) ?: error("请输入有效的 HTTPS 分享链接")
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        try {
            require(connection.responseCode in 200..299) { "页面返回 ${connection.responseCode}" }
            val html = connection.inputStream.bufferedReader().use { it.readText() }
            val title = Regex("<title[^>]*>(.*?)</title>", RegexOption.IGNORE_CASE)
                .find(html)?.groupValues?.get(1)
                ?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
            val cover = listOf(
                Regex("(?:property|name)=[\"']og:image[\"'][^>]*content=[\"']([^\"']+)", RegexOption.IGNORE_CASE),
                Regex("content=[\"']([^\"']+)[\"'][^>]*(?:property|name)=[\"']og:image[\"']", RegexOption.IGNORE_CASE)
            ).firstNotNullOfOrNull { it.find(html)?.groupValues?.get(1) }.orEmpty()
            ReferenceVideo(url, title.ifBlank { "抖音参考视频" }, cover)
        } finally {
            connection.disconnect()
        }
    }) { video ->
        result(video)
        if (video.title == "抖音参考视频") message = "未能读取公开视频信息，已保存链接"
    }

    fun createAiDraft(input: String, current: Recipe, result: (Recipe) -> Unit) {
        val url = extractSharedHttpsUrl(input)
        if (url == null) {
            message = "请输入有效的 HTTPS 分享链接"
            return
        }
        val provider = providerStore.default()
        if (provider == null || provider.apiKey.isBlank() || provider.model.isBlank()) {
            message = "请先在设置中配置并设定默认 AI 服务"
            return
        }
        runIo("AI 总结失败", {
            val prompt = "根据这个公开视频链接尝试整理菜谱：$url。仅输出 JSON：{name,ingredients:[{name,amount,unit}],steps:[string],notes,tags:[string]}。无法确认时留空，不要编造。"
            val body = org.json.JSONObject()
                .put("model", provider.model)
                .put("messages", org.json.JSONArray().put(
                    org.json.JSONObject().put("role", "user").put("content", prompt)
                )).toString()
            val connection = (
                URL(provider.baseUrl.trimEnd('/') + "/chat/completions").openConnection()
                    as HttpURLConnection
                ).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer ${provider.apiKey}")
                connectTimeout = 20_000
                readTimeout = 40_000
            }
            try {
                connection.outputStream.bufferedWriter().use { it.write(body) }
                require(connection.responseCode in 200..299) { "服务返回 ${connection.responseCode}" }
                val raw = org.json.JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                    .getJSONArray("choices").getJSONObject(0).getJSONObject("message")
                    .optString("content").trim()
                    .removePrefix("```json").removePrefix("```").trim()
                    .removeSuffix("```").trim()
                val json = org.json.JSONObject(raw)
                val ingredients = json.optJSONArray("ingredients")?.let { array ->
                    List(array.length()) { Ingredient.fromJson(array.getJSONObject(it)) }
                }.orEmpty()
                val steps = json.optJSONArray("steps")?.let { array ->
                    List(array.length()) { RecipeStep(array.optString(it)) }
                }.orEmpty()
                val tags = json.optJSONArray("tags")?.let { array ->
                    List(array.length()) { array.optString(it) }.filter(String::isNotBlank).toSet()
                }.orEmpty()
                current.copy(
                    name = json.optString("name").ifBlank { current.name },
                    ingredients = ingredients.ifEmpty { current.ingredients },
                    steps = steps.ifEmpty { current.steps },
                    notes = json.optString("notes").ifBlank { current.notes },
                    featureTags = current.featureTags + tags,
                    videos = current.videos.filterNot { it.url == url } + ReferenceVideo(url)
                )
            } finally {
                connection.disconnect()
            }
        }) { draft ->
            result(draft)
            message = "AI 草稿已生成，请逐项确认后保存"
        }
    }

    override fun onCleared() {
        pendingCameraPath?.let(backup::deleteImage)
        stepImageCandidate.takeIf(String::isNotBlank)?.let { candidate ->
            val referenced = recipes.flatMap { it.imagePaths() }.toSet() + editorDraft?.imagePaths().orEmpty()
            if (candidate !in referenced) backup.deleteImage(candidate)
        }
        super.onCleared()
    }

    private fun setDraft(recipe: Recipe?) {
        editorDraft = recipe
        savedStateHandle[EDITOR_DRAFT_KEY] = recipe?.toJson()?.toString()
    }

    private fun setBaseline(recipe: Recipe?) {
        editorBaseline = recipe
        savedStateHandle[EDITOR_BASELINE_KEY] = recipe?.toJson()?.toString()
    }

    private fun cleanupUnreferenced(candidates: Set<String>, current: List<Recipe>) {
        if (candidates.isEmpty()) return
        val referenced = current.flatMap { it.imagePaths() }.toSet()
        val removable = candidates - referenced
        if (removable.isNotEmpty()) runIo("图片清理失败", {
            removable.forEach(backup::deleteImage)
        })
    }

    private fun <T> runIo(errorPrefix: String, work: () -> T, success: (T) -> Unit = {}) {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { work() } }
                .onSuccess(success)
                .onFailure { message = "$errorPrefix：${it.message ?: "未知错误"}" }
        }
    }

    private fun Recipe.imagePaths(): Set<String> =
        (listOf(coverPath) + steps.map { it.imagePath }).filter(String::isNotBlank).toSet()

    companion object {
        private const val EDITOR_DRAFT_KEY = "editor_draft"
        private const val EDITOR_BASELINE_KEY = "editor_baseline"
        private const val STEP_IMAGE_CANDIDATE_KEY = "step_image_candidate"
        private const val PENDING_CAMERA_PATH = "pending_camera_path"
        private const val PENDING_CAMERA_TARGET = "pending_camera_target"
    }
}

enum class PhotoTarget { COVER, STEP }

@Serializable data object RecipesRoute : NavKey
@Serializable data object SettingsRoute : NavKey
@Serializable data class RecipeDetailRoute(val recipeId: String) : NavKey
@Serializable data class RecipeEditorRoute(val recipeId: String? = null) : NavKey
@Serializable data class ProviderEditorRoute(val providerId: String) : NavKey

private data class TopDestination(
    val route: NavKey,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

private val topDestinations = listOf(
    TopDestination(RecipesRoute, "菜谱", Icons.Default.Home),
    TopDestination(SettingsRoute, "设置", Icons.Default.Settings)
)

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun SavorNotesApp(
    vm: RecipeViewModel,
    sharedLink: String?,
    onSharedLinkConsumed: () -> Unit
) {
    val backStack = rememberNavBackStack(RecipesRoute)
    val snackbar = remember { SnackbarHostState() }
    var discardRequested by rememberSaveable { mutableStateOf(false) }
    val navScaffoldState = rememberNavigationSuiteScaffoldState()
    val current = backStack.lastOrNull()
    val windowWidth = with(LocalDensity.current) {
        LocalWindowInfo.current.containerSize.width.toDp()
    }
    val isCompact = windowWidth < 840.dp
    val hideNavigation = current is RecipeEditorRoute ||
        current is ProviderEditorRoute || (isCompact && current is RecipeDetailRoute)

    LaunchedEffect(vm.message) {
        vm.message?.let {
            snackbar.showSnackbar(it)
            vm.message = null
        }
    }
    LaunchedEffect(hideNavigation) {
        if (hideNavigation) navScaffoldState.hide() else navScaffoldState.show()
    }
    LaunchedEffect(sharedLink) {
        if (sharedLink != null) {
            vm.beginEditing(null, sharedLink)
            backStack.clear()
            backStack.add(RecipesRoute)
            backStack.add(RecipeEditorRoute())
            onSharedLinkConsumed()
        }
    }

    val listDetailStrategy = rememberListDetailSceneStrategy<NavKey>()
    NavigationSuiteScaffold(
        state = navScaffoldState,
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background,
        navigationSuiteItems = {
            topDestinations.forEach { destination ->
                val selected = when (destination.route) {
                    RecipesRoute -> current is RecipesRoute || current is RecipeDetailRoute
                    else -> current == destination.route
                }
                item(
                    selected = selected,
                    onClick = {
                        backStack.clear()
                        backStack.add(destination.route)
                    },
                    icon = { Icon(destination.icon, null) },
                    label = { Text(destination.label) }
                )
            }
        }
    ) {
        Scaffold(
            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbar) }
        ) { padding ->
            NavDisplay(
                backStack = backStack,
                onBack = {
                    if (current is RecipeEditorRoute && vm.hasUnsavedChanges) discardRequested = true
                    else if (current is RecipeEditorRoute) {
                        vm.discardDraft()
                        backStack.removeLastOrNull()
                    }
                    else backStack.removeLastOrNull()
                },
                modifier = androidx.compose.ui.Modifier.padding(padding),
                sceneStrategy = listDetailStrategy,
                entryProvider = entryProvider {
                    entry<RecipesRoute>(
                        metadata = ListDetailSceneStrategy.listPane(
                            detailPlaceholder = { RecipeDetailPlaceholder() }
                        )
                    ) {
                        HomeScreen(
                            recipes = vm.recipes,
                            vm = vm,
                            onOpen = { recipe -> backStack.add(RecipeDetailRoute(recipe.id)) },
                            onAdd = {
                                vm.beginEditing(null)
                                backStack.add(RecipeEditorRoute())
                            }
                        )
                    }
                    entry<RecipeDetailRoute>(
                        metadata = ListDetailSceneStrategy.detailPane()
                    ) { route ->
                        val recipe = vm.recipes.firstOrNull { it.id == route.recipeId }
                        if (recipe == null) RecipeDetailPlaceholder()
                        else RecipeDetailScreen(
                            recipe = recipe,
                            vm = vm,
                            showBack = isCompact,
                            onBack = { backStack.removeLastOrNull() },
                            onEdit = {
                                vm.beginEditing(recipe)
                                backStack.add(RecipeEditorRoute(recipe.id))
                            }
                        )
                    }
                    entry<RecipeEditorRoute> { route ->
                        LaunchedEffect(route) {
                            if (vm.editorDraft == null) {
                                vm.beginEditing(vm.recipes.firstOrNull { it.id == route.recipeId })
                            }
                        }
                        RecipeEditorScreen(
                            vm = vm,
                            discardRequested = discardRequested,
                            onDiscardRequestHandled = { discardRequested = false },
                            onSaved = { backStack.removeLastOrNull() },
                            onDeleted = {
                                backStack.removeAll { it is RecipeDetailRoute || it is RecipeEditorRoute }
                                if (backStack.isEmpty()) backStack.add(RecipesRoute)
                            },
                            onDiscard = {
                                vm.discardDraft()
                                backStack.removeLastOrNull()
                            }
                        )
                    }
                    entry<SettingsRoute> {
                        SettingsScreen(
                            vm = vm,
                            onEditProvider = { id -> backStack.add(ProviderEditorRoute(id)) }
                        )
                    }
                    entry<ProviderEditorRoute> { route ->
                        ProviderEditorScreen(
                            initial = vm.provider(route.providerId),
                            vm = vm,
                            onBack = { backStack.removeLastOrNull() }
                        )
                    }
                }
            )
        }
    }
}
