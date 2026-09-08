package com.jakatech.rebanhomais.kmp.data.repository

import com.jakatech.rebanhomais.kmp.domain.entity.Animal
import com.jakatech.rebanhomais.kmp.domain.entity.Pesagem
import com.jakatech.rebanhomais.kmp.domain.entity.Vacina

interface AnimalRepository {
    suspend fun addAnimal(animal: Animal)
    suspend fun getAnimals(): List<Animal>
    suspend fun getAnimalById(animalId: String): Animal?
    suspend fun deleteAnimal(animalId: String)

    // Subcoleção /animais/{animalId}/historicoPesagem/{pesagemId}
    suspend fun addPesagem(animalId: String, pesagem: Pesagem)
    suspend fun getHistoricoPesagem(animalId: String): List<Pesagem>

    // Subcoleção /animais/{animalId}/vacinas/{vacinaId}
    suspend fun addVacina(animalId: String, vacina: Vacina)
    suspend fun getVacinas(animalId: String): List<Vacina>
}
