package com.jakatech.rebanhomais.kmp.data.datasource

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.jakatech.rebanhomais.kmp.data.repository.AuthRepository
import com.jakatech.rebanhomais.kmp.domain.entity.AuthResult
import com.jakatech.rebanhomais.kmp.domain.entity.User
import kotlinx.coroutines.tasks.await

actual class FirebaseAuthRepository actual constructor() : AuthRepository {
    
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    actual override suspend fun login(email: String, password: String): AuthResult {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val firebaseUser = result.user
            if (firebaseUser != null) {
                AuthResult.Success(
                    User(
                        id = firebaseUser.uid,
                        email = firebaseUser.email ?: "",
                        isEmailVerified = firebaseUser.isEmailVerified
                    )
                )
            } else {
                AuthResult.Error(Exception("Falha ao obter usuário após login."))
            }
        } catch (e: Exception) {
            val mappedException = when (e) {
                is FirebaseAuthInvalidUserException -> Exception("Usuário não encontrado.")
                is FirebaseAuthInvalidCredentialsException -> Exception("Credenciais inválidas.")
                else -> e
            }
            AuthResult.Error(mappedException)
        }
    }

    actual override suspend fun register(email: String, password: String): AuthResult {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val firebaseUser = result.user
            if (firebaseUser != null) {
                AuthResult.Success(
                    User(
                        id = firebaseUser.uid,
                        email = firebaseUser.email ?: "",
                        isEmailVerified = firebaseUser.isEmailVerified
                    )
                )
            } else {
                AuthResult.Error(Exception("Falha ao criar usuário."))
            }
        } catch (e: Exception) {
            val mappedException = when (e) {
                is FirebaseAuthUserCollisionException -> Exception("E-mail já está em uso.")
                else -> e
            }
            AuthResult.Error(mappedException)
        }
    }

    actual override suspend fun logout() {
        auth.signOut()
    }

    actual override fun getCurrentUser(): User? {
        val firebaseUser = auth.currentUser ?: return null
        return User(
            id = firebaseUser.uid,
            email = firebaseUser.email ?: "",
            isEmailVerified = firebaseUser.isEmailVerified
        )
    }
}
