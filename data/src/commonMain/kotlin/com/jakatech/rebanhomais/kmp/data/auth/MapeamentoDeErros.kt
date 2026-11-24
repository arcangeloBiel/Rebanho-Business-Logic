package com.jakatech.rebanhomais.kmp.data.auth

import com.jakatech.rebanhomais.kmp.domain.comum.ErroDominio
import com.jakatech.rebanhomais.kmp.domain.regra.RegrasDeConta

/**
 * Traduz os códigos de erro do Supabase Auth (campo `error_code`) para erros do domínio (RF-12).
 * Função pura, para poder ser testada sem rede.
 */
internal fun erroDeAuth(codigo: String?, status: Int?): ErroDominio = when (codigo) {
    "invalid_credentials", "invalid_grant" -> ErroDominio.CredenciaisInvalidas
    "email_not_confirmed" -> ErroDominio.EmailNaoConfirmado
    "user_already_exists", "email_exists" -> ErroDominio.EmailJaCadastrado
    "weak_password" -> ErroDominio.SenhaFraca(RegrasDeConta.SENHA_MINIMA)
    "otp_expired" -> ErroDominio.CodigoInvalido
    "same_password" -> ErroDominio.SenhaIgualAnterior
    "email_address_invalid" -> ErroDominio.EmailInvalido
    "over_email_send_rate_limit", "over_request_rate_limit" -> ErroDominio.MuitasTentativas
    else -> if (status == 429) {
        ErroDominio.MuitasTentativas
    } else {
        ErroDominio.ErroInesperado("auth: status=$status codigo=$codigo")
    }
}
