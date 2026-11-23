package com.jakatech.rebanhomais.kmp.domain.casodeuso

import com.jakatech.rebanhomais.kmp.domain.comum.ErroDominio
import com.jakatech.rebanhomais.kmp.domain.comum.GeradorDeId
import com.jakatech.rebanhomais.kmp.domain.comum.Resultado
import com.jakatech.rebanhomais.kmp.domain.model.Fazenda
import com.jakatech.rebanhomais.kmp.domain.model.Membro
import com.jakatech.rebanhomais.kmp.domain.model.Papel
import com.jakatech.rebanhomais.kmp.domain.repositorio.FazendaRepository

data class NovaFazenda(
    val nome: String,
    val donoUsuarioId: String,
    val municipio: String? = null,
    val uf: String? = null,
    val codigoPropriedade: String? = null
)

/** Cria a fazenda e já registra quem criou como DONO. */
class CadastrarFazenda(
    private val fazendas: FazendaRepository,
    private val ids: GeradorDeId
) {
    suspend operator fun invoke(nova: NovaFazenda): Resultado<Fazenda> {
        val nome = nova.nome.trim()
        if (nome.isEmpty()) return Resultado.Falha(ErroDominio.NomeObrigatorio("nome"))

        val fazenda = Fazenda(
            id = ids.novo(),
            nome = nome,
            municipio = nova.municipio?.trim()?.ifEmpty { null },
            uf = nova.uf?.trim()?.uppercase()?.ifEmpty { null },
            codigoPropriedade = nova.codigoPropriedade?.trim()?.ifEmpty { null }
        )
        fazendas.salvar(fazenda)
        fazendas.salvarMembro(Membro(fazendaId = fazenda.id, usuarioId = nova.donoUsuarioId, papel = Papel.DONO))
        return Resultado.Sucesso(fazenda)
    }
}
