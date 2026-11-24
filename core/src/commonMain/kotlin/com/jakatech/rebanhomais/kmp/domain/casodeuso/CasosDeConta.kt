package com.jakatech.rebanhomais.kmp.domain.casodeuso

import com.jakatech.rebanhomais.kmp.domain.comum.ErroDominio
import com.jakatech.rebanhomais.kmp.domain.comum.Resultado
import com.jakatech.rebanhomais.kmp.domain.model.EstadoSessao
import com.jakatech.rebanhomais.kmp.domain.model.Usuario
import com.jakatech.rebanhomais.kmp.domain.regra.RegrasDeConta
import com.jakatech.rebanhomais.kmp.domain.regra.RegrasDeFazenda
import com.jakatech.rebanhomais.kmp.domain.repositorio.AuthRepository
import com.jakatech.rebanhomais.kmp.domain.repositorio.MinhasFazendasRepository
import com.jakatech.rebanhomais.kmp.domain.repositorio.PreferenciasRepository
import com.jakatech.rebanhomais.kmp.domain.repositorio.SincronizacaoRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first

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

/**
 * HU-04 (CA-04.1): pede o código de recuperação de senha.
 * A resposta é a mesma exista ou não a conta (RN-03): só falhas que o usuário pode
 * resolver (e-mail mal digitado, sem internet, muitas tentativas) aparecem.
 */
class PedirRecuperacaoDeSenha(private val auth: AuthRepository) {
    suspend operator fun invoke(email: String): Resultado<Unit> {
        val emailNormalizado = RegrasDeConta.normalizarEmail(email)
        RegrasDeConta.validarEmail(emailNormalizado)?.let { return Resultado.Falha(it) }
        return when (val r = auth.pedirCodigoDeRecuperacao(emailNormalizado)) {
            is Resultado.Sucesso -> r
            is Resultado.Falha -> when (r.erro) {
                ErroDominio.SemConexao, ErroDominio.MuitasTentativas -> r
                else -> Resultado.Sucesso(Unit)
            }
        }
    }
}

/** HU-04 (CA-04.2, CA-04.3): troca a senha usando o código recebido por e-mail. */
class RedefinirSenha(private val auth: AuthRepository) {
    suspend operator fun invoke(email: String, codigo: String, novaSenha: String): Resultado<Usuario> {
        val emailNormalizado = RegrasDeConta.normalizarEmail(email)
        RegrasDeConta.validarEmail(emailNormalizado)?.let { return Resultado.Falha(it) }
        val codigoNormalizado = RegrasDeConta.normalizarCodigo(codigo)
        RegrasDeConta.validarCodigo(codigoNormalizado)?.let { return Resultado.Falha(it) }
        RegrasDeConta.validarSenhaNova(novaSenha)?.let { return Resultado.Falha(it) }
        return auth.redefinirSenha(emailNormalizado, codigoNormalizado, novaSenha)
    }
}

/**
 * HU-03 (Fase 2) e HU-06 (Fase 4): sair e apagar do aparelho a sessão, as fazendas, a fazenda selecionada
 * e todo o rebanho, para que outra pessoa no mesmo celular não veja os dados (CA-06.3, RNF-05).
 *
 * Antes, tenta enviar o que está só no aparelho (CA-06.1). Se ainda sobrar algo (sem internet ou recusado),
 * não sai: devolve [ErroDominio.DadosNaoEnviados] com os números (CA-06.2). A tela avisa, com
 * "Ficar e tentar de novo" em destaque, e só chama de novo com [confirmarPerda] = true depois de o
 * usuário confirmar duas vezes (RN-10, P-01).
 */
class Sair(
    private val auth: AuthRepository,
    private val minhasFazendas: MinhasFazendasRepository,
    private val preferencias: PreferenciasRepository,
    private val sincronizacao: SincronizacaoRepository
) {
    suspend operator fun invoke(confirmarPerda: Boolean = false): Resultado<Unit> {
        sincronizacao.enviarPendentes() // sem internet ou sem sessão, o que sobrar aparece no estado
        val estado = sincronizacao.estado.first()
        if (!estado.tudoSalvo && !confirmarPerda) {
            return Resultado.Falha(ErroDominio.DadosNaoEnviados(estado.pendentes, estado.comErro))
        }
        auth.sair()
        sincronizacao.apagarDadosLocais()
        minhasFazendas.limpar()
        preferencias.selecionarFazenda(null)
        return Resultado.Sucesso(Unit)
    }
}

/**
 * RF-02: a UI observa este fluxo para decidir entre abertura, login, criar a primeira fazenda e o app.
 * Quando logado, junta as fazendas do usuário e a selecionada (RN-07).
 */
class ObservarSessao(
    private val auth: AuthRepository,
    private val minhasFazendas: MinhasFazendasRepository,
    private val preferencias: PreferenciasRepository
) {
    operator fun invoke(): Flow<EstadoSessao> =
        combine(auth.sessao, minhasFazendas.fazendas, preferencias.fazendaSelecionadaId) { sessao, fazendas, idGuardado ->
            when (sessao) {
                is EstadoSessao.Logado -> sessao.copy(
                    fazendas = fazendas,
                    fazendaSelecionada = RegrasDeFazenda.escolherSelecionada(fazendas, idGuardado)
                )
                else -> sessao
            }
        }.distinctUntilChanged()
}
