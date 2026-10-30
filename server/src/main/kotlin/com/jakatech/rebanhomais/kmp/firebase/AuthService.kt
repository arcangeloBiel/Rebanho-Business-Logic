package com.jakatech.rebanhomais.kmp.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.UserRecord
import com.jakatech.rebanhomais.kmp.model.AuthRequestDto
import com.jakatech.rebanhomais.kmp.model.AuthResponseDto
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

object AuthService {
    private val logger = LoggerFactory.getLogger(AuthService::class.java)

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    private val firebaseApiKey: String
        get() = System.getenv("FIREBASE_WEB_API_KEY")
            ?: System.getenv("FIREBASE_API_KEY")
            ?: "AIzaSyBMkAErL6nt7SwEYKEyE0df-rxMq5CSOE4"

    @Serializable
    private data class FirebaseRestSignInRequest(
        val email: String,
        val password: String,
        val returnSecureToken: Boolean = true
    )

    @Serializable
    private data class FirebaseRestSignInResponse(
        val idToken: String? = null,
        val email: String? = null,
        val refreshToken: String? = null,
        val expiresIn: String? = null,
        val localId: String? = null,
        val displayName: String? = null,
        val registered: Boolean? = null,
        val error: FirebaseRestErrorContainer? = null
    )

    @Serializable
    private data class FirebaseRestErrorContainer(
        val code: Int? = null,
        val message: String? = null
    )

    fun register(request: AuthRequestDto): AuthResponseDto {
        val email = request.email.trim()
        val password = request.password

        if (email.isBlank() || !email.contains("@")) {
            throw IllegalArgumentException("E-mail inválido.")
        }
        if (password.length < 6) {
            throw IllegalArgumentException("A senha deve ter no mínimo 6 caracteres.")
        }

        try {
            val createRequest = UserRecord.CreateRequest()
                .setEmail(email)
                .setPassword(password)

            if (!request.nome.isNullOrBlank()) {
                createRequest.setDisplayName(request.nome.trim())
            }

            val userRecord = FirebaseAuth.getInstance().createUser(createRequest)
            logger.info("Usuário criado com sucesso no Firebase Auth. UID: ${userRecord.uid}")

            // Salvar informações adicionais no Firestore se disponível
            try {
                val userData = hashMapOf<String, Any?>(
                    "uid" to userRecord.uid,
                    "email" to email,
                    "nome" to request.nome?.trim(),
                    "criadoEm" to System.currentTimeMillis()
                )
                FirestoreService.getDb()
                    ?.collection("usuarios")
                    ?.document(userRecord.uid)
                    ?.set(userData)
            } catch (e: Exception) {
                logger.warn("Aviso ao salvar perfil no Firestore: ${e.message}")
            }

            // Realizar login automático para retornar o idToken
            return login(AuthRequestDto(email = email, password = password, nome = request.nome))
        } catch (e: FirebaseAuthException) {
            val msg = when (e.errorCode.name) {
                "EMAIL_ALREADY_EXISTS" -> "Este e-mail já está em uso por outra conta."
                "INVALID_PASSWORD" -> "A senha fornecida é inválida."
                else -> "Erro no Firebase: ${e.message}"
            }
            throw IllegalArgumentException(msg, e)
        }
    }

    fun login(request: AuthRequestDto): AuthResponseDto {
        val email = request.email.trim()
        val password = request.password

        if (email.isBlank() || password.isBlank()) {
            throw IllegalArgumentException("E-mail e senha são obrigatórios.")
        }

        val url = "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=$firebaseApiKey"
        val payload = json.encodeToString(
            FirebaseRestSignInRequest.serializer(),
            FirebaseRestSignInRequest(email = email, password = password)
        )

        val httpRequest = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build()

        val httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())
        val responseBody = httpResponse.body()

        if (httpResponse.statusCode() == 200) {
            val restResponse = json.decodeFromString(FirebaseRestSignInResponse.serializer(), responseBody)
            val uid = restResponse.localId ?: throw IllegalStateException("UID não retornado pelo Firebase.")

            val displayName = restResponse.displayName ?: request.nome ?: try {
                FirebaseAuth.getInstance().getUser(uid).displayName
            } catch (_: Exception) { null }

            return AuthResponseDto(
                uid = uid,
                email = restResponse.email ?: email,
                nome = displayName,
                token = restResponse.idToken,
                refreshToken = restResponse.refreshToken
            )
        } else {
            val errorMsg = try {
                val errorContainer = json.decodeFromString(FirebaseRestSignInResponse.serializer(), responseBody)
                val rawError = errorContainer.error?.message ?: "AUTH_ERROR"
                when {
                    rawError.startsWith("EMAIL_NOT_FOUND") -> "E-mail não cadastrado."
                    rawError.startsWith("INVALID_PASSWORD") -> "Senha incorreta."
                    rawError.startsWith("USER_DISABLED") -> "Esta conta de usuário foi desativada."
                    rawError.startsWith("TOO_MANY_ATTEMPTS") -> "Muitas tentativas incorretas. Tente novamente mais tarde."
                    else -> "Falha no login ($rawError)"
                }
            } catch (_: Exception) {
                "Credenciais inválidas ou erro no serviço de autenticação."
            }
            throw IllegalArgumentException(errorMsg)
        }
    }

    fun resetPassword(email: String): String {
        val cleanEmail = email.trim()
        if (cleanEmail.isBlank() || !cleanEmail.contains("@")) {
            throw IllegalArgumentException("E-mail inválido.")
        }

        try {
            val link = FirebaseAuth.getInstance().generatePasswordResetLink(cleanEmail)
            logger.info("Link de redefinição de senha gerado para $cleanEmail")
            return link
        } catch (e: FirebaseAuthException) {
            throw IllegalArgumentException("Erro ao solicitar redefinição: ${e.message}", e)
        }
    }

    fun verifyToken(idToken: String): AuthResponseDto {
        if (idToken.isBlank()) {
            throw IllegalArgumentException("Token não fornecido.")
        }
        try {
            val decodedToken = FirebaseAuth.getInstance().verifyIdToken(idToken)
            return AuthResponseDto(
                uid = decodedToken.uid,
                email = decodedToken.email ?: "",
                nome = decodedToken.name,
                isEmailVerified = decodedToken.isEmailVerified
            )
        } catch (e: Exception) {
            throw IllegalArgumentException("Token inválido ou expirado: ${e.message}", e)
        }
    }
}
