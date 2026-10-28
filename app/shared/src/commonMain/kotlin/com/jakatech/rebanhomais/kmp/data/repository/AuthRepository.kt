package com.jakatech.rebanhomais.kmp.data.repository

import com.jakatech.rebanhomais.kmp.domain.entity.AuthResult
import com.jakatech.rebanhomais.kmp.domain.entity.User

interface AuthRepository {
    suspend fun login(email: String, password: String): AuthResult
    suspend fun register(email: String, password: String): AuthResult
    suspend fun logout()
    fun getCurrentUser(): User?
}
