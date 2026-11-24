package com.jakatech.rebanhomais.kmp.data.fazendas

import com.jakatech.rebanhomais.kmp.domain.model.Fazenda
import com.jakatech.rebanhomais.kmp.domain.model.FazendaDoUsuario
import com.jakatech.rebanhomais.kmp.domain.model.Papel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JsonDeFazendasTest {

    /** Formato devolvido pelo PostgREST para membros?select=papel,deleted_at,fazendas(...). */
    private val respostaDoServidor = """
        [
          {"papel": "TECNICO", "deleted_at": null,
           "fazendas": {"id": "f2", "nome": "sítio boa vista", "municipio": null, "uf": "MG", "codigo_propriedade": null, "deleted_at": null}},
          {"papel": "DONO", "deleted_at": null,
           "fazendas": {"id": "f1", "nome": "Fazenda Água Limpa", "municipio": "Uberaba", "uf": "MG", "codigo_propriedade": "123", "deleted_at": null}},
          {"papel": "FUNCIONARIO", "deleted_at": "2026-11-01T10:00:00+00:00",
           "fazendas": {"id": "f3", "nome": "Saí desta", "municipio": null, "uf": null, "codigo_propriedade": null, "deleted_at": null}},
          {"papel": "DONO", "deleted_at": null,
           "fazendas": {"id": "f4", "nome": "Excluída", "municipio": null, "uf": null, "codigo_propriedade": null, "deleted_at": "2026-11-02T10:00:00+00:00"}},
          {"papel": "GERENTE", "deleted_at": null,
           "fazendas": {"id": "f5", "nome": "Papel desconhecido", "municipio": null, "uf": null, "codigo_propriedade": null, "deleted_at": null}}
        ]
    """.trimIndent()

    @Test
    fun ca_06_1_le_fazendas_com_papel_ignorando_excluidas_e_ordenando_pelo_nome() {
        val lista = JsonDeFazendas.minhasFazendasDe(JsonDeFazendas.lista(respostaDoServidor))
        assertEquals(listOf("f1", "f2"), lista.map { it.fazenda.id })
        assertEquals(listOf(Papel.DONO, Papel.TECNICO), lista.map { it.papel })
        assertEquals("Uberaba", lista.first().fazenda.municipio)
        assertEquals("123", lista.first().fazenda.codigoPropriedade)
        assertNull(lista.last().fazenda.municipio)
    }

    @Test
    fun le_o_retorno_de_criar_fazenda() {
        val fazenda = JsonDeFazendas.fazendaDe(
            JsonDeFazendas.objeto("""{"id": "f9", "nome": "Nova", "municipio": null, "uf": "GO", "codigo_propriedade": null, "created_at": "x"}""")
        )
        assertEquals(Fazenda("f9", "Nova", uf = "GO"), fazenda)
    }

    @Test
    fun cache_volta_igual_para_o_mesmo_usuario() {
        val lista = listOf(
            FazendaDoUsuario(Fazenda("f1", "Água Limpa", "Uberaba", "MG", "123"), Papel.DONO),
            FazendaDoUsuario(Fazenda("f2", "Boa Vista"), Papel.TECNICO)
        )
        assertEquals(lista, JsonDeFazendas.deCache(JsonDeFazendas.paraCache("u1", lista), "u1"))
    }

    @Test
    fun ordena_pelo_nome_ignorando_acentos_e_maiusculas() {
        val lista = listOf("Boa Vista", "água limpa", "Córrego Fundo", "Cachoeira").map {
            FazendaDoUsuario(Fazenda(it, it), Papel.DONO)
        }
        assertEquals(
            listOf("água limpa", "Boa Vista", "Cachoeira", "Córrego Fundo"),
            JsonDeFazendas.ordenar(lista).map { it.fazenda.nome }
        )
    }

    @Test
    fun cache_de_outro_usuario_e_ignorado() {
        val texto = JsonDeFazendas.paraCache("u1", listOf(FazendaDoUsuario(Fazenda("f1", "A"), Papel.DONO)))
        assertNull(JsonDeFazendas.deCache(texto, "u2"))
    }

    @Test
    fun cache_ausente_ou_corrompido_e_ignorado() {
        assertNull(JsonDeFazendas.deCache(null, "u1"))
        assertNull(JsonDeFazendas.deCache("{isto não é json", "u1"))
        assertNull(JsonDeFazendas.deCache("""{"usuario": "u1", "fazendas": 3}""", "u1"))
    }
}
