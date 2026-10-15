package com.jakatech.rebanhomais.kmp.firebase

import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.SetOptions
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.cloud.FirestoreClient
import com.jakatech.rebanhomais.kmp.model.AnimalDto
import com.jakatech.rebanhomais.kmp.model.PesagemDto
import com.jakatech.rebanhomais.kmp.model.VacinaDto
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream
import java.util.UUID

object FirestoreService {
    private val logger = LoggerFactory.getLogger(FirestoreService::class.java)
    private var firestore: Firestore? = null

    fun initialize() {
        try {
            if (FirebaseApp.getApps().isEmpty()) {
                val envPath = System.getenv("FIREBASE_CREDENTIALS")
                val candidateFiles = listOfNotNull(
                    envPath?.let { File(it) },
                    File("serviceAccountKey.json"),
                    File("server/serviceAccountKey.json"),
                    File("../serviceAccountKey.json"),
                    File("/home/ec2-user/Rebanho-Business-Logic/serviceAccountKey.json"),
                    File(System.getProperty("user.dir"), "serviceAccountKey.json")
                )

                val serviceAccountFile = candidateFiles.firstOrNull { it.exists() && it.isFile }

                val options = if (serviceAccountFile != null) {
                    logger.info("Inicializando Firebase com arquivo de credenciais: ${serviceAccountFile.absolutePath}")
                    FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(FileInputStream(serviceAccountFile)))
                        .setProjectId("rebanhomais-c2d02")
                        .build()
                } else {
                    logger.warn("Arquivo serviceAccountKey.json não encontrado nas pastas: ${candidateFiles.map { it.absolutePath }}. Tentando Application Default...")
                    FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.getApplicationDefault())
                        .setProjectId("rebanhomais-c2d02")
                        .build()
                }

                FirebaseApp.initializeApp(options)
            }
            firestore = FirestoreClient.getFirestore()
            logger.info("Firestore inicializado com sucesso!")
        } catch (e: Exception) {
            logger.error("Falha ao inicializar Firebase Admin SDK: ${e.message}", e)
        }
    }

    private fun db(): Firestore = firestore ?: throw IllegalStateException("Firestore não inicializado. Configure a variável FIREBASE_CREDENTIALS ou coloque o serviceAccountKey.json na raiz.")

    // POST /animais
    fun salvarAnimal(animal: AnimalDto): AnimalDto {
        val animalId = animal.id?.ifBlank { null } ?: UUID.randomUUID().toString()
        val docRef = db().collection("animais").document(animalId)

        val animalData = hashMapOf<String, Any?>(
            "nome" to animal.nome,
            "tipo" to animal.tipo,
            "raca" to animal.raca,
            "dataNascimento" to animal.dataNascimento,
            "sexo" to animal.sexo,
            "pesoAtual" to animal.pesoAtual,
            "status" to animal.status,
            "lote" to animal.lote,
            "criadoEm" to com.google.cloud.Timestamp.now().toDate().toString()
        )

        val writeResult = docRef.set(animalData, SetOptions.merge()).get()
        logger.info("Animal $animalId gravado com sucesso no Firestore às: ${writeResult.updateTime}")

        animal.historicoPesagem.forEach { pesagem ->
            salvarPesagem(animalId, pesagem)
        }

        animal.vacinas.forEach { vacina ->
            salvarVacina(animalId, vacina)
        }

        return animal.copy(id = animalId)
    }

    // GET /animais
    fun listarAnimais(): List<AnimalDto> {
        val snapshot = db().collection("animais").get().get()
        return snapshot.documents.map { doc ->
            val animalId = doc.id
            AnimalDto(
                id = animalId,
                nome = doc.getString("nome") ?: "",
                tipo = doc.getString("tipo") ?: "BOI",
                raca = doc.getString("raca"),
                dataNascimento = doc.getString("dataNascimento"),
                sexo = doc.getString("sexo") ?: "MACHO",
                pesoAtual = doc.getDouble("pesoAtual"),
                status = doc.getString("status") ?: "ATIVO",
                lote = doc.getString("lote"),
                historicoPesagem = listarPesagens(animalId),
                vacinas = listarVacinas(animalId)
            )
        }
    }

    // GET /animais/{id}
    fun buscarAnimalPorId(animalId: String): AnimalDto? {
        val doc = db().collection("animais").document(animalId).get().get()
        if (!doc.exists()) return null

        return AnimalDto(
            id = doc.id,
            nome = doc.getString("nome") ?: "",
            tipo = doc.getString("tipo") ?: "BOI",
            raca = doc.getString("raca"),
            dataNascimento = doc.getString("dataNascimento"),
            sexo = doc.getString("sexo") ?: "MACHO",
            pesoAtual = doc.getDouble("pesoAtual"),
            status = doc.getString("status") ?: "ATIVO",
            lote = doc.getString("lote"),
            historicoPesagem = listarPesagens(animalId),
            vacinas = listarVacinas(animalId)
        )
    }

    // DELETE /animais/{id}
    fun deletarAnimal(animalId: String) {
        db().collection("animais").document(animalId).delete().get()
    }

    // Subcoleção: /animais/{animalId}/historicoPesagem
    fun salvarPesagem(animalId: String, pesagem: PesagemDto): PesagemDto {
        val pesagemId = pesagem.id?.ifBlank { null } ?: UUID.randomUUID().toString()
        val docRef = db().collection("animais").document(animalId)
            .collection("historicoPesagem").document(pesagemId)

        val data = hashMapOf<String, Any?>(
            "data" to pesagem.data,
            "peso" to pesagem.pesoKg,
            "observacao" to pesagem.observacao
        )

        docRef.set(data, SetOptions.merge()).get()
        return pesagem.copy(id = pesagemId)
    }

    fun listarPesagens(animalId: String): List<PesagemDto> {
        val snapshot = db().collection("animais").document(animalId)
            .collection("historicoPesagem").get().get()

        return snapshot.documents.map { doc ->
            PesagemDto(
                id = doc.id,
                data = doc.getString("data") ?: "",
                pesoKg = doc.getDouble("peso") ?: 0.0,
                observacao = doc.getString("observacao")
            )
        }
    }

    // Subcoleção: /animais/{animalId}/vacinas
    fun salvarVacina(animalId: String, vacina: VacinaDto): VacinaDto {
        val vacinaId = vacina.id?.ifBlank { null } ?: UUID.randomUUID().toString()
        val docRef = db().collection("animais").document(animalId)
            .collection("vacinas").document(vacinaId)

        val data = hashMapOf<String, Any?>(
            "nome" to vacina.nome,
            "dataAplicacao" to vacina.dataAplicacao,
            "proximaDose" to vacina.proximaDose,
            "observacao" to vacina.observacao
        )

        docRef.set(data, SetOptions.merge()).get()
        return vacina.copy(id = vacinaId)
    }

    fun listarVacinas(animalId: String): List<VacinaDto> {
        val snapshot = db().collection("animais").document(animalId)
            .collection("vacinas").get().get()

        return snapshot.documents.map { doc ->
            VacinaDto(
                id = doc.id,
                nome = doc.getString("nome") ?: "",
                dataAplicacao = doc.getString("dataAplicacao") ?: "",
                proximaDose = doc.getString("proximaDose"),
                observacao = doc.getString("observacao")
            )
        }
    }
}
