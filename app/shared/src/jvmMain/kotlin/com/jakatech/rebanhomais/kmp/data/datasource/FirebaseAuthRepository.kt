package com.jakatech.rebanhomais.kmp.data.datasource

import com.jakatech.rebanhomais.kmp.data.repository.AuthRepository
import com.jakatech.rebanhomais.kmp.domain.entity.AuthResult
import com.jakatech.rebanhomais.kmp.domain.entity.User

actual class FirebaseAuthRepository actual constructor() : AuthRepository {
    
    actual override suspend fun login(email: String, password: String): AuthResult {
        TODO("Implementação JVM pendente (Não suportado nativamente pelo Firebase Client, considere integração REST ou Ktor)")
    }

    actual override suspend fun register(email: String, password: String): AuthResult {
        TODO("Implementação JVM pendente")
    }

    actual override suspend fun logout() {
        TODO("Implementação JVM pendente")
    }

    actual override fun getCurrentUser(): User? {
        TODO("Implementação JVM pendente")
    }
}
