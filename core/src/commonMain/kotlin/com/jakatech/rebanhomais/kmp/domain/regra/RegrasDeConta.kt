package com.jakatech.rebanhomais.kmp.domain.regra

import com.jakatech.rebanhomais.kmp.domain.comum.ErroDominio

/** Validações feitas no aparelho, antes de qualquer chamada ao servidor (RF-01). */
object RegrasDeConta {
    /** RN-01. */
    const val SENHA_MINIMA = 8
    const val TAMANHO_DO_CODIGO = 6

    private val formatoDeEmail = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    private val formatoDeCodigo = Regex("^[0-9]{$TAMANHO_DO_CODIGO}$")

    /** RN-02: sem espaços nas pontas e sem diferença de maiúsculas. */
    fun normalizarEmail(email: String): String = email.trim().lowercase()

    fun validarEmail(emailNormalizado: String): ErroDominio? =
        if (formatoDeEmail.matches(emailNormalizado)) null else ErroDominio.EmailInvalido

    fun validarSenhaNova(senha: String): ErroDominio? =
        if (senha.length >= SENHA_MINIMA) null else ErroDominio.SenhaFraca(SENHA_MINIMA)

    /** Aceita o código com espaços ("123 456"), comum ao copiar do e-mail. */
    fun normalizarCodigo(codigo: String): String = codigo.filterNot { it.isWhitespace() }

    fun validarCodigo(codigoNormalizado: String): ErroDominio? =
        if (formatoDeCodigo.matches(codigoNormalizado)) null else ErroDominio.CodigoInvalido
}
