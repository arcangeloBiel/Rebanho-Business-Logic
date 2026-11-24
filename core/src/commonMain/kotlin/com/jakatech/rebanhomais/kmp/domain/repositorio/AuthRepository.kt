package com.jakatech.rebanhomais.kmp.domain.repositorio

import com.jakatech.rebanhomais.kmp.domain.comum.Resultado
import com.jakatech.rebanhomais.kmp.domain.model.EstadoSessao
import com.jakatech.rebanhomais.kmp.domain.model.Usuario
import kotlinx.coroutines.flow.Flow

/**
 * Conta e sessão. A implementação (Supabase Auth) guarda a sessão no aparelho
 * e a restaura ao abrir o app, com ou sem internet (RF-03).
 * Recebe e-mail e código já normalizados e validados pelos casos de uso.
 */
interface AuthRepository {
    /** Estado atual e as mudanças seguintes. */
    val sessao: Flow<EstadoSessao>

    /** Cria a conta e dispara o e-mail com o código de 6 dígitos. Não deixa logado. */
    suspend fun criarConta(email: String, senha: String, nome: String?): Resultado<Unit>

    /** Confirma a conta com o código; em caso de sucesso, a sessão passa a Logado. */
    suspend fun confirmarConta(email: String, codigo: String): Resultado<Usuario>

    suspend fun reenviarCodigo(email: String): Resultado<Unit>

    suspend fun entrar(email: String, senha: String): Resultado<Usuario>

    /** Envia por e-mail o código de 6 dígitos para recuperar a senha (RF-06). */
    suspend fun pedirCodigoDeRecuperacao(email: String): Resultado<Unit>

    /**
     * Confere o código de recuperação e troca a senha (RF-06).
     * Com código errado ou vencido, nada muda e a senha antiga continua valendo (CA-04.3).
     * Em caso de sucesso, a sessão passa a Logado.
     */
    suspend fun redefinirSenha(email: String, codigo: String, novaSenha: String): Resultado<Usuario>

    /** Apaga a sessão do aparelho mesmo sem internet (RF-05). */
    suspend fun sair()
}
