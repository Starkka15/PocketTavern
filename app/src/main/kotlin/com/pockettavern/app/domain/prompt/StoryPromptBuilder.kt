package com.pockettavern.app.domain.prompt

import com.pockettavern.app.domain.model.CastMember
import com.pockettavern.app.domain.model.ChatMessage
import com.pockettavern.app.domain.model.PromptMessage
import com.pockettavern.app.domain.model.Story
import com.pockettavern.app.domain.model.StoryOpening

/**
 * T3 — assembles the SINGLE chat-completion prompt for one Story (ensemble) turn.
 *
 * Output = `List<PromptMessage>`: one `system` message (world + full cast incl. private secrets +
 * player persona + hidden facts + keyed lore + director directives) followed by the chat history
 * as user/assistant turns. Fed to `LlmRepository.generate(messages = ...)` → ONE call (V1, V2).
 *
 * The "director" (who reacts / who stays silent / who's hiding what) is NOT code — it's the model's
 * private reasoning. With DeepSeek R1 that lands in the `<think>` block PT already buckets (V6).
 * Secrets are SOFT-hidden: present in the system context, withheld from the scene by directive (V3, V8).
 *
 * Clean stack (plan B): does NOT touch `PromptBuilder` (the ST single-character path).
 * SPEC: ~/projects/pockettavern/ensemble-mode/SPEC.md
 */
