@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.kitchennotes.app

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.LocalDining
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    recipes: List<Recipe>,
    vm: RecipeViewModel,
    onOpen: (Recipe) -> Unit,
    onAdd: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedTags by rememberSaveable { mutableStateOf(setOf<String>()) }
    var mode by rememberSaveable { mutableStateOf("全部") }
    var listMode by rememberSaveable { mutableStateOf(false) }
    val gridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()
    val isScrolledDown by remember {
        derivedStateOf { gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 24 }
    }
    val categoryTags = listOf("家常", "凉菜", "川菜", "热菜", "低脂", "快手")
        .filter { it in vm.availableDefaultTags }
    LaunchedEffect(vm.availableDefaultTags) {
        selectedTags = selectedTags.intersect(vm.availableDefaultTags)
    }
    val shown = remember(recipes, query, selectedTags, mode) {
        recipes.filter { recipe ->
            (query.isBlank() || recipe.searchableText().contains(query.lowercase())) &&
                selectedTags.all { it in recipe.cuisineTags || it in recipe.featureTags } &&
                when (mode) {
                    "收藏" -> recipe.favorite
                    "最近" -> recipe.lastCookedAt > 0
                    else -> true
                }
        }.sortedWith(when (mode) {
            "做得最多" -> compareByDescending { it.cookCount }
            "最近" -> compareByDescending { it.lastCookedAt }
            else -> compareByDescending { it.updatedAt }
        })
    }

    BackHandler(enabled = isScrolledDown) {
        coroutineScope.launch { gridState.animateScrollToItem(0) }
    }

    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = if (listMode) GridCells.Fixed(1) else GridCells.Adaptive(156.dp),
            state = gridState,
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 92.dp),
            horizontalArrangement = Arrangement.spacedBy(11.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.padding(top = 14.dp)) {
                    Text(
                        "SAVOR NOTES",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.6.sp
                    )
                    Text("清欢小谱", style = MaterialTheme.typography.headlineLarge)
                    Text("人间有味，且记清欢", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                        placeholder = { Text("搜索菜名、食材或标签") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Close, "清除搜索")
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(15.dp)
                    )
                }
            }

            item(key = "categories", span = { GridItemSpan(maxLineSpan) }) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(horizontal = 1.dp, vertical = 4.dp)
                ) {
                    item {
                        CategoryButton("全部", Icons.Default.Home, selectedTags.isEmpty()) {
                            selectedTags = emptySet()
                        }
                    }
                    items(categoryTags) { tag ->
                        val icon = when (tag) {
                            "川菜" -> Icons.Default.LocalFireDepartment
                            "低脂" -> Icons.Default.LocalDining
                            else -> Icons.Default.Restaurant
                        }
                        CategoryButton(tag, icon, tag in selectedTags) {
                            selectedTags = selectedTags.toggle(tag)
                        }
                    }
                }
            }

            item(key = "recipe_title", span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("菜谱", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(Modifier.padding(3.dp)) {
                            ViewSwitchButton(Icons.Default.GridView, !listMode) { listMode = false }
                            ViewSwitchButton(Icons.AutoMirrored.Filled.List, listMode) { listMode = true }
                        }
                    }
                }
            }

            item(key = "filters", span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("全部", "最近", "收藏", "做得最多").forEach { item ->
                        FilterChip(
                            selected = mode == item,
                            onClick = { mode = item },
                            label = { Text(item) }
                        )
                    }
                    if (selectedTags.isNotEmpty()) AssistChip(
                        onClick = { selectedTags = emptySet() },
                        label = { Text("清除标签") },
                        leadingIcon = { Icon(Icons.Default.Tune, null, Modifier.size(16.dp)) }
                    )
                }
            }

            if (shown.isEmpty()) {
                item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().heightIn(min = 280.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Restaurant, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(10.dp))
                        Text("还没有匹配的菜谱", style = MaterialTheme.typography.titleMedium)
                        Text("点击右下角开始记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                }
            } else {
                items(shown, key = { it.id }) { recipe ->
                    RecipeCard(recipe, listMode, onOpen, vm::cooked)
                }
            }
        }
        FloatingActionButton(
            onClick = {
                if (isScrolledDown) coroutineScope.launch { gridState.animateScrollToItem(0) }
                else onAdd()
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            shape = RoundedCornerShape(17.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(
                if (isScrolledDown) Icons.Default.KeyboardArrowUp else Icons.Default.Add,
                if (isScrolledDown) "回到首页" else "添加菜谱"
            )
        }
    }
}

