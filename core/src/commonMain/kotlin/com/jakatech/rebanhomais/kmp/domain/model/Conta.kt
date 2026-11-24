package com.jakatech.rebanhomais.kmp.domain.model

/** Pessoa que usa o app. Os dados do rebanho pertencem às fazendas, não ao usuário. */
data class Usuario(
    val id: String,
    val email: String,
    val nome: String? = null
)

/** Uma fazenda da qual o usuário participa, com o papel dele nela. */
data class FazendaDoUsuario(
    val fazenda: Fazenda,
    val papel: Papel
)

/** Estado da sessão que a UI observa (PRD Fase 2, RF-02). */
sealed interface EstadoSessao {
    /** Lendo a sessão guardada no aparelho. Mostrar uma tela de abertura. */
    data object Carregando : EstadoSessao

    data object Deslogado : EstadoSessao

    data class Logado(
        val usuario: Usuario,
        /**
         * Sessão restaurada do aparelho sem conseguir falar com o servidor (RN-08).
         * O app funciona normalmente; a renovação acontece quando a internet voltar.
         */
        val semConexao: Boolean = false,
        /** Preenchido na fatia 3 (minhas fazendas). */
        val fazendas: List<FazendaDoUsuario> = emptyList(),
        val fazendaSelecionada: FazendaDoUsuario? = null
    ) : EstadoSessao
}
