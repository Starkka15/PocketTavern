package com.stark.sillytavern.domain.model

sealed class StreamEvent {
    data class Token(val token: String, val accumulated: String) : StreamEvent()
    data class Complete(val fullText: String) : StreamEvent()
    data class Error(val message: String) : StreamEvent()
}
