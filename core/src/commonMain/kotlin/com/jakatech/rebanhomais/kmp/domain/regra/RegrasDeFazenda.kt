package com.jakatech.rebanhomais.kmp.domain.regra

import com.jakatech.rebanhomais.kmp.domain.model.FazendaDoUsuario

object RegrasDeFazenda {
    private val formatoDeUf = Regex("^[A-Z]{2}$")

    /** " mg " vira "MG"; vazio vira nulo. */
    fun normalizarUf(uf: String?): String? = uf?.trim()?.uppercase()?.ifEmpty { null }

    fun ufValida(ufNormalizada: String?): Boolean = ufNormalizada == null || formatoDeUf.matches(ufNormalizada)

    /**
     * RN-07: a fazenda guardada como selecionada, se ainda estiver na lista;
     * senão, a primeira da lista; com a lista vazia, nenhuma.
     */
    fun escolherSelecionada(fazendas: List<FazendaDoUsuario>, idGuardado: String?): FazendaDoUsuario? =
        fazendas.firstOrNull { it.fazenda.id == idGuardado } ?: fazendas.firstOrNull()
}
