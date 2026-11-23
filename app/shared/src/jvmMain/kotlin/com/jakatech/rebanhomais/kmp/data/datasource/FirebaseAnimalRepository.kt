package com.jakatech.rebanhomais.kmp.data.datasource

import com.jakatech.rebanhomais.kmp.data.repository.AnimalRepository
import com.jakatech.rebanhomais.kmp.domain.entity.Animal
import com.jakatech.rebanhomais.kmp.domain.entity.Pesagem
import com.jakatech.rebanhomais.kmp.domain.entity.Vacina

actual class FirebaseAnimalRepository actual constructor() : AnimalRepository {

    actual override suspend fun addAnimal(animal: Animal) {
        TODO("Implementação JVM")
    }

    actual override suspend fun getAnimals(): List<Animal> {
        TODO("Implementação JVM")
    }

    actual override suspend fun getAnimalById(animalId: String): Animal? {
        TODO("Implementação JVM")
    }

    actual override suspend fun deleteAnimal(animalId: String) {
        TODO("Implementação JVM")
    }

    actual override suspend fun addPesagem(animalId: String, pesagem: Pesagem) {
        TODO("Implementação JVM")
    }

    actual override suspend fun getHistoricoPesagem(animalId: String): List<Pesagem> {
        TODO("Implementação JVM")
    }

    actual override suspend fun addVacina(animalId: String, vacina: Vacina) {
        TODO("Implementação JVM")
    }

    actual override suspend fun getVacinas(animalId: String): List<Vacina> {
        TODO("Implementação JVM")
    }
}