class StoryPromptBuilder(
    private val story: Story,
    /** The player's display name; substituted for `{{user}}`. Pass "{{user}}" to defer to PT macros. */
    private val userName: String = "{{user}}"
) {

    /** Build the messages array for this turn. [opening] is used only on a fresh scene. */
    fun buildMessages(
        history: List<ChatMessage>,
        opening: StoryOpening? = null,
        mission: com.pockettavern.app.domain.model.StoryMission? = null,
        roster: Set<String> = emptySet()
    ): List<PromptMessage> {
        val msgs = ArrayList<PromptMessage>(history.size + 1)
        msgs += PromptMessage("system", buildSystem(history, opening, mission, roster))
        for (m in history) {
            if (m.isNarrator) continue                       // narrator notes: not sent to AI
            val role = if (m.isUser) "user" else "assistant"
            // A character-tagged assistant message keeps its tag so the model sees who said what.
            val content = if (!m.isUser && m.senderName != null) "${m.senderName}: ${m.content}" else m.content
            msgs += PromptMessage(role, sub(content))
        }
        return msgs
    }

    private fun buildSystem(
        history: List<ChatMessage>,
        opening: StoryOpening?,
        mission: com.pockettavern.app.domain.model.StoryMission? = null,
        roster: Set<String> = emptySet()
    ): String = buildString {
        val w = story.world
        appendLine("# WORLD")
        appendLine(w.setting)
        if (w.factions.isNotEmpty()) appendLine("Factions: ${w.factions.joinToString(", ")}")
        if (w.alwaysOn.isNotBlank()) appendLine(w.alwaysOn)
        if (w.mechanics.isNotEmpty()) {
            appendLine("Rules of this world:")
            w.mechanics.forEach { appendLine("- $it") }
        }
        appendLine()

        appendLine("# CAST — you write ALL of them, never the player")
        story.cast.forEach { c -> appendCast(c) }

        appendLine("# THE PLAYER ($userName)")
        appendLine(story.userCharacter.persona)
        if (story.userCharacter.hiddenFacts.isNotEmpty()) {
            appendLine("Things the cast may know that $userName does NOT — never narrate these to $userName unless discovered in-scene:")
            story.userCharacter.hiddenFacts.forEach { appendLine("- $it") }
        }
        appendLine()

        // Keyed lore: always-on + entries whose keys appear in recent turns / the opening seed (V14, no trim).
        val scan = buildString {
            history.takeLast(LORE_SCAN_TURNS).forEach { append(it.content).append(' ') }
            opening?.let { append(it.seed) }
        }
        val lore = story.lore.filter { it.matches(scan) }
        if (lore.isNotEmpty()) {
            appendLine("# LORE")
            lore.forEach { appendLine("- ${it.content}") }
            appendLine()
        }

        // Opening seed only on a fresh scene (no assistant turn yet) — a seed, not a frozen tableau (V5).
        if (opening != null && history.none { !it.isUser && !it.isNarrator }) {
            appendLine("# OPENING")
            appendLine("Open from this seed; do not freeze the cast into a fixed tableau, let it breathe: ${opening.seed}")
            appendLine()
        }

        // Active mission framing (card-authored) — current scene's objective.
        mission?.let { m ->
            appendLine("# CURRENT MISSION")
            appendLine("${m.name}: ${m.briefing}")
            if (m.location.isNotBlank()) appendLine("Location: ${m.location}")
            appendLine()
        }

        // Highlighted roster (V4: leads, NOT exclusive — others may still appear organically).
        if (roster.isNotEmpty()) {
            appendLine("# LEAD CHARACTERS this scene: ${roster.joinToString(", ")}")
            appendLine("Focus on these characters — they are assigned/present. The rest of the household may still appear organically if the scene genuinely calls for it (a phone call, someone wandering in), but keep the spotlight on the leads.")
            appendLine()
        }

        append(DIRECTOR_DIRECTIVES)

        // DIRECTION cascade (V16): core directives above, then the card author's steering, then the
        // active scene's (opening/mission) — most specific last, so it wins on conflict.
        val direction = buildList {
            if (story.direction.isNotBlank()) add(story.direction)
            if (opening != null && opening.direction.isNotBlank()) add("For this opening/branch: ${opening.direction}")
            if (mission != null && mission.direction.isNotBlank()) add("For the current mission: ${mission.direction}")
        }
        if (direction.isNotEmpty()) {
            appendLine(); appendLine()
            appendLine("# STORY DIRECTION (the card author's steering — takes precedence over the defaults above where they conflict)")
            direction.forEach { appendLine(it) }
        }
    }.let(::sub)

    private fun StringBuilder.appendCast(c: CastMember) {
        appendLine("## ${c.name}")
        if (c.appearance.isNotBlank()) appendLine("Appearance: ${c.appearance}")
        appendLine("Voice: ${c.voice}")
        if (c.publicRole.isNotBlank()) appendLine("Public role: ${c.publicRole}")
        if (c.privateSecret.isNotBlank())
            appendLine("PRIVATE — hidden from other characters and the player until revealed in-fiction: ${c.privateSecret}")
        if (c.reactionTendency.isNotBlank()) appendLine("Reacts when: ${c.reactionTendency}")
        if (c.ideologyTargeting.isNotBlank()) appendLine("Ideology / targets: ${c.ideologyTargeting}")
        if (c.relationships.isNotBlank()) appendLine("Relationships: ${c.relationships}")
        appendLine()
    }

    private fun sub(s: String) = s.replace("{{user}}", userName)

    companion object {
        const val LORE_SCAN_TURNS = 6

        /** The make-or-break block. Tunable at playtest (T13). Encodes V1/V3/V4/V6/V7/V8. */
        val DIRECTOR_DIRECTIVES = """
# HOW TO WRITE THIS SCENE
You control the entire cast. {{user}} is the human player — you NEVER write {{user}}'s dialogue, actions, or thoughts. Only the player decides those.

Continue the scene naturally in response to what {{user}} just did:
- Have whichever cast members WOULD react, react — each in their own distinct voice. Tag every speaker, e.g. "Name: ...".
- Whoever WOULDN'T react stays silent. Not everyone speaks every turn. Non-reaction is valid and often correct. Do NOT round-robin the cast.
- Respect PRESENCE: only characters actually in the current scene/location may speak or act. Someone not established as present does NOT suddenly appear unless the story deliberately brings them in (they arrive, {{user}} goes to them, a phone call). Do NOT assemble the whole cast in one place by default — most are elsewhere in the world.
- When a scene opens small, keep it small; introduce new characters GRADUALLY across the story, not all at once.
- Let characters react to EACH OTHER, not only to {{user}} — they can talk among themselves when it fits.
- Keep every voice distinct; a reader should know who is speaking even without the tag.
- Honor who-knows-what. A character acts only on what THEY would know. Keep each character's PRIVATE facts hidden until the scene reveals them in-fiction — never have one character expose another's secret they could not know.
- Reason first, privately, about who would react, who stays quiet, and who is concealing what — then write only the scene itself.
- Advance the story, then stop and leave room for {{user}} to act next.
""".trim()
    }
}
