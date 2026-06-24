package com.pockettavern.app.domain.model

import kotlinx.serialization.Serializable

/**
 * Native PocketTavern **Story** card — a self-contained ensemble (world + cast + user-character +
 * lore + openings) for human-directed multi-character RP. Distinct from ST single-character cards
 * and from [Group] (which round-robins separate ST cards).
 *
 * One Story = one file. Carried as JSON, embedded in a PNG `ptensemble` tEXt chunk (+ cover art),
 * or as raw `.json`. Authored EXTERNALLY — the app imports & runs, never creates/converts (V11).
 *
 * SPEC: ~/projects/pockettavern/ensemble-mode/SPEC.md
 */
@Serializable
data class Story(
    val schema: String = SCHEMA_VERSION,        // format version for forward-compat
    val name: String,
    val creator: String = "",
    val version: String = "1.0",
    val tags: List<String> = emptyList(),
    /** Cover art filename (the PNG itself when embedded), relative to the Story's asset dir. */
    val cover: String? = null,
    val world: StoryWorld,
    val cast: List<CastMember>,
    val userCharacter: StoryUserCharacter,
    /** Lore BAKED IN — no external lorebook file (V15). */
    val lore: List<StoryLore> = emptyList(),
    /** Flexible openers — NEVER a single locked first_mes (V5). >=1 required. */
    val openings: List<StoryOpening>,
    /** Card-authored mission scenarios the player can launch (optional). */
    val missions: List<StoryMission> = emptyList(),
    /**
     * Card-author DIRECTION — story-specific director steering (tone, pacing, how to run THIS story),
     * layered ON TOP of PocketTavern's core director contract. Cascades: core -> card.direction ->
     * active scene (opening/mission) direction, most specific last. Optional; empty = pure core. (V16)
     */
    val direction: String = ""
) {
    companion object {
        const val SCHEMA_VERSION = "pt-story-1"
    }
}

/**
 * A card-authored mission/scenario the player (director) can launch. [cast] = suggested/assigned
 * characters — the scene's LEADS (highlighted, not exclusive: others may still appear organically, V4).
 */
@Serializable
data class StoryMission(
    val name: String,
    val briefing: String,
    val location: String = "",
    /** Suggested lead characters (cast names). Empty = whole household available. */
    val cast: List<String> = emptyList(),
    /** Per-mission director steering — overrides card.direction for this scene (V16). Optional. */
    val direction: String = ""
)

/** Setting + factions + always-on context + mechanic/rule definitions. */
@Serializable
data class StoryWorld(
    val setting: String,
    val factions: List<String> = emptyList(),
    /** Always injected into the prompt — the constant world frame. */
    val alwaysOn: String = "",
    /** Rule/mechanic definitions (how powers/physics/etc. work in this world). */
    val mechanics: List<String> = emptyList()
)

/**
 * A first-class cast member. [privateSecret] is SOFT-hidden state (V3): lives in the model's
 * reasoning/system context, never narrated to the player unless the scene reveals it in-fiction.
 */
@Serializable
data class CastMember(
    val name: String,
    val appearance: String = "",
    /** How they talk — the distinct voice the model must keep (V7). */
    val voice: String,
    val publicRole: String = "",
    /** Soft-hidden agenda/secret (V3). */
    val privateSecret: String = "",
    /** When this character would react vs stay silent — feeds organic reaction (V4). */
    val reactionTendency: String = "",
    val ideologyTargeting: String = "",
    val relationships: String = ""
)

/**
 * The persona the human plays. [hiddenFacts] are things OTHER cast know that the user-character
 * does NOT — soft hidden state in the other direction (V8); not narrated to the player unless
 * discovered in-scene.
 */
@Serializable
data class StoryUserCharacter(
    val persona: String,
    val hiddenFacts: List<String> = emptyList()
)

/** Native lorebook-equivalent entry, baked into the card (V15). */
@Serializable
data class StoryLore(
    val keys: List<String> = emptyList(),
    val content: String,
    /** If true, injected every turn regardless of keyword match. */
    val alwaysOn: Boolean = false
) {
    /** Keyed entries (not always-on) match when any key appears in [text] (case-insensitive). */
    fun matches(text: String): Boolean {
        if (alwaysOn) return true
        val low = text.lowercase()
        return keys.any { it.isNotBlank() && low.contains(it.lowercase()) }
    }
}

/**
 * A flexible opening seed. Replaces ST's single locked first_mes (V5): a Story ships several, the
 * player picks (or the model expands one), and cast positions are NOT frozen by it.
 */
@Serializable
data class StoryOpening(
    val label: String = "",
    /** Seed text — a situation/prompt, not a fully-staged tableau. */
    val seed: String,
    /** Per-opening director steering — overrides card.direction for this branch (V16). Optional. */
    val direction: String = ""
)

// ---------------------------------------------------------------- validation

/** A schema problem found in a [Story]; [path] locates it for authoring tools. */
data class StoryValidationError(val path: String, val message: String)

object StoryValidator {
    /** Returns all problems; empty list = valid. Enforces §I schema + V5/V15-relevant rules. */
    fun validate(story: Story): List<StoryValidationError> {
        val errs = mutableListOf<StoryValidationError>()
        fun bad(path: String, msg: String) = errs.add(StoryValidationError(path, msg))

        if (story.name.isBlank()) bad("name", "Story name required")
        if (story.world.setting.isBlank()) bad("world.setting", "World setting required")

        // Cast: >=1, each needs name + voice (distinct-voice invariant V7 depends on a voice).
        if (story.cast.isEmpty()) bad("cast", "At least one cast member required")
        val seen = mutableSetOf<String>()
        story.cast.forEachIndexed { i, c ->
            if (c.name.isBlank()) bad("cast[$i].name", "Cast member name required")
            else if (!seen.add(c.name.lowercase())) bad("cast[$i].name", "Duplicate cast name '${c.name}'")
            if (c.voice.isBlank()) bad("cast[$i].voice", "Voice required (distinct-voice invariant)")
        }

        if (story.userCharacter.persona.isBlank())
            bad("userCharacter.persona", "User-character persona required")

        // V5: openings must be flexible seeds, >=1 — never a single locked opener.
        if (story.openings.isEmpty()) bad("openings", "At least one opening seed required (V5)")
        story.openings.forEachIndexed { i, o ->
            if (o.seed.isBlank()) bad("openings[$i].seed", "Opening seed text required")
        }

        // V15: lore is baked in (a list on the model) — entries must carry content.
        story.lore.forEachIndexed { i, l ->
            if (l.content.isBlank()) bad("lore[$i].content", "Lore entry content required")
            if (!l.alwaysOn && l.keys.none { it.isNotBlank() })
                bad("lore[$i].keys", "Keyed lore entry needs >=1 key (or set alwaysOn)")
        }

        // Card-authored missions: name + briefing required; cast names must reference real cast.
        val castNames = story.cast.map { it.name }.toSet()
        story.missions.forEachIndexed { i, m ->
            if (m.name.isBlank()) bad("missions[$i].name", "Mission name required")
            if (m.briefing.isBlank()) bad("missions[$i].briefing", "Mission briefing required")
            m.cast.forEach { c -> if (c !in castNames) bad("missions[$i].cast", "Unknown cast '$c'") }
        }

        return errs
    }

    fun isValid(story: Story): Boolean = validate(story).isEmpty()
}
