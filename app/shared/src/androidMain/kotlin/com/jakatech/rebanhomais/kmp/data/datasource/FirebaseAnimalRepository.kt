package com.jakatech.rebanhomais.kmp.data.datasource

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.jakatech.rebanhomais.kmp.data.repository.AnimalRepository
import com.jakatech.rebanhomais.kmp.domain.entity.Animal
import com.jakatech.rebanhomais.kmp.domain.entity.Pesagem
import com.jakatech.rebanhomais.kmp.domain.entity.Sexo
import com.jakatech.rebanhomais.kmp.domain.entity.StatusAnimal
import com.jakatech.rebanhomais.kmp.domain.entity.TipoAnimal
import com.jakatech.rebanhomais.kmp.domain.entity.Vacina
import com.jakatech.rebanhomais.kmp.domain.entity.parseLocalDate
import kotlinx.coroutines.tasks.await

actual class FirebaseAnimalRepository actual constructor() : AnimalRepository {

    private val firestore = FirebaseFirestore.getInstance()
    private val animaisCollection = firestore.collection("animais")

    actual override suspend fun addAnimal(animal: Animal) {
        val docRef = if (animal.id.isNotBlank()) {
            animaisCollection.document(animal.id)
        } else {
            animaisCollection.document()
        }

        val animalData = hashMapOf<String, Any?>(
            "nome" to animal.nome,
            "tipo" to animal.tipo.name,
            "raca" to animal.raca,
            "dataNascimento" to animal.dataNascimento?.toString(),
            "sexo" to animal.sexo.name,
            "pesoAtual" to animal.pesoAtual,
            "status" to animal.status.name,
            "lote" to animal.lote
        )

        docRef.set(animalData, SetOptions.merge()).await()

        // Salvar subcoleção de pesagens se existirem
        animal.historicoPesagem.forEach { pesagem ->
            addPesagem(docRef.id, pesagem)
        }

        // Salvar subcoleção de vacinas se existirem
        animal.vacinas.forEach { vacina ->
            addVacina(docRef.id, vacina)
        }
    }

    actual override suspend fun getAnimals(): List<Animal> {
        val snapshot = animaisCollection.get().await()
        val animais = mutableListOf<Animal>()

        for (doc in snapshot.documents) {
            val animalId = doc.id
            val nome = doc.getString("nome") ?: ""
            val tipo = doc.getString("tipo")?.let { runCatching { TipoAnimal.valueOf(it) }.getOrNull() } ?: TipoAnimal.BOI
            val raca = doc.getString("raca")
            val dataNascimento = parseLocalDate(doc.getString("dataNascimento"))
            val sexo = doc.getString("sexo")?.let { runCatching { Sexo.valueOf(it) }.getOrNull() } ?: Sexo.MACHO
            val pesoAtual = doc.getDouble("pesoAtual")
            val status = doc.getString("status")?.let { runCatching { StatusAnimal.valueOf(it) }.getOrNull() } ?: StatusAnimal.ATIVO
            val lote = doc.getString("lote")

            val pesagens = getHistoricoPesagem(animalId)
            val vacinas = getVacinas(animalId)

            animais.add(
                Animal(
                    id = animalId,
                    nome = nome,
                    tipo = tipo,
                    raca = raca,
                    dataNascimento = dataNascimento,
                    sexo = sexo,
                    pesoAtual = pesoAtual,
                    status = status,
                    vacinas = vacinas,
                    historicoPesagem = pesagens,
                    lote = lote
                )
            )
        }

        return animais
    }

    actual override suspend fun getAnimalById(animalId: String): Animal? {
        val doc = animaisCollection.document(animalId).get().await()
        if (!doc.exists()) return null

        val nome = doc.getString("nome") ?: ""
        val tipo = doc.getString("tipo")?.let { runCatching { TipoAnimal.valueOf(it) }.getOrNull() } ?: TipoAnimal.BOI
        val raca = doc.getString("raca")
        val dataNascimento = parseLocalDate(doc.getString("dataNascimento"))
        val sexo = doc.getString("sexo")?.let { runCatching { Sexo.valueOf(it) }.getOrNull() } ?: Sexo.MACHO
        val pesoAtual = doc.getDouble("pesoAtual")
        val status = doc.getString("status")?.let { runCatching { StatusAnimal.valueOf(it) }.getOrNull() } ?: StatusAnimal.ATIVO
        val lote = doc.getString("lote")

        val pesagens = getHistoricoPesagem(animalId)
        val vacinas = getVacinas(animalId)

        return Animal(
            id = animalId,
            nome = nome,
            tipo = tipo,
            raca = raca,
            dataNascimento = dataNascimento,
            sexo = sexo,
            pesoAtual = pesoAtual,
            status = status,
            vacinas = vacinas,
            historicoPesagem = pesagens,
            lote = lote
        )
    }

    actual override suspend fun deleteAnimal(animalId: String) {
        animaisCollection.document(animalId).delete().await()
    }

    // --- Subcoleção: /animais/{animalId}/historicoPesagem/{pesagemId} ---

    actual override suspend fun addPesagem(animalId: String, pesagem: Pesagem) {
        val pesagemCol = animaisCollection.document(animalId).collection("historicoPesagem")
        val docRef = if (pesagem.id.isNotBlank()) pesagemCol.document(pesagem.id) else pesagemCol.document()

        val pesagemData = hashMapOf<String, Any?>(
            "data" to pesagem.data.toString(),
            "peso" to pesagem.pesoKg,
            "observacao" to pesagem.observacao
        )

        docRef.set(pesagemData, SetOptions.merge()).await()
    }

    actual override suspend fun getHistoricoPesagem(animalId: String): List<Pesagem> {
        val snapshot = animaisCollection.document(animalId)
            .collection("historicoPesagem")
            .get()
            .await()

        return snapshot.documents.mapNotNull { doc ->
            val data = parseLocalDate(doc.getString("data")) ?: return@mapNotNull null
            val peso = doc.getDouble("peso") ?: doc.getDouble("pesoKg") ?: 0.0
            val observacao = doc.getString("observacao")

            Pesagem(
                id = doc.id,
                data = data,
                pesoKg = peso,
                observacao = observacao
            )
        }
    }

    // --- Subcoleção: /animais/{animalId}/vacinas/{vacinaId} ---

    actual override suspend fun addVacina(animalId: String, vacina: Vacina) {
        val vacinasCol = animaisCollection.document(animalId).collection("vacinas")
        val docRef = if (vacina.id.isNotBlank()) vacinasCol.document(vacina.id) else vacinasCol.document()

        val vacinaData = hashMapOf<String, Any?>(
            "nome" to vacina.nome,
            "dataAplicacao" to vacina.dataAplicacao.toString(),
            "proximaDose" to vacina.proximaAplicacao?.toString(),
            "observacao" to vacina.observacao
        )

        docRef.set(vacinaData, SetOptions.merge()).await()
    }

    actual override suspend fun getVacinas(animalId: String): List<Vacina> {
        val snapshot = animaisCollection.document(animalId)
            .collection("vacinas")
            .get()
            .await()

        return snapshot.documents.mapNotNull { doc ->
            val nome = doc.getString("nome") ?: return@mapNotNull null
            val dataAplicacao = parseLocalDate(doc.getString("dataAplicacao")) ?: return@mapNotNull null
            val proximaDose = parseLocalDate(doc.getString("proximaDose") ?: doc.getString("proximaAplicacao"))
            val observacao = doc.getString("observacao")

            Vacina(
                id = doc.id,
                nome = nome,
                dataAplicacao = dataAplicacao,
                proximaAplicacao = proximaDose,
                observacao = observacao
            )
        }
    }
}