@Composable
private fun CategoryButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier.width(48.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Surface(
            modifier = Modifier.size(34.dp),
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            shape = RoundedCornerShape(11.dp)
        ) { Box(contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(18.dp)) } }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ViewSwitchButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(30.dp),
        color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
        contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(7.dp),
        shadowElevation = if (selected) 2.dp else 0.dp
    ) { Box(contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(17.dp)) } }
}

@Composable
private fun RecipeCard(
    recipe: Recipe,
    listMode: Boolean,
    open: (Recipe) -> Unit,
    cooked: (Recipe) -> Unit
) {
    ElevatedCard(
        onClick = { open(recipe) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        if (listMode) {
            Row(Modifier.height(84.dp), verticalAlignment = Alignment.CenterVertically) {
                RecipeImage(recipe.coverPath, Modifier.width(92.dp).fillMaxHeight())
                RecipeCardText(recipe, cooked, Modifier.weight(1f).padding(horizontal = 12.dp))
            }
        } else {
            Column {
                RecipeImage(recipe.coverPath, Modifier.fillMaxWidth().aspectRatio(1.38f))
                RecipeCardText(recipe, cooked, Modifier.padding(10.dp))
            }
        }
    }
}

@Composable
private fun RecipeCardText(recipe: Recipe, cooked: (Recipe) -> Unit, modifier: Modifier) {
    Column(modifier) {
        Text(recipe.name, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                (recipe.cuisineTags + recipe.featureTags).take(2).joinToString(" · ").ifBlank { "未分类" },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text("做过 ${recipe.cookCount} 次", style = MaterialTheme.typography.labelMedium)
            IconButton(onClick = { cooked(recipe) }, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Default.AddCircle, "做过一次", Modifier.size(19.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun RecipeDetailPlaceholder() {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Restaurant, null, Modifier.size(54.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            Text("选择一道菜查看详情", style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
fun RecipeDetailScreen(
    recipe: Recipe,
    vm: RecipeViewModel,
    showBack: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit
) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showBack) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
            else Spacer(Modifier.size(48.dp))
            Text("菜谱详情", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            IconButton(onClick = { vm.favorite(recipe) }) {
                Icon(if (recipe.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "收藏", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "编辑", tint = MaterialTheme.colorScheme.primary) }
        }

        RecipeImage(recipe.coverPath, Modifier.fillMaxWidth().height(210.dp).clip(RoundedCornerShape(20.dp)))
        Row(
            Modifier.fillMaxWidth().padding(top = 15.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(Modifier.weight(1f)) {
                Text(recipe.name, style = MaterialTheme.typography.headlineMedium)
                if (recipe.notes.isNotBlank()) Text(
                    recipe.notes.lineSequence().first(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = RoundedCornerShape(11.dp)
            ) { Text("已做 ${recipe.cookCount} 次", Modifier.padding(horizontal = 10.dp, vertical = 7.dp), fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(6.dp))
            FilledIconButton(onClick = { vm.cooked(recipe) }, modifier = Modifier.size(32.dp), shape = RoundedCornerShape(10.dp)) {
                Icon(Icons.Default.Add, "做过一次", Modifier.size(19.dp))
            }
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            (recipe.cuisineTags + recipe.featureTags).forEach { tag -> TagPill(tag) }
        }

        DetailSection("食材") {
            if (recipe.ingredients.isEmpty()) MutedEmpty("暂未记录食材")
            recipe.ingredients.forEachIndexed { index, ingredient ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text(ingredient.name, Modifier.weight(1f))
                    Text("${ingredient.amount}${ingredient.unit}", fontWeight = FontWeight.Bold)
                }
                if (index != recipe.ingredients.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .65f))
            }
        }

        DetailSection("制作步骤") {
            if (recipe.steps.isEmpty()) MutedEmpty("暂未记录步骤")
            recipe.steps.forEachIndexed { index, step ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
                    Surface(
                        modifier = Modifier.size(23.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        shape = RoundedCornerShape(7.dp)
                    ) { Box(contentAlignment = Alignment.Center) { Text("${index + 1}", fontWeight = FontWeight.ExtraBold, fontSize = 11.sp) } }
                    Spacer(Modifier.width(10.dp))
                    Text(step.text, modifier = Modifier.weight(1f), lineHeight = 21.sp)
                    if (step.imagePath.isNotBlank()) {
                        Spacer(Modifier.width(8.dp))
                        RecipeImage(step.imagePath, Modifier.size(88.dp).clip(RoundedCornerShape(12.dp)))
                    }
                }
            }
        }

        if (recipe.notes.isNotBlank()) DetailSection("注意事项") {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(14.dp)) {
                Text(recipe.notes, Modifier.fillMaxWidth().padding(12.dp))
            }
        }

        if (recipe.videos.isNotEmpty()) DetailSection("参考视频") {
            recipe.videos.forEach { video ->
                OutlinedCard(
                    onClick = { openReferenceVideo(context, video.url) },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        VideoCover(video.coverUrl, Modifier.size(width = 60.dp, height = 48.dp).clip(RoundedCornerShape(10.dp)))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(video.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("观看原视频", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                        }
                        Icon(Icons.Default.PlayCircle, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun TagPill(text: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(9.dp)) {
        Text(text, Modifier.padding(horizontal = 9.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun DetailSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 18.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 7.dp))
        content()
    }
}

@Composable
private fun MutedEmpty(text: String) = Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeEditorScreen(
    vm: RecipeViewModel,
    discardRequested: Boolean,
    onDiscardRequestHandled: () -> Unit,
    onSaved: () -> Unit,
    onDeleted: () -> Unit,
    onDiscard: () -> Unit
) {
    val draft = vm.editorDraft ?: return
    var ingredientName by rememberSaveable(draft.id) { mutableStateOf("") }
    var ingredientAmount by rememberSaveable(draft.id) { mutableStateOf("") }
    var ingredientUnit by rememberSaveable(draft.id) { mutableStateOf("") }
    var customTagInput by rememberSaveable(draft.id) { mutableStateOf("") }
    var stepText by rememberSaveable(draft.id) { mutableStateOf("") }
    var editingStepIndex by rememberSaveable(draft.id) { mutableStateOf<Int?>(null) }
    var videoUrl by rememberSaveable(draft.id) { mutableStateOf(vm.consumeSharedVideoText()) }
    var photoTarget by remember { mutableStateOf(PhotoTarget.COVER) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var revealedDefaultTag by remember { mutableStateOf<String?>(null) }
    var deletingCustomTag by remember { mutableStateOf<String?>(null) }
    val selectedStep = editingStepIndex?.let(draft.steps::getOrNull)
    val hasPendingStepEdit = if (selectedStep == null) {
        stepText.isNotBlank() || vm.stepImageCandidate.isNotBlank()
    } else {
        stepText != selectedStep.text || vm.stepImageCandidate != selectedStep.imagePath
    }
    val hasLocalChanges = ingredientName.isNotBlank() || ingredientAmount.isNotBlank() ||
        ingredientUnit.isNotBlank() || customTagInput.isNotBlank() || hasPendingStepEdit ||
        videoUrl.isNotBlank()
    val hasPendingChanges = vm.hasUnsavedChanges || hasLocalChanges
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { vm.copyImage(it, photoTarget) }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        vm.finishCamera(success)
    }

    BackHandler {
        if (hasPendingChanges) confirmDiscard = true else onDiscard()
    }

    if (confirmDiscard || discardRequested) ConfirmDialog(
        title = "放弃未保存的修改？",
        text = "可以先保存本次编辑，也可以放弃修改后返回。",
        confirmLabel = "放弃",
        dismissLabel = "保存",
        onDismiss = { confirmDiscard = false; onDiscardRequestHandled() },
        onDismissButton = {
            if (vm.saveDraft() != null) {
                confirmDiscard = false
                onDiscardRequestHandled()
                onSaved()
            }
        },
        onConfirm = { confirmDiscard = false; onDiscardRequestHandled(); onDiscard() }
    )
    if (confirmDelete) ConfirmDialog(
        title = "删除这道菜？",
        text = "菜谱和未被其他菜谱使用的图片将被删除，此操作无法撤销。",
        confirmLabel = "删除",
        onDismiss = { confirmDelete = false },
        onConfirm = { confirmDelete = false; vm.delete(draft.id); onDeleted() }
    )
    deletingCustomTag?.let { tag ->
        ConfirmDialog(
            title = "删除自定义标签“$tag”？",
            text = "该标签会从所有菜谱中移除，预置标签不会受到影响。",
            confirmLabel = "删除",
            onDismiss = { deletingCustomTag = null },
            onConfirm = { vm.removeTag(tag); deletingCustomTag = null }
        )
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding()) {
        EditorTopBar(
            title = if (draft.name.isBlank()) "新增菜谱" else "编辑菜谱",
            onBack = { if (hasPendingChanges) confirmDiscard = true else onDiscard() },
            onDelete = if (vm.recipes.any { it.id == draft.id }) ({ confirmDelete = true }) else null
        )
        Column(Modifier.padding(horizontal = 18.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            RecipeImage(draft.coverPath, Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(20.dp)))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { photoTarget = PhotoTarget.COVER; gallery.launch("image/*") }) {
                    Icon(Icons.Default.Image, null); Text(" 相册封面")
                }
                OutlinedButton(onClick = {
                    photoTarget = PhotoTarget.COVER
                    camera.launch(vm.prepareCamera(PhotoTarget.COVER))
                }) { Icon(Icons.Default.CameraAlt, null); Text(" 拍摄封面") }
            }
            OutlinedTextField(
                draft.name,
                { value -> vm.updateDraft { it.copy(name = value) } },
                Modifier.fillMaxWidth(),
                label = { Text("菜名 *") },
                singleLine = true,
                shape = RoundedCornerShape(15.dp)
            )

            EditorSection("标签") {
                Text("点击标签即可添加或移除；长按预置标签可显示删除按钮。", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    (defaultCuisineTags + defaultFeatureTags)
                        .filter { it in vm.availableDefaultTags }
                        .forEach { tag ->
                        val selected = tag in draft.cuisineTags || tag in draft.featureTags
                        DeletablePresetTagChip(
                            tag = tag,
                            selected = selected,
                            deleteVisible = revealedDefaultTag == tag,
                            onToggle = {
                                if (tag in defaultCuisineTags) vm.updateDraft { it.copy(cuisineTags = it.cuisineTags.toggle(tag)) }
                                else vm.updateDraft { it.copy(featureTags = it.featureTags.toggle(tag)) }
                            },
                            onLongPress = { revealedDefaultTag = tag },
                            onDelete = {
                                vm.removeDefaultTag(tag)
                                revealedDefaultTag = null
                            }
                        )
                    }
                }
                if (vm.customTags.isNotEmpty()) {
                    Text("自定义标签", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        vm.customTags.sorted().forEach { tag ->
                            InputChip(
                                selected = tag in draft.featureTags,
                                onClick = { vm.updateDraft { it.copy(featureTags = it.featureTags.toggle(tag)) } },
                                label = { Text(tag) },
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.Close,
                                        "删除自定义标签",
                                        Modifier.size(16.dp).clickable { deletingCustomTag = tag }
                                    )
                                }
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        customTagInput,
                        { customTagInput = it },
                        Modifier.weight(1f),
                        label = { Text("新增自定义标签") },
                        placeholder = { Text("例如：孩子爱吃") },
                        singleLine = true
                    )
                    IconButton(onClick = {
                        val tag = customTagInput.trim()
                        if (tag.isNotBlank()) {
                            vm.addTag(tag)
                            vm.updateDraft { it.copy(featureTags = it.featureTags + tag) }
                            customTagInput = ""
                        }
                    }) { Icon(Icons.Default.Add, "添加标签") }
                }
            }

            EditorSection("食材") {
                draft.ingredients.forEachIndexed { index, ingredient ->
                    OutlinedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            OutlinedTextField(
                                ingredient.name,
                                { value -> vm.updateDraft { recipe -> recipe.copy(ingredients = recipe.ingredients.toMutableList().also { it[index] = ingredient.copy(name = value) }) } },
                                Modifier.fillMaxWidth(),
                                label = { Text("食材名称 *") },
                                singleLine = true
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    ingredient.amount,
                                    { value -> vm.updateDraft { recipe -> recipe.copy(ingredients = recipe.ingredients.toMutableList().also { it[index] = ingredient.copy(amount = value) }) } },
                                    Modifier.weight(1f),
                                    label = { Text("用量（可选）") },
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    ingredient.unit,
                                    { value -> vm.updateDraft { recipe -> recipe.copy(ingredients = recipe.ingredients.toMutableList().also { it[index] = ingredient.copy(unit = value) }) } },
                                    Modifier.weight(1f),
                                    label = { Text("单位（可选）") },
                                    singleLine = true
                                )
                                IconButton(onClick = { vm.updateDraft { recipe -> recipe.copy(ingredients = recipe.ingredients.toMutableList().also { it.removeAt(index) }) } }) {
                                    Icon(Icons.Default.Delete, "删除食材")
                                }
                            }
                        }
                    }
                }
                OutlinedTextField(ingredientName, { ingredientName = it }, Modifier.fillMaxWidth(), label = { Text("食材名称 *") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    OutlinedTextField(ingredientAmount, { ingredientAmount = it }, Modifier.weight(1f), label = { Text("用量（可选）") }, singleLine = true)
                    OutlinedTextField(ingredientUnit, { ingredientUnit = it }, Modifier.weight(1f), label = { Text("单位（可选）") }, singleLine = true)
                }
                OutlinedButton(onClick = {
                    if (ingredientName.isNotBlank()) {
                        vm.updateDraft { it.copy(ingredients = it.ingredients + Ingredient(ingredientName.trim(), ingredientAmount.trim(), ingredientUnit.trim())) }
                        ingredientName = ""; ingredientAmount = ""; ingredientUnit = ""
                    }
                }, Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Text("添加食材") }
            }

            EditorSection("制作步骤") {
                draft.steps.forEachIndexed { index, step ->
                    OutlinedCard(
                        onClick = {
                            editingStepIndex = index
                            stepText = step.text
                            vm.replaceStepImageCandidate(step.imagePath)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(7.dp)) {
                                Text("${index + 1}", Modifier.padding(horizontal = 7.dp, vertical = 4.dp), fontWeight = FontWeight.Bold)
                            }
                            Text(step.text, Modifier.weight(1f).padding(horizontal = 9.dp), maxLines = 3, overflow = TextOverflow.Ellipsis)
                            if (step.imagePath.isNotBlank()) RecipeImage(step.imagePath, Modifier.size(48.dp).clip(RoundedCornerShape(9.dp)))
                            IconButton(onClick = {
                                vm.updateDraft { recipe -> recipe.copy(steps = recipe.steps.toMutableList().also { it.removeAt(index) }) }
                                editingStepIndex = null
                                stepText = ""
                                vm.replaceStepImageCandidate("")
                            }) {
                                Icon(Icons.Default.Delete, "删除步骤")
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        stepText,
                        { stepText = it },
                        Modifier.weight(1f),
                        label = { Text(if (editingStepIndex == null) "写一条简洁步骤" else "修改第 ${editingStepIndex!! + 1} 步") },
                        minLines = 2
                    )
                    StepImageButton(
                        path = vm.stepImageCandidate,
                        onGallery = { photoTarget = PhotoTarget.STEP; gallery.launch("image/*") },
                        onCamera = { photoTarget = PhotoTarget.STEP; camera.launch(vm.prepareCamera(PhotoTarget.STEP)) },
                        onRemove = { vm.replaceStepImageCandidate("") }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                    if (stepText.isNotBlank()) {
                        val newStep = RecipeStep(stepText.trim(), vm.stepImageCandidate)
                        val index = editingStepIndex
                        vm.updateDraft { recipe ->
                            if (index != null && index in recipe.steps.indices) recipe.copy(steps = recipe.steps.toMutableList().also { it[index] = newStep })
                            else recipe.copy(steps = recipe.steps + newStep)
                        }
                        vm.consumeStepImageCandidate()
                        stepText = ""
                        editingStepIndex = null
                    }
                    }) { Icon(if (editingStepIndex == null) Icons.Default.Add else Icons.Default.Check, null); Text(if (editingStepIndex == null) "添加步骤" else "保存修改") }
                    if (editingStepIndex != null) OutlinedButton(onClick = {
                        editingStepIndex = null
                        stepText = ""
                        vm.replaceStepImageCandidate("")
                    }) { Text("取消修改") }
                }
            }

            OutlinedTextField(
                draft.notes,
                { value -> vm.updateDraft { it.copy(notes = value) } },
                Modifier.fillMaxWidth(),
                label = { Text("注意事项") },
                minLines = 3
            )

            EditorSection("参考视频") {
                draft.videos.forEach { video ->
                    OutlinedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            VideoCover(video.coverUrl, Modifier.size(width = 54.dp, height = 44.dp).clip(RoundedCornerShape(9.dp)))
                            OutlinedTextField(
                                value = video.title,
                                onValueChange = { title ->
                                    vm.updateDraft { recipe ->
                                        recipe.copy(videos = recipe.videos.map { existing ->
                                            if (existing.url == video.url) existing.copy(title = title) else existing
                                        })
                                    }
                                },
                                modifier = Modifier.weight(1f).padding(horizontal = 9.dp),
                                label = { Text("视频标题") },
                                singleLine = true
                            )
                            IconButton(onClick = { vm.updateDraft { it.copy(videos = it.videos - video) } }) {
                                Icon(Icons.Default.Close, "移除视频")
                            }
                        }
                    }
                }
                OutlinedTextField(
                    videoUrl,
                    { videoUrl = it },
                    Modifier.fillMaxWidth(),
                    label = { Text("粘贴视频分享文案或链接") },
                    supportingText = { Text("支持抖音及其他视频网站；会自动提取其中的 HTTPS 链接和公开标题。") }
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        if (videoUrl.isNotBlank()) vm.resolveVideo(videoUrl) { video ->
                            vm.updateDraft { it.copy(videos = it.videos.filterNot { old -> old.url == video.url } + video) }
                            videoUrl = ""
                        }
                    }) { Icon(Icons.Default.Link, null); Text(" 添加链接") }
                }
            }

            Button(
                onClick = { if (vm.saveDraft() != null) onSaved() },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(15.dp)
            ) { Icon(Icons.Default.Check, null); Text(" 保存菜谱") }
            Spacer(Modifier.height(22.dp))
        }
    }
}

@Composable
private fun StepImageButton(
    path: String,
    onGallery: () -> Unit,
    onCamera: () -> Unit,
    onRemove: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        if (path.isBlank()) {
            OutlinedIconButton(
                onClick = { expanded = true },
                modifier = Modifier.size(58.dp),
                shape = RoundedCornerShape(13.dp)
            ) { Icon(Icons.Default.AddAPhoto, "添加步骤图片") }
        } else {
            Surface(
                onClick = { expanded = true },
                modifier = Modifier.size(58.dp),
                shape = RoundedCornerShape(13.dp)
            ) { RecipeImage(path, Modifier.fillMaxSize()) }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("从相册选择") },
                leadingIcon = { Icon(Icons.Default.Image, null) },
                onClick = { expanded = false; onGallery() }
            )
            DropdownMenuItem(
                text = { Text("拍摄图片") },
                leadingIcon = { Icon(Icons.Default.CameraAlt, null) },
                onClick = { expanded = false; onCamera() }
            )
            if (path.isNotBlank()) DropdownMenuItem(
                text = { Text("移除图片") },
                leadingIcon = { Icon(Icons.Default.Delete, null) },
                onClick = { expanded = false; onRemove() }
            )
        }
    }
}

@Composable
private fun EditorTopBar(title: String, onBack: () -> Unit, onDelete: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        if (onDelete != null) IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "删除") }
        else Spacer(Modifier.size(48.dp))
    }
}

