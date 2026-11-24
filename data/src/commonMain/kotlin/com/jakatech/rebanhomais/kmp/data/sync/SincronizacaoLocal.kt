package com.jakatech.rebanhomais.kmp.data.sync

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.db.SqlDriver
import com.jakatech.rebanhomais.kmp.data.local.RebanhoDatabase
import com.jakatech.rebanhomais.kmp.data.local.instanteAtual
import com.jakatech.rebanhomais.kmp.domain.comum.ErroDominio
import com.jakatech.rebanhomais.kmp.domain.comum.Resultado
import com.jakatech.rebanhomais.kmp.domain.model.EstadoDaSincronizacao
import com.jakatech.rebanhomais.kmp.domain.model.RegistroComErro
import com.jakatech.rebanhomais.kmp.domain.model.ResumoDoEnvio
import com.jakatech.rebanhomais.kmp.domain.model.TipoDeRegistro
import com.jakatech.rebanhomais.kmp.domain.repositorio.SincronizacaoRepository
import com.russhwolf.settings.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * Envio do banco do aparelho para o servidor (PRD Fase 4, fatia 1).
 *
 * - Tabela por tabela, na ordem das dependências (RF-02), em lotes de [tamanhoDoLote] (RNF-02).
 * - Lote recusado: reenvia um a um para achar o culpado; só ele fica com erro (RN-06, CA-05.1).
 * - Registro que depende de outro com erro espera, pendente, sem ir ao servidor (RN-06).
 * - Sem internet ou sem sessão: para; o que já foi aceito fica marcado (RF-03, RN-08, RN-09).
 */
