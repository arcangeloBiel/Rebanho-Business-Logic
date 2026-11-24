package com.jakatech.rebanhomais.kmp.data.comum

import com.jakatech.rebanhomais.kmp.data.auth.erroDeAuth
import com.jakatech.rebanhomais.kmp.domain.comum.ErroDominio
import com.jakatech.rebanhomais.kmp.domain.comum.Resultado
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import kotlinx.coroutines.CancellationException

/** Executa uma chamada ao Supabase e converte as exceções em erros do domínio (RF-12). */
internal inline fun <T> executar(bloco: () -> T): Resultado<T> = try {
    Resultado.Sucesso(bloco())
} catch (e: CancellationException) {
    throw e
} catch (e: AuthRestException) {
    Resultado.Falha(erroDeAuth(e.error, e.statusCode))
} catch (e: PostgrestRestException) {
    Resultado.Falha(erroDoBanco(e.code))
} catch (e: RestException) {
    Resultado.Falha(erroDeAuth(e.error, e.statusCode))
} catch (e: HttpRequestException) {
    Resultado.Falha(ErroDominio.SemConexao)
} catch (e: Exception) {
    Resultado.Falha(ErroDominio.ErroInesperado(e.message ?: e::class.simpleName.orEmpty()))
}

/** Códigos SQLSTATE devolvidos pelas funções e políticas do banco (migrations da Fase 2). */
internal fun erroDoBanco(sqlstate: String?): ErroDominio = when (sqlstate) {
    "42501" -> ErroDominio.SemPermissao
    "22023" -> ErroDominio.NomeObrigatorio("nome")
    "23514" -> ErroDominio.ErroInesperado("banco: regra de integridade violada ($sqlstate)")
    else -> ErroDominio.ErroInesperado("banco: sqlstate=$sqlstate")
}
