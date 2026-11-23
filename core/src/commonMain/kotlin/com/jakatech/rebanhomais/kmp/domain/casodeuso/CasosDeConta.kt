package com.jakatech.rebanhomais.kmp.domain.casodeuso

import com.jakatech.rebanhomais.kmp.domain.comum.ErroDominio
import com.jakatech.rebanhomais.kmp.domain.comum.Resultado
import com.jakatech.rebanhomais.kmp.domain.model.EstadoSessao
import com.jakatech.rebanhomais.kmp.domain.model.Usuario
import com.jakatech.rebanhomais.kmp.domain.regra.RegrasDeConta
import com.jakatech.rebanhomais.kmp.domain.repositorio.AuthRepository
import kotlinx.coroutines.flow.Flow

/** O e-mail para onde o código foi enviado, já normalizado. A UI leva o usuário para a tela do código. */
data class ContaCriada(val email: String)

/** HU-01: criar conta com e-mail e senha. */
class CriarConta(private val auth: AuthRepository) {
    suspend operator fun invoke(email: String, senha: String, nome: String? = null): Resultado<ContaCriada> {
        val emailNormalizado = RegrasDeConta.normalizarEmail(email)
        RegrasDeConta.validarEmail(emailNormalizado)?.let { return Resultado.Falha(it) }
        RegrasDeConta.validarSenhaNova(senha)?.let { return Resultado.Falha(it) }
        return when (val r = auth.criarConta(emailNormalizado, senha, nome?.trim()?.ifEmpty { null })) {
            is Resultado.Falha -> r
            is Resultado.Sucesso -> Resultado.Sucesso(ContaCriada(emailNormalizado))
        }
    }
}

/** HU-01 (CA-01.5, CA-01.6): confirmar a conta com o código de 6 dígitos. */
class ConfirmarConta(private val auth: AuthRepository) {
    suspend operator fun invoke(email: String, codigo: String): Resultado<Usuario> {
        val emailNormalizado = RegrasDeConta.normalizarEmail(email)
        RegrasDeConta.validarEmail(emailNormalizado)?.let { return Resultado.Falha(it) }
        val codigoNormalizado = RegrasDeConta.normalizarCodigo(codigo)
        RegrasDeConta.validarCodigo(codigoNormalizado)?.let { return Resultado.Falha(it) }
        return auth.confirmarConta(emailNormalizado, codigoNormalizado)
    }
}

/** HU-01 (CA-01.6): pedir um código novo. */
class ReenviarCodigo(private val auth: AuthRepository) {
    suspend operator fun invoke(email: String): Resultado<Unit> {
        val emailNormalizado = RegrasDeConta.normalizarEmail(email)
        RegrasDeConta.validarEmail(emailNormalizado)?.let { return Resultado.Falha(it) }
        return auth.reenviarCodigo(emailNormalizado)
    }
}

/**
 * HU-02: entrar com e-mail e senha.
 * A regra de tamanho da senha vale só para senha nova; no login, senha vazia já é credencial inválida.
 */
class Entrar(private val auth: AuthRepository) {
    suspend operator fun invoke(email: String, senha: String): Resultado<Usuario> {
        val emailNormalizado = RegrasDeConta.normalizarEmail(email)
        RegrasDeConta.validarEmail(emailNormalizado)?.let { return Resultado.Falha(it) }
        if (senha.isEmpty()) return Resultado.Falha(ErroDominio.CredenciaisInvalidas)
        return auth.entrar(emailNormalizado, senha)
    }
}

/** HU-03: sair e apagar a sessão do aparelho. */
class Sair(private val auth: AuthRepository) {
    suspend operator fun invoke() = auth.sair()
}

/** RF-02: a UI observa este fluxo para decidir entre abertura, login e app. */
class ObservarSessao(private val auth: AuthRepository) {
    operator fun invoke(): Flow<EstadoSessao> = auth.sessao
}
