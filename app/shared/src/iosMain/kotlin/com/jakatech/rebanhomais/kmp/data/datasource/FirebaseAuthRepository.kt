package com.jakatech.rebanhomais.kmp.data.datasource

import com.jakatech.rebanhomais.kmp.data.repository.AuthRepository
import com.jakatech.rebanhomais.kmp.domain.entity.AuthResult
import com.jakatech.rebanhomais.kmp.domain.entity.User

actual class FirebaseAuthRepository actual constructor() : AuthRepository {
    
    actual override suspend fun login(email: String, password: String): AuthResult {
        TODO("Implementação iOS pendente: Você deve configurar o Firebase SDK para iOS ou usar o dev.gitlive:firebase-auth")
    }

    actual override suspend fun register(email: String, password: String): AuthResult {
        TODO("Implementação iOS pendente")
    }

    actual override suspend fun logout() {
        TODO("Implementação iOS pendente")
    }

    actual override fun getCurrentUser(): User? {
        TODO("Implementação iOS pendente")
    }
}
