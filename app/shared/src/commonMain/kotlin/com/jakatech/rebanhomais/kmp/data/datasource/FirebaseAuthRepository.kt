package com.jakatech.rebanhomais.kmp.data.datasource

import com.jakatech.rebanhomais.kmp.data.repository.AuthRepository
import com.jakatech.rebanhomais.kmp.domain.entity.AuthResult
import com.jakatech.rebanhomais.kmp.domain.entity.User

/**
 * Implementação do AuthRepository usando Firebase Authentication.
 */
expect class FirebaseAuthRepository() : AuthRepository {
    override suspend fun login(email: String, password: String): AuthResult
    override suspend fun register(email: String, password: String): AuthResult
    override suspend fun logout()
    override fun getCurrentUser(): User?
}
