package com.pockettavern.app.domain.model

enum class CardSource(
    val displayName: String,
    val requiresServerConfig: Boolean,
    val supportsLorebooks: Boolean
) {
    CHARAVAULT("CharaVault", requiresServerConfig = true, supportsLorebooks = true),
    CHUB("Chub.ai", requiresServerConfig = false, supportsLorebooks = false),
    CHARACTER_TAVERN("Character Tavern", requiresServerConfig = false, supportsLorebooks = false),
    WYVERN("Wyvern Chat", requiresServerConfig = false, supportsLorebooks = false),
    PYGMALION("Pygmalion", requiresServerConfig = false, supportsLorebooks = false),
    SPICYCHAT("SpicyChat", requiresServerConfig = false, supportsLorebooks = false),
    HARPY("Harpy", requiresServerConfig = false, supportsLorebooks = false),
    JANNYAI("JannyAI", requiresServerConfig = false, supportsLorebooks = false),
    SAKURA("Sakura.fm", requiresServerConfig = false, supportsLorebooks = false),
    RISUREALM("RisuRealm", requiresServerConfig = false, supportsLorebooks = false),
    SAUCEPAN("Saucepan", requiresServerConfig = false, supportsLorebooks = false),
}
