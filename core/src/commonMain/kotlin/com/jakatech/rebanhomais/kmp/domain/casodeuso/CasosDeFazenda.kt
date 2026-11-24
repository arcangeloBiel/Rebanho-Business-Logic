package com.jakatech.rebanhomais.kmp.domain.casodeuso

import com.jakatech.rebanhomais.kmp.domain.comum.ErroDominio
import com.jakatech.rebanhomais.kmp.domain.comum.GeradorDeId
import com.jakatech.rebanhomais.kmp.domain.comum.Resultado
import com.jakatech.rebanhomais.kmp.domain.model.Fazenda
import com.jakatech.rebanhomais.kmp.domain.model.FazendaDoUsuario
import com.jakatech.rebanhomais.kmp.domain.regra.RegrasDeFazenda
import com.jakatech.rebanhomais.kmp.domain.repositorio.MinhasFazendasRepository
import com.jakatech.rebanhomais.kmp.domain.repositorio.PreferenciasRepository
import kotlinx.coroutines.flow.first

data class NovaFazenda(
    val nome: String,
    val municipio: String? = null,
    val uf: String? = null,
    val codigoPropriedade: String? = null
)

/**
 * HU-05: cria a fazenda com o usuário logado como DONO, numa única operação no servidor (RF-07),
 * e já a deixa selecionada.
 */
class CadastrarFazenda(
    private val minhasFazendas: MinhasFazendasRepository,
    private val preferencias: PreferenciasRepository,
    private val ids: GeradorDeId
) {
    suspend operator fun invoke(nova: NovaFazenda): Resultado<FazendaDoUsuario> {
        val nome = nova.nome.trim()
        if (nome.isEmpty()) return Resultado.Falha(ErroDominio.NomeObrigatorio("nome"))
        val uf = RegrasDeFazenda.normalizarUf(nova.uf)
        if (!RegrasDeFazenda.ufValida(uf)) return Resultado.Falha(ErroDominio.UfInvalida)

        val fazenda = Fazenda(
            id = ids.novo(),
            nome = nome,
            municipio = nova.municipio?.trim()?.ifEmpty { null },
            uf = uf,
            codigoPropriedade = nova.codigoPropriedade?.trim()?.ifEmpty { null }
        )
        val resultado = minhasFazendas.criar(fazenda)
        if (resultado is Resultado.Sucesso) preferencias.selecionarFazenda(resultado.valor.fazenda.id)
        return resultado
    }
}

/** HU-06: busca no servidor as fazendas do usuário, com o papel dele em cada uma (RF-08). */
class ListarMinhasFazendas(private val minhasFazendas: MinhasFazendasRepository) {
    suspend operator fun invoke(): Resultado<List<FazendaDoUsuario>> = minhasFazendas.atualizar()
}

/** HU-06: escolhe a fazenda em que o usuário vai trabalhar; a escolha fica guardada (RF-09). */
class SelecionarFazenda(
    private val minhasFazendas: MinhasFazendasRepository,
    private val preferencias: PreferenciasRepository
) {
    suspend operator fun invoke(fazendaId: String): Resultado<FazendaDoUsuario> {
        val escolhida = minhasFazendas.fazendas.first().firstOrNull { it.fazenda.id == fazendaId }
            ?: return Resultado.Falha(ErroDominio.FazendaNaoEncontrada(fazendaId))
        preferencias.selecionarFazenda(fazendaId)
        return Resultado.Sucesso(escolhida)
    }
}
