package com.jakatech.rebanhomais.kmp.data.auth

import com.jakatech.rebanhomais.kmp.domain.comum.ErroDominio
import com.jakatech.rebanhomais.kmp.domain.comum.Resultado
import com.jakatech.rebanhomais.kmp.domain.model.EstadoSessao
import com.jakatech.rebanhomais.kmp.domain.model.Usuario
import com.jakatech.rebanhomais.kmp.domain.repositorio.AuthRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * Conta e sessão com o Supabase Auth.
 *
 * A sessão fica guardada no aparelho pelo próprio supabase-kt e é restaurada ao abrir o app (RF-03).
 * O token é renovado automaticamente quando há internet (RF-04).
 */
class SupabaseAuthRepository(private val supabase: SupabaseClient) : AuthRepository {

    private val auth get() = supabase.auth

    override val sessao: Flow<EstadoSessao> = auth.sessionStatus
        .map { status -> estadoDe(status) }
        .distinctUntilChanged()

    private fun estadoDe(status: SessionStatus): EstadoSessao = when (status) {
        is SessionStatus.Initializing -> EstadoSessao.Carregando
        is SessionStatus.NotAuthenticated -> EstadoSessao.Deslogado
        is SessionStatus.Authenticated -> {
            val usuario = (status.session.user ?: auth.currentUserOrNull())?.paraUsuario()
            if (usuario != null) EstadoSessao.Logado(usuario) else EstadoSessao.Deslogado
        }
        // RN-08: sem internet para renovar o token, mas a sessão guardada continua valendo.
        is SessionStatus.RefreshFailure -> {
            val usuario = auth.currentSessionOrNull()?.user?.paraUsuario()
            if (usuario != null) EstadoSessao.Logado(usuario, semConexao = true) else EstadoSessao.Deslogado
        }
    }

    override suspend fun criarConta(email: String, senha: String, nome: String?): Resultado<Unit> = chamar {
        val usuario = auth.signUpWith(Email) {
            this.email = email
            this.password = senha
            data = buildJsonObject { if (nome != null) put("nome", nome) }
        }
        // Com confirmação de e-mail ligada, o Supabase não devolve erro para e-mail já cadastrado:
        // devolve um usuário "disfarçado", sem identidades. É assim que se detecta o CA-01.2.
        if (usuario != null && usuario.identities?.isEmpty() == true) {
            return Resultado.Falha(ErroDominio.EmailJaCadastrado)
        }
    }

    override suspend fun confirmarConta(email: String, codigo: String): Resultado<Usuario> = chamar {
        auth.verifyEmailOtp(type = OtpType.Email.EMAIL, email = email, token = codigo)
        usuarioAtual()
    }

    override suspend fun reenviarCodigo(email: String): Resultado<Unit> = chamar {
        auth.resendEmail(OtpType.Email.SIGNUP, email)
    }

    override suspend fun entrar(email: String, senha: String): Resultado<Usuario> = chamar {
        auth.signInWith(Email) {
            this.email = email
            this.password = senha
        }
        usuarioAtual()
    }

    override suspend fun sair() {
        try {
            auth.signOut()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Sem internet não dá para avisar o servidor, mas a sessão sai do aparelho mesmo assim (RF-05).
            auth.clearSession()
        }
    }

    private fun usuarioAtual(): Usuario =
        auth.currentUserOrNull()?.paraUsuario()
            ?: throw IllegalStateException("Sessão não encontrada depois da autenticação.")

    /** Executa a chamada e converte as exceções do supabase-kt em erros do domínio. */
    private inline fun <T> chamar(bloco: () -> T): Resultado<T> = try {
        Resultado.Sucesso(bloco())
    } catch (e: CancellationException) {
        throw e
    } catch (e: AuthRestException) {
        Resultado.Falha(erroDeAuth(e.error, e.statusCode))
    } catch (e: RestException) {
        Resultado.Falha(erroDeAuth(e.error, e.statusCode))
    } catch (e: HttpRequestException) {
        Resultado.Falha(ErroDominio.SemConexao)
    } catch (e: Exception) {
        Resultado.Falha(ErroDominio.ErroInesperado(e.message ?: e::class.simpleName.orEmpty()))
    }
}

internal fun UserInfo.paraUsuario(): Usuario = Usuario(
    id = id,
    email = email.orEmpty(),
    nome = (userMetadata?.get("nome") as? JsonPrimitive)?.contentOrNull
)
