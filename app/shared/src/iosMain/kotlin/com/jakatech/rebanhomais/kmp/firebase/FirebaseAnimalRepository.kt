package com.jakatech.rebanhomais.kmp.firebase

import com.jakatech.rebanhomais.kmp.data.repository.AnimalRepository
import com.jakatech.rebanhomais.kmp.domain.entity.Animal

actual class FirebaseAnimalRepository : AnimalRepository {
    actual override suspend fun addAnimal(animal: Animal) {
        // Implementação usando Firebase SDK iOS
    }

    actual override suspend fun getAnimals(): List<Animal> {
        TODO("Not yet implemented")
    }
}
