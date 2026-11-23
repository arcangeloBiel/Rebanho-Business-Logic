package com.jakatech.rebanhomais.kmp.data.auth

import com.jakatech.rebanhomais.kmp.data.ConfiguracaoSupabase
import com.jakatech.rebanhomais.kmp.domain.comum.ErroDominio
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** RF-12: erros do Supabase Auth viram erros tipados do domínio. */
class MapeamentoDeErrosTest {

    @Test
    fun ca_02_2_credenciais_invalidas() {
        assertEquals(ErroDominio.CredenciaisInvalidas, erroDeAuth("invalid_credentials", 400))
        assertEquals(ErroDominio.CredenciaisInvalidas, erroDeAuth("invalid_grant", 400))
    }

    @Test
    fun ca_01_2_email_ja_cadastrado() {
        assertEquals(ErroDominio.EmailJaCadastrado, erroDeAuth("user_already_exists", 422))
        assertEquals(ErroDominio.EmailJaCadastrado, erroDeAuth("email_exists", 422))
    }

    @Test
    fun ca_01_6_codigo_vencido_ou_errado() =
        assertEquals(ErroDominio.CodigoInvalido, erroDeAuth("otp_expired", 403))

    @Test
    fun email_ainda_nao_confirmado() =
        assertEquals(ErroDominio.EmailNaoConfirmado, erroDeAuth("email_not_confirmed", 400))

    @Test
    fun senha_fraca_informa_o_minimo() =
        assertEquals(ErroDominio.SenhaFraca(8), erroDeAuth("weak_password", 422))

    @Test
    fun limite_de_envio_de_email() {
        assertEquals(ErroDominio.MuitasTentativas, erroDeAuth("over_email_send_rate_limit", 429))
        assertEquals(ErroDominio.MuitasTentativas, erroDeAuth(null, 429))
    }

    @Test
    fun erro_desconhecido_vira_inesperado_com_detalhe_para_log() {
        val erro = assertIs<ErroDominio.ErroInesperado>(erroDeAuth("algo_novo", 500))
        assertTrue("algo_novo" in erro.detalhe)
    }

    @Test
    fun configuracao_gerada_no_build_aponta_para_o_supabase() {
        assertTrue(ConfiguracaoSupabase.URL.startsWith("https://"))
        assertTrue(ConfiguracaoSupabase.URL.endsWith(".supabase.co"))
        assertTrue(ConfiguracaoSupabase.CHAVE_PUBLICA.isNotBlank())
        assertTrue(!ConfiguracaoSupabase.CHAVE_PUBLICA.startsWith("sb_secret_"), "nunca a chave secreta no app")
    }
}
