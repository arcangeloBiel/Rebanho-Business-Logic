package com.jakatech.rebanhomais.kmp.firebase

import com.jakatech.rebanhomais.kmp.data.repository.AnimalRepository
import com.jakatech.rebanhomais.kmp.domain.entity.Animal

expect class FirebaseAnimalRepository : AnimalRepository {
    override suspend fun addAnimal(animal: Animal)
    override suspend fun getAnimals(): List<Animal>
}