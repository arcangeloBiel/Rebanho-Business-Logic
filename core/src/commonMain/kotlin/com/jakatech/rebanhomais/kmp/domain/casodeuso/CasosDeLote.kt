package com.jakatech.rebanhomais.kmp.domain.casodeuso

import com.jakatech.rebanhomais.kmp.domain.comum.ErroDominio
import com.jakatech.rebanhomais.kmp.domain.comum.GeradorDeId
import com.jakatech.rebanhomais.kmp.domain.comum.Resultado
import com.jakatech.rebanhomais.kmp.domain.model.Lote
import com.jakatech.rebanhomais.kmp.domain.repositorio.LoteRepository
import com.jakatech.rebanhomais.kmp.domain.repositorio.MinhasFazendasRepository
import com.jakatech.rebanhomais.kmp.domain.repositorio.PreferenciasRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/*
 * Lotes no banco local (PRD Fase 3, fatia 4). Lote é só um agrupamento de animais da fazenda
 * (pasto, piquete, safra). Qualquer papel pode criar e renomear, como no RLS do Supabase.
 */

/**
 * Um animal só pode entrar num lote da própria fazenda. O Supabase recusaria o contrário
 * (FK (fazenda_id, lote_id)) e o registro ficaria preso na sincronização da Fase 4.
 */
internal suspend fun LoteRepository.validarDaFazenda(loteId: String?, fazendaId: String): ErroDominio? {
    if (loteId == null) return null
    val lote = buscarPorId(loteId)
    return if (lote == null || lote.fazendaId != fazendaId) ErroDominio.LoteNaoEncontrado(loteId) else null
}

data class NovoLote(val fazendaId: String, val nome: String)

class CadastrarLote(
    private val lotes: LoteRepository,
    private val ids: GeradorDeId
) {
    suspend operator fun invoke(novo: NovoLote): Resultado<Lote> {
        val nome = novo.nome.trim().ifEmpty { return Resultado.Falha(ErroDominio.NomeObrigatorio("nome")) }
        val lote = Lote(id = ids.novo(), fazendaId = novo.fazendaId, nome = nome)
        lotes.salvar(lote)
        return Resultado.Sucesso(lote)
    }
}

class RenomearLote(private val lotes: LoteRepository) {
    suspend operator fun invoke(loteId: String, nome: String): Resultado<Lote> {
        val lote = lotes.buscarPorId(loteId) ?: return Resultado.Falha(ErroDominio.LoteNaoEncontrado(loteId))
        val novoNome = nome.trim().ifEmpty { return Resultado.Falha(ErroDominio.NomeObrigatorio("nome")) }
        val renomeado = lote.copy(nome = novoNome)
        // Sem mudança, nada é gravado: o registro não volta a ficar pendente à toa.
        if (renomeado != lote) lotes.salvar(renomeado)
        return Resultado.Sucesso(renomeado)
    }
}

/** Os lotes da fazenda selecionada, em ordem de nome, atualizados a cada mudança. Sem fazenda, vazio (RN-01). */
class ObservarLotes(
    private val lotes: LoteRepository,
    private val minhasFazendas: MinhasFazendasRepository,
    private val preferencias: PreferenciasRepository
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<List<Lote>> =
        fazendaSelecionadaId(minhasFazendas, preferencias).flatMapLatest { fazendaId ->
            if (fazendaId == null) flowOf(emptyList()) else lotes.observarPorFazenda(fazendaId)
        }
}
