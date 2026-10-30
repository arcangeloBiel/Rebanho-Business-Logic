package com.jakatech.rebanhomais.kmp.model

import kotlinx.serialization.Serializable

@Serializable
data class AuthRequestDto(
    val email: String,
    val password: String,
    val nome: String? = null
)

@Serializable
data class AuthResponseDto(
    val uid: String,
    val email: String,
    val nome: String? = null,
    val token: String? = null,
    val refreshToken: String? = null,
    val isEmailVerified: Boolean = false
)

@Serializable
data class ResetPasswordRequestDto(
    val email: String
)

@Serializable
data class VerifyTokenRequestDto(
    val token: String
)
