package com.jakatech.rebanhomais.kmp.domain.usecase

import com.jakatech.rebanhomais.kmp.data.repository.AnimalRepository
import com.jakatech.rebanhomais.kmp.domain.entity.Animal

class AddAnimalUseCase(
    private val repository: AnimalRepository
) {
    suspend operator fun invoke(animal: Animal) = repository.addAnimal(animal)
}
