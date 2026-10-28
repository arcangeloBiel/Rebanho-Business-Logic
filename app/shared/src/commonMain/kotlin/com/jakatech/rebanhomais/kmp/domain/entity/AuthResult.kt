package com.jakatech.rebanhomais.kmp.domain.entity

sealed class AuthResult {
    data class Success(val user: User) : AuthResult()
    data class Error(val exception: Exception) : AuthResult()
}
