package com.jakatech.rebanhomais.kmp.domain.usecase

import com.jakatech.rebanhomais.kmp.data.repository.AuthRepository
import com.jakatech.rebanhomais.kmp.domain.entity.AuthResult

class LoginUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(email: String, password: String): AuthResult {
        if (email.isBlank() || password.isBlank()) {
            return AuthResult.Error(IllegalArgumentException("E-mail e senha não podem ser vazios"))
        }
        return repository.login(email, password)
    }
}
