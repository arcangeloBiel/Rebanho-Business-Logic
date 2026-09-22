package com.jakatech.rebanhomais.kmp

import com.jakatech.rebanhomais.kmp.model.AnimalDto
import com.jakatech.rebanhomais.kmp.model.ApiResponse
import com.jakatech.rebanhomais.kmp.model.PesagemDto
import com.jakatech.rebanhomais.kmp.model.VacinaDto
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

// Armazenamento em memória para as rotas REST
private val rebanhoDb = ConcurrentHashMap<String, AnimalDto>()

fun Application.module() {
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }

    routing {
        get("/") {
            call.respondText(sayHello("Ktor"))
        }

        route("/animais") {
            // GET /animais -> Listar todo o rebanho
            get {
                call.respond(rebanhoDb.values.toList())
            }

            // POST /animais -> Cadastrar novo animal no rebanho
            post {
                try {
                    val animal = call.receive<AnimalDto>()
                    val animalId = animal.id?.ifBlank { null } ?: UUID.randomUUID().toString()
                    val novoAnimal = animal.copy(id = animalId)
                    
                    rebanhoDb[animalId] = novoAnimal
                    
                    call.respond(
                        HttpStatusCode.Created,
                        ApiResponse(
                            sucesso = true,
                            mensagem = "Animal cadastrado com sucesso no rebanho!",
                            dados = novoAnimal
                        )
                    )
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<AnimalDto>(
                            sucesso = false,
                            mensagem = "Erro ao cadastrar animal: ${e.localizedMessage}"
                        )
                    )
                }
            }

            // GET /animais/{animalId} -> Buscar animal por ID
            get("/{animalId}") {
                val animalId = call.parameters["animalId"]
                val animal = animalId?.let { rebanhoDb[it] }
                if (animal != null) {
                    call.respond(animal)
                } else {
                    call.respond(HttpStatusCode.NotFound, ApiResponse<AnimalDto>(sucesso = false, mensagem = "Animal não encontrado."))
                }
            }

            // DELETE /animais/{animalId} -> Remover animal
            delete("/{animalId}") {
                val animalId = call.parameters["animalId"]
                if (animalId != null && rebanhoDb.remove(animalId) != null) {
                    call.respond(ApiResponse<Unit>(sucesso = true, mensagem = "Animal removido com sucesso."))
                } else {
                    call.respond(HttpStatusCode.NotFound, ApiResponse<Unit>(sucesso = false, mensagem = "Animal não encontrado."))
                }
            }

            // POST /animais/{animalId}/pesagens -> Adicionar pesagem
            post("/{animalId}/pesagens") {
                val animalId = call.parameters["animalId"]
                val animal = animalId?.let { rebanhoDb[it] }
                if (animal == null) {
                    call.respond(HttpStatusCode.NotFound, ApiResponse<Unit>(sucesso = false, mensagem = "Animal não encontrado."))
                    return@post
                }

                val pesagem = call.receive<PesagemDto>()
                val pesagemId = pesagem.id?.ifBlank { null } ?: UUID.randomUUID().toString()
                val novaPesagem = pesagem.copy(id = pesagemId)

                val animalAtualizado = animal.copy(
                    pesoAtual = novaPesagem.pesoKg,
                    historicoPesagem = animal.historicoPesagem + novaPesagem
                )
                rebanhoDb[animal.id!!] = animalAtualizado

                call.respond(HttpStatusCode.Created, ApiResponse(sucesso = true, mensagem = "Pesagem registrada!", dados = novaPesagem))
            }

            // POST /animais/{animalId}/vacinas -> Adicionar vacina
            post("/{animalId}/vacinas") {
                val animalId = call.parameters["animalId"]
                val animal = animalId?.let { rebanhoDb[it] }
                if (animal == null) {
                    call.respond(HttpStatusCode.NotFound, ApiResponse<Unit>(sucesso = false, mensagem = "Animal não encontrado."))
                    return@post
                }

                val vacina = call.receive<VacinaDto>()
                val vacinaId = vacina.id?.ifBlank { null } ?: UUID.randomUUID().toString()
                val novaVacina = vacina.copy(id = vacinaId)

                val animalAtualizado = animal.copy(
                    vacinas = animal.vacinas + novaVacina
                )
                rebanhoDb[animal.id!!] = animalAtualizado

                call.respond(HttpStatusCode.Created, ApiResponse(sucesso = true, mensagem = "Vacina registrada!", dados = novaVacina))
            }
        }
    }
}
