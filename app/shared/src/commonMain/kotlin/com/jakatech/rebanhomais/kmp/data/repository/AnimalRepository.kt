package com.jakatech.rebanhomais.kmp.data.repository

import com.jakatech.rebanhomais.kmp.domain.entity.Animal

interface AnimalRepository {
    suspend fun addAnimal(animal: Animal)
    suspend fun getAnimals(): List<Animal>
}