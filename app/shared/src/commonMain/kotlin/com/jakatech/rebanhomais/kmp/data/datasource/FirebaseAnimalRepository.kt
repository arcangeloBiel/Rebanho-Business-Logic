package com.jakatech.rebanhomais.kmp.data.datasource

import com.jakatech.rebanhomais.kmp.data.repository.AnimalRepository
import com.jakatech.rebanhomais.kmp.domain.entity.Animal
import com.jakatech.rebanhomais.kmp.domain.entity.Pesagem
import com.jakatech.rebanhomais.kmp.domain.entity.Vacina

/**
 * Implementação do AnimalRepository usando Firebase.
 * A implementação concreta é fornecida por cada plataforma via actual.
 */
expect class FirebaseAnimalRepository() : AnimalRepository {
    override suspend fun addAnimal(animal: Animal)
    override suspend fun getAnimals(): List<Animal>
    override suspend fun getAnimalById(animalId: String): Animal?
    override suspend fun deleteAnimal(animalId: String)
    override suspend fun addPesagem(animalId: String, pesagem: Pesagem)
    override suspend fun getHistoricoPesagem(animalId: String): List<Pesagem>
    override suspend fun addVacina(animalId: String, vacina: Vacina)
    override suspend fun getVacinas(animalId: String): List<Vacina>
}
