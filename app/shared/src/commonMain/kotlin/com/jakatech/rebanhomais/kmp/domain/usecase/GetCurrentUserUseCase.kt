package com.jakatech.rebanhomais.kmp.domain.usecase

import com.jakatech.rebanhomais.kmp.data.repository.AuthRepository
import com.jakatech.rebanhomais.kmp.domain.entity.User

class GetCurrentUserUseCase(private val repository: AuthRepository) {
    operator fun invoke(): User? = repository.getCurrentUser()
}
