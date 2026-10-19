package com.lifetracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaceCategoriesTest {
    private fun place(id: String, kind: String) = PlaceEntity(id, id, kind, 0.0, 0.0, 50, false, 0L)

    @Test fun the_eight_built_in_ids_are_the_old_place_kinds() {
        assertEquals(PlaceKinds.list.map { it.id }, DefaultPlaceCategories.list.map { it.id })
        assertEquals(PlaceKinds.list.map { it.label }, DefaultPlaceCategories.list.map { it.name })
    }

    @Test fun a_place_takes_its_categorys_colour_and_icon() {
        val cats = DefaultPlaceCategories.list + PlaceCategoryEntity("pc1", "Friend's house", 0xFF123456L, "heart", 8, false)
        val looks = PlaceLooks.resolve(listOf(place("a", "home"), place("b", "pc1")), cats)
        assertEquals(0xFF8E94E8L, looks["a"]!!.color)
        assertEquals("Friend's house", looks["b"]!!.category)
        assertEquals("heart", looks["b"]!!.icon)
    }

    @Test fun a_hidden_category_still_colours_its_places_and_an_unknown_one_looks_like_other() {
        val cats = DefaultPlaceCategories.list.map { if (it.id == "gym") it.copy(archived = true, color = 0xFF111111L) else it }
        val looks = PlaceLooks.resolve(listOf(place("g", "gym"), place("x", "mystery")), cats)
        assertEquals(0xFF111111L, looks["g"]!!.color)
        assertEquals("Other", looks["x"]!!.category)
    }

    @Test fun new_ids_do_not_clash_with_built_in_ones() {
        assertEquals(false, DefaultPlaceCategories.list.any { it.id == PlaceLooks.newId(1L) })
    }
}
