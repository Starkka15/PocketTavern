package com.pockettavern.app.domain.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T2 / Story card schema + validator. Pure-domain unit test (no Android deps).
 * Run: ./gradlew :app:testDebugUnitTest  (needs the JVM test harness wired in build.gradle.kts).
 */
class StoryValidatorTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun validStory() = Story(
        name = "Sample",
        world = StoryWorld(setting = "A city that hides what it is."),
        cast = listOf(
            CastMember(name = "A", voice = "dry, clipped"),
            CastMember(name = "B", voice = "warm, rambling")
        ),
        userCharacter = StoryUserCharacter(persona = "a tired detective"),
        lore = listOf(StoryLore(keys = listOf("SOB"), content = "the oversight board")),
        openings = listOf(
            StoryOpening(label = "after shift", seed = "you get home; something happened"),
            StoryOpening(label = "open", seed = "pick up wherever the house is")
        )
    )

    @Test fun valid_story_has_no_errors() {
        assertTrue(StoryValidator.validate(validStory()).isEmpty())
    }

    @Test fun empty_openings_violates_V5() {
        val s = validStory().copy(openings = emptyList())
        assertTrue(StoryValidator.validate(s).any { it.path == "openings" })
    }

    @Test fun keyed_lore_without_keys_violates_V15() {
        val s = validStory().copy(lore = listOf(StoryLore(keys = emptyList(), content = "x", alwaysOn = false)))
        assertTrue(StoryValidator.validate(s).any { it.path == "lore[0].keys" })
    }

    @Test fun alwaysOn_lore_needs_no_keys() {
        val s = validStory().copy(lore = listOf(StoryLore(keys = emptyList(), content = "x", alwaysOn = true)))
        assertTrue(StoryValidator.validate(s).none { it.path.startsWith("lore") })
    }

    @Test fun duplicate_cast_name_rejected() {
        val s = validStory().copy(cast = listOf(
            CastMember(name = "A", voice = "v1"), CastMember(name = "a", voice = "v2")
        ))
        assertTrue(StoryValidator.validate(s).any { it.path == "cast[1].name" })
    }

    @Test fun cast_without_voice_violates_V7() {
        val s = validStory().copy(cast = listOf(CastMember(name = "A", voice = "")))
        assertTrue(StoryValidator.validate(s).any { it.path == "cast[0].voice" })
    }

    @Test fun empty_cast_rejected() {
        assertTrue(StoryValidator.validate(validStory().copy(cast = emptyList())).any { it.path == "cast" })
    }

    @Test fun json_round_trips() {
        val s = validStory()
        val back = json.decodeFromString(Story.serializer(), json.encodeToString(Story.serializer(), s))
        assertEquals(s, back)
        assertEquals(Story.SCHEMA_VERSION, back.schema)
    }

    @Test fun keyed_lore_matches_on_keyword() {
        val l = StoryLore(keys = listOf("Hunter"), content = "x")
        assertTrue(l.matches("the hunter family"))
        assertTrue(!l.matches("nothing relevant"))
    }
}