@Composable
private fun EditorSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

@Composable
fun SettingsScreen(vm: RecipeViewModel) {
    val backup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri -> uri?.let(vm::export) }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::import) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Text("设置", style = MaterialTheme.typography.headlineLarge) }
        item {
            SettingsCard("数据备份") {
                Text("备份包含菜谱和应用内图片。可在系统文件选择器中直接选择网盘位置。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { backup.launch("清欢小谱_${System.currentTimeMillis()}.zip") }) {
                        Icon(Icons.Default.UploadFile, null); Text(" 备份")
                    }
                    OutlinedButton(onClick = { restore.launch(arrayOf("application/zip", "application/octet-stream")) }) {
                        Icon(Icons.Default.Download, null); Text(" 恢复")
                    }
                }
            }
        }
        item { Spacer(Modifier.height(42.dp)) }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}

@Composable
fun RecipeImage(path: String, modifier: Modifier) {
    Box(
        modifier.background(
            Brush.linearGradient(listOf(Color(0xFFF7CA7A), Color(0xFFBF5425)))
        ),
        contentAlignment = Alignment.Center
    ) {
        if (path.isNotBlank() && File(path).isFile) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(File(path)).size(1024).crossfade(true).build(),
                contentDescription = "菜品图片",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(Icons.Default.Restaurant, "暂无图片", Modifier.size(48.dp), tint = Color.White.copy(alpha = .9f))
        }
    }
}

