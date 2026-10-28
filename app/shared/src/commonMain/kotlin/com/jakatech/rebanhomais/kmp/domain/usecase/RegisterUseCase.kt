package com.jakatech.rebanhomais.kmp.domain.usecase

import com.jakatech.rebanhomais.kmp.data.repository.AuthRepository
import com.jakatech.rebanhomais.kmp.domain.entity.AuthResult

class RegisterUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(email: String, password: String): AuthResult {
        if (email.isBlank() || password.length < 6) {
            return AuthResult.Error(IllegalArgumentException("E-mail inválido ou senha muito curta (mínimo de 6 caracteres)"))
        }
        return repository.register(email, password)
    }
}
