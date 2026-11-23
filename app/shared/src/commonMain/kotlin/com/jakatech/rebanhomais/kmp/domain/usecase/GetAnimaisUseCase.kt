package com.jakatech.rebanhomais.kmp.domain.usecase

import com.jakatech.rebanhomais.kmp.data.repository.AnimalRepository
import com.jakatech.rebanhomais.kmp.domain.entity.Animal

class GetAnimaisUseCase(
    private val repository: AnimalRepository
) {
    suspend operator fun invoke(): List<Animal> = repository.getAnimals()
}
