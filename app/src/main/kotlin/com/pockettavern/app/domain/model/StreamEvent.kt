package com.pockettavern.app.domain.model

sealed class StreamEvent {
    data class Token(val token: String, val accumulated: String) : StreamEvent()
    data class ThinkingToken(val token: String, val accumulatedThinking: String) : StreamEvent()
    data class Complete(val fullText: String, val thinkingText: String = "") : StreamEvent()
    data class Error(val message: String) : StreamEvent()
}

sealed class GroupStreamEvent {
    data class CharacterStarted(val characterName: String, val characterAvatar: String) : GroupStreamEvent()
    data class Token(val token: String, val accumulated: String) : GroupStreamEvent()
    data class CharacterComplete(val characterName: String, val characterAvatar: String, val fullText: String) : GroupStreamEvent()
    data object AllComplete : GroupStreamEvent()
    data class Error(val message: String) : GroupStreamEvent()
}
