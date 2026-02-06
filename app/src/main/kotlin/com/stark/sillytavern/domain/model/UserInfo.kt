package com.stark.sillytavern.domain.model

data class UserInfo(
    val handle: String,
    val name: String,
    val avatar: String?,
    val isAdmin: Boolean,
    val hasPassword: Boolean,
    val created: Long?
)
