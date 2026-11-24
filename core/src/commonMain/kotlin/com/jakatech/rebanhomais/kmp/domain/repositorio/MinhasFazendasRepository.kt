package com.jakatech.rebanhomais.kmp.domain.repositorio

import com.jakatech.rebanhomais.kmp.domain.comum.Resultado
import com.jakatech.rebanhomais.kmp.domain.model.Fazenda
import com.jakatech.rebanhomais.kmp.domain.model.FazendaDoUsuario
import kotlinx.coroutines.flow.Flow

/**
 * Fazendas do usuário logado, cada uma com o papel dele (RF-08).
 * A implementação guarda a última lista no aparelho para funcionar sem internet
 * e a atualiza sozinha quando o usuário entra.
 */
interface MinhasFazendasRepository {
    val fazendas: Flow<List<FazendaDoUsuario>>

    /** Busca a lista no servidor. */
    suspend fun atualizar(): Resultado<List<FazendaDoUsuario>>

    /** Cria a fazenda e o vínculo DONO numa única operação no servidor (RF-07). */
    suspend fun criar(fazenda: Fazenda): Resultado<FazendaDoUsuario>

    /** Apaga a lista guardada no aparelho (ao sair). */
    suspend fun limpar()
}

/** Preferências guardadas só no aparelho. */
interface PreferenciasRepository {
    /** Id da fazenda escolhida pelo usuário (RF-09). */
    val fazendaSelecionadaId: Flow<String?>

    suspend fun selecionarFazenda(fazendaId: String?)
}
