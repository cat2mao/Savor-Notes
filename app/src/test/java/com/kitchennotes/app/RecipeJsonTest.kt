package com.kitchennotes.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONArray
import org.json.JSONObject

class RecipeJsonTest {
    @Test fun recipe_round_trips_all_user_content() {
        val source = Recipe(
            id = "id-1", name = "青椒土豆丝", ingredients = listOf(
                Ingredient("土豆", "2", "个", IngredientCategory.MAIN),
                Ingredient("盐", "少许", "", IngredientCategory.SEASONING)
            ),
            cuisineTags = setOf("鲁菜"), featureTags = setOf("下饭"), steps = listOf(RecipeStep("切丝"), RecipeStep("快炒", "step.jpg")),
            notes = "大火", videos = listOf(ReferenceVideo("https://example.com/video", "参考")), cookCount = 4
        )
        val restored = Recipe.fromJson(source.toJson())
        assertEquals(source, restored)
    }

    @Test fun searchable_text_contains_name_ingredient_and_tags() {
        val recipe = Recipe(name = "凉拌黄瓜", ingredients = listOf(Ingredient("黄瓜")), cuisineTags = setOf("凉菜"), featureTags = setOf("低脂"))
        assertTrue(recipe.searchableText().contains("黄瓜"))
        assertTrue(recipe.searchableText().contains("低脂"))
    }

    @Test fun merge_keeps_newest_content_and_highest_cook_count() {
        val local = Recipe(id = "same", name = "旧名称", cookCount = 7, lastCookedAt = 100, updatedAt = 10, steps = listOf(RecipeStep("切丝", "a.jpg")))
        val backup = Recipe(id = "same", name = "新名称", cookCount = 3, lastCookedAt = 50, updatedAt = 20, steps = listOf(RecipeStep("切丝", "b.jpg")))
        val result = RecipeMerger.merge(listOf(local), listOf(backup)).single()
        assertEquals("新名称", result.name)
        assertEquals(7, result.cookCount)
        assertEquals(100, result.lastCookedAt)
        assertEquals("b.jpg", result.steps.single().imagePath)
    }

    @Test fun extracts_https_url_from_douyin_share_text() {
        val text = "复制打开抖音，看看这道菜 https://v.douyin.com/AbC123/ 更多内容"
        assertEquals("https://v.douyin.com/AbC123/", extractSharedHttpsUrl(text))
    }

    @Test fun extracts_author_and_title_from_douyin_share_text() {
        val text = "2.56 复制打开抖音，看看【村驴的作品】下饭菜的经典，回锅肉家庭版保姆级教程！ # 我的厨 https://v.douyin.com/AUjlOIEecoQ/"
        val hints = extractVideoShareHints(text)
        assertEquals("村驴", hints.author)
        assertEquals("下饭菜的经典，回锅肉家庭版保姆级教程", hints.title)
        assertEquals("村驴 · 下饭菜的经典，回锅肉家庭版保姆级教程", formatReferenceVideoTitle(hints.author, hints.title))
    }

    @Test fun rejects_non_https_and_oversized_share_text() {
        assertEquals(null, extractSharedHttpsUrl("打开 http://example.com/video"))
        assertEquals(null, extractSharedHttpsUrl("x".repeat(32_769) + " https://example.com"))
    }

    @Test fun legacy_steps_and_process_photos_migrate_without_data_loss() {
        val legacy = JSONObject()
            .put("id", "legacy")
            .put("name", "旧菜谱")
            .put("steps", JSONArray(listOf("切菜", "翻炒")))
            .put("processPhotos", JSONArray(listOf("one.jpg", "two.jpg")))
        val recipe = Recipe.fromJson(legacy)
        assertEquals(RecipeStep("切菜", "one.jpg"), recipe.steps[0])
        assertEquals(RecipeStep("翻炒", "two.jpg"), recipe.steps[1])
    }

    @Test fun parses_markdown_wrapped_ai_recipe_json() {
        val analysis = parseRecipeAnalysis("""
            ```json
            {"name":"番茄炒蛋","ingredients":[{"name":"番茄","amount":"2","unit":"个","category":"主料"}],"steps":["炒鸡蛋","加入番茄翻炒"],"notes":"少许糖","cuisineTags":["热菜"],"featureTags":["快手","无效标签"]}
            ```
        """.trimIndent())
        assertEquals("番茄炒蛋", analysis.name)
        assertEquals(Ingredient("番茄", "2", "个"), analysis.ingredients.single())
        assertEquals(listOf(RecipeStep("炒鸡蛋"), RecipeStep("加入番茄翻炒")), analysis.steps)
        assertEquals(setOf("热菜"), analysis.cuisineTags)
        assertEquals(setOf("快手"), analysis.featureTags)
    }

    @Test fun old_ingredients_default_to_main_and_ai_categories_are_kept() {
        assertEquals(IngredientCategory.MAIN, Ingredient.fromJson(JSONObject().put("name", "鸡蛋")).category)
        val analysis = parseRecipeAnalysis("""{"name":"炒菜","ingredients":[{"name":"盐","category":"佐料"}]}""")
        assertEquals(IngredientCategory.SEASONING, analysis.ingredients.single().category)
    }

    @Test fun random_picker_avoids_repeats_and_starts_a_new_cycle() {
        val first = nextRandomRecipeId(listOf("a", "b"), setOf("a"), "a") { 0 }
        assertEquals("b", first.first)
        assertEquals(setOf("a", "b"), first.second)

        val nextCycle = nextRandomRecipeId(listOf("a", "b"), first.second, "b") { 0 }
        assertEquals("a", nextCycle.first)
        assertEquals(setOf("a"), nextCycle.second)
    }
}
