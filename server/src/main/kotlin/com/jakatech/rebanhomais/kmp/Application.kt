package com.jakatech.rebanhomais.kmp

import com.jakatech.rebanhomais.kmp.firebase.FirestoreService
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

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port, host = "0.0.0.0") {
        FirestoreService.initialize()
        module()
    }.start(wait = true)
}

fun Application.module() {
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        })
    }

    routing {
        get("/") {
            call.respondText(sayHello("Ktor"))
        }

        route("/animais") {
            // GET /animais -> Listar todo o rebanho do Firestore
            get {
                try {
                    val lista = FirestoreService.listarAnimais()
                    call.respond(lista)
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiResponse<List<AnimalDto>>(
                            sucesso = false,
                            mensagem = "Erro ao buscar animais do Firestore: ${e.message}"
                        )
                    )
                }
            }

            // POST /animais -> Gravar diretamente no Firestore
            post {
                try {
                    val animal = call.receive<AnimalDto>()
                    val animalSalvo = FirestoreService.salvarAnimal(animal)
                    
                    call.respond(
                        HttpStatusCode.Created,
                        ApiResponse(
                            sucesso = true,
                            mensagem = "Animal cadastrado no Cloud Firestore com sucesso!",
                            dados = animalSalvo
                        )
                    )
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<AnimalDto>(
                            sucesso = false,
                            mensagem = "Erro ao salvar no Firestore: ${e.message}"
                        )
                    )
                }
            }

            // GET /animais/{animalId}
            get("/{animalId}") {
                val animalId = call.parameters["animalId"] ?: return@get call.respond(
                    HttpStatusCode.BadRequest, ApiResponse<Unit>(sucesso = false, mensagem = "ID inválido")
                )
                try {
                    val animal = FirestoreService.buscarAnimalPorId(animalId)
                    if (animal != null) {
                        call.respond(animal)
                    } else {
                        call.respond(
                            HttpStatusCode.NotFound,
                            ApiResponse<AnimalDto>(sucesso = false, mensagem = "Animal não encontrado no Firestore.")
                        )
                    }
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiResponse<Unit>(sucesso = false, mensagem = "Erro: ${e.message}")
                    )
                }
            }

            // DELETE /animais/{animalId}
            delete("/{animalId}") {
                val animalId = call.parameters["animalId"] ?: return@delete call.respond(
                    HttpStatusCode.BadRequest, ApiResponse<Unit>(sucesso = false, mensagem = "ID inválido")
                )
                try {
                    FirestoreService.deletarAnimal(animalId)
                    call.respond(ApiResponse<Unit>(sucesso = true, mensagem = "Animal removido do Firestore."))
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiResponse<Unit>(sucesso = false, mensagem = "Erro: ${e.message}")
                    )
                }
            }

            // POST /animais/{animalId}/pesagens
            post("/{animalId}/pesagens") {
                val animalId = call.parameters["animalId"] ?: return@post call.respond(
                    HttpStatusCode.BadRequest, ApiResponse<Unit>(sucesso = false, mensagem = "ID inválido")
                )
                try {
                    val pesagem = call.receive<PesagemDto>()
                    val pesagemSalva = FirestoreService.salvarPesagem(animalId, pesagem)
                    call.respond(HttpStatusCode.Created, ApiResponse(sucesso = true, mensagem = "Pesagem salva no Firestore!", dados = pesagemSalva))
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<Unit>(sucesso = false, mensagem = "Erro ao salvar pesagem: ${e.message}")
                    )
                }
            }

            // POST /animais/{animalId}/vacinas
            post("/{animalId}/vacinas") {
                val animalId = call.parameters["animalId"] ?: return@post call.respond(
                    HttpStatusCode.BadRequest, ApiResponse<Unit>(sucesso = false, mensagem = "ID inválido")
                )
                try {
                    val vacina = call.receive<VacinaDto>()
                    val vacinaSalva = FirestoreService.salvarVacina(animalId, vacina)
                    call.respond(HttpStatusCode.Created, ApiResponse(sucesso = true, mensagem = "Vacina salva no Firestore!", dados = vacinaSalva))
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<Unit>(sucesso = false, mensagem = "Erro ao salvar vacina: ${e.message}")
                    )
                }
            }
        }
    }
}
