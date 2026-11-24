package com.jakatech.rebanhomais.kmp.data.sync

import com.jakatech.rebanhomais.kmp.data.fazendas.SupabaseMinhasFazendasRepository
import com.jakatech.rebanhomais.kmp.data.local.AnimaisLocais
import com.jakatech.rebanhomais.kmp.data.local.PesagensLocais
import com.jakatech.rebanhomais.kmp.data.local.RebanhoDatabase
import com.jakatech.rebanhomais.kmp.data.local.driverEmMemoria
import com.jakatech.rebanhomais.kmp.domain.comum.ErroDominio
import com.jakatech.rebanhomais.kmp.domain.comum.Resultado
import com.jakatech.rebanhomais.kmp.domain.model.Animal
import com.jakatech.rebanhomais.kmp.domain.model.Origem
import com.jakatech.rebanhomais.kmp.domain.model.Pesagem
import com.jakatech.rebanhomais.kmp.domain.model.Sexo
import com.russhwolf.settings.MapSettings
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * PRD Fase 4, RNF-06: dois aparelhos do mesmo usuário sincronizando por um Supabase local de verdade
 * (migrations, RLS e gatilhos da Fase 2). Roda no job "Integração" do CI, que sobe o Supabase e
 * passa REBANHO_TESTE_URL, REBANHO_TESTE_CHAVE e REBANHO_TESTE_SERVICO. Sem elas, é pulado
 * (ou falha, se REBANHO_TESTE_OBRIGATORIO=1).
 */
@OptIn(ExperimentalUuidApi::class)
class IntegracaoSupabaseTest {
    private val url: String? = System.getenv("REBANHO_TESTE_URL")?.takeIf { it.isNotBlank() }
    private val chave: String? = System.getenv("REBANHO_TESTE_CHAVE")?.takeIf { it.isNotBlank() }
    private val servico: String? = System.getenv("REBANHO_TESTE_SERVICO")?.takeIf { it.isNotBlank() }
    private val obrigatorio = System.getenv("REBANHO_TESTE_OBRIGATORIO") == "1"

    private fun novoId() = Uuid.random().toString()

    private fun cliente(chaveDoCliente: String): SupabaseClient =
        createSupabaseClient(supabaseUrl = url!!, supabaseKey = chaveDoCliente) {
            install(Auth) {
                alwaysAutoRefresh = false
                autoLoadFromStorage = false
                autoSaveToStorage = false
            }
            install(Postgrest)
        }

    private class Aparelho(cliente: SupabaseClient, escopo: CoroutineScope) {
        val driver = driverEmMemoria()
        private val banco = RebanhoDatabase(driver)
        val animais = AnimaisLocais(banco)
        val pesagens = PesagensLocais(banco)
        val sync = SincronizacaoLocal(
            driver, ServidorSupabase(cliente), MapSettings(),
            SupabaseMinhasFazendasRepository(cliente, MapSettings(), escopo)
        )
    }

    private fun <T> Resultado<T>.ok(): T = when (this) {
        is Resultado.Sucesso -> valor
        is Resultado.Falha -> fail("Esperava sucesso, veio $erro")
    }

    private fun animal(id: String, fazendaId: String, brinco: String) = Animal(
        id = id, fazendaId = fazendaId, brinco = brinco, sexo = Sexo.FEMEA, origem = Origem.NASCIDO_NA_FAZENDA,
        dataNascimento = LocalDate(2026, 1, 10), nome = "Mimosa"
    )

    @Test
    fun dois_aparelhos_sincronizam_pelo_supabase() = runBlocking {
        if (url == null || chave == null || servico == null) {
            if (obrigatorio) fail("REBANHO_TESTE_URL, REBANHO_TESTE_CHAVE e REBANHO_TESTE_SERVICO são obrigatórias neste job")
            return@runBlocking
        }

        // Usuário já confirmado, criado pela API de admin do Supabase local.
        val email = "teste-${novoId()}@rebanho.test"
        val senha = "senha-de-teste-123"
        val admin = cliente(servico)
        admin.auth.importAuthToken(servico)
        admin.auth.admin.createUserWithEmail {
            this.email = email
            password = senha
            autoConfirm = true
        }

        val usuario = cliente(chave)
        usuario.auth.signInWith(Email) {
            this.email = email
            password = senha
        }
        val fazendaId = novoId()
        usuario.postgrest.rpc("criar_fazenda", buildJsonObject {
            put("p_id", fazendaId)
            put("p_nome", "Fazenda de integração")
        })

        val escopo = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val celularA = Aparelho(usuario, escopo)
        val celularB = Aparelho(usuario, escopo)
        try {
            // CA-01.1 e CA-02.1: A lança sem sinal, envia; B recebe.
            val vacaId = novoId()
            celularA.animais.salvar(animal(vacaId, fazendaId, "A1"))
            celularA.pesagens.salvar(Pesagem(novoId(), fazendaId, vacaId, LocalDate(2026, 10, 1), 312.5))
            assertEquals(2, celularA.sync.sincronizar().ok().envio.enviados)
            assertTrue(celularA.sync.estado.first().tudoSalvo)

            assertTrue(celularB.sync.sincronizar().ok().recebimento.recebidos >= 2)
            assertEquals("A1", assertNotNull(celularB.animais.buscarPorId(vacaId)).brinco)
            assertEquals(312.5, celularB.pesagens.listarPorAnimal(vacaId).single().pesoKg)

            // RN-01: sincronizar de novo não reenvia nem duplica.
            assertEquals(0, celularA.sync.sincronizar().ok().envio.enviados)

            // CA-02.2: o DONO exclui em A; some em B.
            celularA.animais.excluir(vacaId)
            celularA.sync.sincronizar().ok()
            celularB.sync.sincronizar().ok()
            assertNull(celularB.animais.buscarPorId(vacaId))

            // CA-05.1: o mesmo brinco nos dois sem sinal; o segundo a chegar fica com erro.
            celularA.animais.salvar(animal(novoId(), fazendaId, "77"))
            celularB.animais.salvar(animal(novoId(), fazendaId, "77"))
            celularA.sync.sincronizar().ok()
            val resumoB = celularB.sync.sincronizar().ok()
            assertEquals(1, resumoB.envio.recusados)
            assertEquals(ErroDominio.BrincoDuplicado("77"), celularB.sync.observarRegistrosComErro().first().single().erro)
        } finally {
            escopo.cancel()
            celularA.driver.close()
            celularB.driver.close()
        }
    }
}