@Composable
private fun VideoCover(url: String, modifier: Modifier) {
    Box(
        modifier.background(Brush.linearGradient(listOf(Color(0xFF392920), Color(0xFFC85A25)))),
        contentAlignment = Alignment.Center
    ) {
        if (url.isNotBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(url).crossfade(true).build(),
                contentDescription = "视频封面",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else Icon(Icons.Default.PlayCircle, "视频封面", tint = Color.White)
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    dismissLabel: String = "取消",
    onDismissButton: () -> Unit = onDismiss
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismissButton) { Text(dismissLabel) } }
    )
}

@Composable
private fun DeletablePresetTagChip(
    tag: String,
    selected: Boolean,
    deleteVisible: Boolean,
    onToggle: () -> Unit,
    onLongPress: () -> Unit,
    onDelete: () -> Unit
) {
    Box {
        Surface(
            modifier = Modifier.combinedClickable(onClick = onToggle, onLongClick = onLongPress),
            shape = RoundedCornerShape(8.dp),
            color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
            contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            border = BorderStroke(
                1.dp,
                if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.outlineVariant
            )
        ) {
            Text(tag, modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp))
        }
        if (deleteVisible) {
            Surface(
                onClick = onDelete,
                modifier = Modifier.align(Alignment.TopEnd).size(20.dp),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            ) {
                Icon(Icons.Default.Close, "删除预置标签", Modifier.padding(4.dp))
            }
        }
    }
}

private fun Set<String>.toggle(value: String) = if (value in this) this - value else this + value

private fun openReferenceVideo(context: Context, input: String) {
    val url = extractSharedHttpsUrl(input) ?: return
    val uri = url.toUri()
    val douyinIntent = Intent(Intent.ACTION_VIEW, uri).setPackage("com.ss.android.ugc.aweme")
    val fallback = Intent(Intent.ACTION_VIEW, uri)
    runCatching { context.startActivity(douyinIntent) }
        .recoverCatching { context.startActivity(fallback) }
}