internal class SincronizacaoLocal(
    private val driver: SqlDriver,
    private val servidor: ServidorDoRebanho,
    private val settings: Settings,
    private val contexto: CoroutineContext = Dispatchers.Default,
    private val agora: () -> String = ::instanteAtual,
    private val tamanhoDoLote: Int = 500
) : SincronizacaoRepository {

    private val consultas = RebanhoDatabase(driver).sincronizacaoQueries
    private val trava = Mutex()
    private val sincronizando = MutableStateFlow(false)
    private val ultimoEnvio = MutableStateFlow(settings.getStringOrNull(CHAVE_ULTIMO_ENVIO))

    override val estado: Flow<EstadoDaSincronizacao> = combine(
        consultas.naoSincronizados().asFlow().mapToList(contexto),
        sincronizando,
        ultimoEnvio
    ) { linhas, emAndamento, ultimo ->
        val comErro = linhas.count { it.sync_erro_codigo != null }
        EstadoDaSincronizacao(
            pendentes = linhas.size - comErro,
            comErro = comErro,
            ultimoEnvio = ultimo,
            sincronizando = emAndamento
        )
    }

    override fun observarRegistrosComErro(): Flow<List<RegistroComErro>> =
        consultas.registrosComErro { tipo, id, referencia, codigo, mensagem ->
            val tipoDoRegistro = TipoDeRegistro.valueOf(tipo)
            RegistroComErro(tipoDoRegistro, id, referencia, erroDoRegistro(tipoDoRegistro, referencia, codigo, mensagem))
        }.asFlow().mapToList(contexto)

    override suspend fun enviarPendentes(): Resultado<ResumoDoEnvio> = trava.withLock {
        sincronizando.value = true
        try {
            withContext(contexto) { enviar() }
        } finally {
            sincronizando.value = false
        }
    }

    private suspend fun enviar(): Resultado<ResumoDoEnvio> {
        // Ids que não estão no servidor por erro: quem aponta para eles espera (RN-06).
        val bloqueados = TABELAS_DE_SINCRONIZACAO.flatMap { driver.idsComErro(it) }.toMutableSet()
        var enviados = 0
        var recusados = 0
        var aguardando = 0

        for (tabela in TABELAS_DE_SINCRONIZACAO) {
            val linhas = ordenarPorDependencia(driver.pendentes(tabela))
            if (linhas.isEmpty()) continue

            val liberadas = mutableListOf<LinhaPendente>()
            for (linha in linhas) {
                if (tabela.referencias.any { linha.referencia(it) in bloqueados }) {
                    bloqueados += linha.id
                    aguardando++
                } else {
                    liberadas += linha
                }
            }

            for (lote in liberadas.chunked(tamanhoDoLote)) {
                when (val r = servidor.gravar(tabela.nome, lote.map { it.json })) {
                    RespostaDoServidor.Aceito -> {
                        lote.forEach { driver.marcarSincronizado(tabela, it) }
                        enviados += lote.size
                    }
                    is RespostaDoServidor.Recusado -> {
                        // Um a um, na mesma ordem, para só o culpado ficar com erro.
                        for (linha in lote) {
                            if (tabela.referencias.any { linha.referencia(it) in bloqueados }) {
                                bloqueados += linha.id
                                aguardando++
                                continue
                            }
                            val individual = if (lote.size == 1) r else servidor.gravar(tabela.nome, listOf(linha.json))
                            when (individual) {
                                RespostaDoServidor.Aceito -> {
                                    driver.marcarSincronizado(tabela, linha)
                                    enviados++
                                }
                                is RespostaDoServidor.Recusado -> {
                                    driver.marcarErro(tabela, linha, individual.codigo, individual.mensagem)
                                    bloqueados += linha.id
                                    recusados++
                                }
                                else -> return parar(tabela, individual)
                            }
                        }
                    }
                    else -> return parar(tabela, r)
                }
                driver.notifyListeners(tabela.nome)
            }
        }

        val instante = agora()
        settings.putString(CHAVE_ULTIMO_ENVIO, instante)
        ultimoEnvio.value = instante
        return Resultado.Sucesso(ResumoDoEnvio(enviados, recusados, aguardando))
    }

    /** Interrompido: avisa quem observa o que já foi marcado e devolve o motivo. */
    private fun parar(tabela: TabelaDeSincronizacao, resposta: RespostaDoServidor): Resultado<ResumoDoEnvio> {
        driver.notifyListeners(tabela.nome)
        return Resultado.Falha(
            if (resposta == RespostaDoServidor.SemSessao) ErroDominio.SemSessao else ErroDominio.SemConexao
        )
    }

    companion object {
        const val CHAVE_ULTIMO_ENVIO = "sync.ultimo_envio"
    }
}

/** Mães e pais pendentes vão antes das crias, para o servidor já conhecer o genitor (RF-02). */
internal fun ordenarPorDependencia(linhas: List<LinhaPendente>): List<LinhaPendente> {
    val pendentes = linhas.associateBy { it.id }
    val ordenadas = LinkedHashMap<String, LinhaPendente>()
    fun visitar(linha: LinhaPendente, caminho: Set<String>) {
        if (linha.id in ordenadas || linha.id in caminho) return
        for (coluna in listOf("mae_id", "pai_id")) {
            linha.referencia(coluna)?.let(pendentes::get)?.let { visitar(it, caminho + linha.id) }
        }
        ordenadas[linha.id] = linha
    }
    linhas.forEach { visitar(it, emptySet()) }
    return ordenadas.values.toList()
}

/** Código guardado no registro → erro do domínio para a tela (RN-07). */
internal fun erroDoRegistro(tipo: TipoDeRegistro, referencia: String, codigo: String?, mensagem: String?): ErroDominio =
    when (codigo) {
        "23505" -> if (tipo == TipoDeRegistro.ANIMAL) ErroDominio.BrincoDuplicado(referencia) else
            ErroDominio.ErroInesperado("duplicado no servidor: ${mensagem.orEmpty()}")
        "42501" -> ErroDominio.SemPermissao
        else -> ErroDominio.ErroInesperado("servidor recusou ($codigo): ${mensagem.orEmpty()}")
    }
