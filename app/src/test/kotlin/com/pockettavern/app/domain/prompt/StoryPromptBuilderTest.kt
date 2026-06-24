package com.pockettavern.app.domain.prompt

import com.pockettavern.app.domain.model.CastMember
import com.pockettavern.app.domain.model.ChatMessage
import com.pockettavern.app.domain.model.Story
import com.pockettavern.app.domain.model.StoryLore
import com.pockettavern.app.domain.model.StoryOpening
import com.pockettavern.app.domain.model.StoryUserCharacter
import com.pockettavern.app.domain.model.StoryWorld
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** T3 — Story ensemble prompt assembly. Asserts V1/V2/V3/V4/V5/V7/V8 land in the prompt. */
class StoryPromptBuilderTest {

    private val story = Story(
        name = "T",
        world = StoryWorld(setting = "A city that hides what it is.", alwaysOn = "The house is loud."),
        cast = listOf(
            CastMember(name = "Gulara", voice = "smug, warm", privateSecret = "forgets the window"),
            CastMember(name = "Mira", voice = "professional veneer", privateSecret = "crush on {{user}}")
        ),
        userCharacter = StoryUserCharacter(
            persona = "a tired detective",
            hiddenFacts = listOf("carries an ancient bloodline he doesn't know about")
        ),
        lore = listOf(StoryLore(keys = listOf("Hunter"), content = "the Hunter metabolism is distinct")),
        openings = listOf(StoryOpening(label = "open", seed = "you get home; something happened"))
    )

    private fun msg(content: String, isUser: Boolean, sender: String? = null) =
        ChatMessage(content = content, isUser = isUser, senderName = sender)

    @Test fun single_system_message_first_V1() {
        val out = StoryPromptBuilder(story, "Marcus").buildMessages(listOf(msg("hi", true)))
        assertEquals(1, out.count { it.role == "system" })
        assertEquals("system", out.first().role)
        assertTrue(out.all { it.role in setOf("system", "user", "assistant") }) // V2
    }

    @Test fun secrets_present_but_flagged_hidden_V3() {
        val sys = StoryPromptBuilder(story, "Marcus").buildMessages(listOf(msg("hi", true))).first().content
        assertTrue(sys.contains("forgets the window"))
        assertTrue(sys.contains("crush on Marcus"))          // {{user}} substituted
        assertTrue(sys.contains("hidden from other characters and the player until revealed in-fiction"))
    }

    @Test fun organic_reaction_directive_V4() {
        val sys = StoryPromptBuilder(story).buildMessages(listOf(msg("hi", true))).first().content
        assertTrue(sys.contains("Non-reaction is valid"))
        assertTrue(sys.contains("Do NOT round-robin"))
    }

    @Test fun distinct_voice_and_speaker_tags_V7() {
        val sys = StoryPromptBuilder(story).buildMessages(listOf(msg("hi", true))).first().content
        assertTrue(sys.contains("Voice: smug, warm"))
        assertTrue(sys.contains("Tag every speaker"))
    }

    @Test fun user_hidden_facts_V8() {
        val sys = StoryPromptBuilder(story, "Marcus").buildMessages(listOf(msg("hi", true))).first().content
        assertTrue(sys.contains("carries an ancient bloodline he doesn't know about"))
        assertTrue(sys.contains("never narrate these to Marcus unless discovered in-scene"))
    }

    @Test fun never_writes_for_player() {
        val sys = StoryPromptBuilder(story, "Marcus").buildMessages(listOf(msg("hi", true))).first().content
        assertTrue(sys.contains("you NEVER write Marcus's dialogue"))
        assertFalse(sys.contains("{{user}}"))
    }

    @Test fun opening_injected_on_fresh_scene_only_V5() {
        val pb = StoryPromptBuilder(story)
        val fresh = pb.buildMessages(listOf(msg("hi", true)), story.openings[0]).first().content
        assertTrue(fresh.contains("you get home; something happened"))
        // once an assistant turn exists, the scene has started — opening NOT re-injected
        val started = pb.buildMessages(listOf(msg("hi", true), msg("...", false, "Gulara")), story.openings[0]).first().content
        assertFalse(started.contains("# OPENING"))
    }

    @Test fun history_role_mapping_and_sender_prefix() {
        val out = StoryPromptBuilder(story).buildMessages(
            listOf(msg("What happened?", true), msg("studies the ceiling", false, "Gulara"))
        )
        assertEquals("user", out[1].role)
        assertEquals("assistant", out[2].role)
        assertTrue(out[2].content.startsWith("Gulara: "))
    }

    @Test fun keyed_lore_injected_on_match() {
        val pb = StoryPromptBuilder(story)
        val hit = pb.buildMessages(listOf(msg("tell me about the Hunter family", true))).first().content
        assertTrue(hit.contains("the Hunter metabolism is distinct"))
        val miss = pb.buildMessages(listOf(msg("nice weather", true))).first().content
        assertFalse(miss.contains("the Hunter metabolism is distinct"))
    }
}
