package com.jakatech.rebanhomais.kmp.domain

import com.jakatech.rebanhomais.kmp.domain.casodeuso.CadastrarAnimal
import com.jakatech.rebanhomais.kmp.domain.casodeuso.CadastrarProtocolo
import com.jakatech.rebanhomais.kmp.domain.casodeuso.ListarPendenciasVacinais
import com.jakatech.rebanhomais.kmp.domain.casodeuso.NovaPesagem
import com.jakatech.rebanhomais.kmp.domain.casodeuso.NovaSaida
import com.jakatech.rebanhomais.kmp.domain.casodeuso.NovaVacinacao
import com.jakatech.rebanhomais.kmp.domain.casodeuso.NovoAnimal
import com.jakatech.rebanhomais.kmp.domain.casodeuso.NovoProtocolo
import com.jakatech.rebanhomais.kmp.domain.casodeuso.ObterCategoria
import com.jakatech.rebanhomais.kmp.domain.casodeuso.ObterResumoDePeso
import com.jakatech.rebanhomais.kmp.domain.casodeuso.RegistrarPesagem
import com.jakatech.rebanhomais.kmp.domain.casodeuso.RegistrarSaida
import com.jakatech.rebanhomais.kmp.domain.casodeuso.RegistrarVacinacao
import com.jakatech.rebanhomais.kmp.domain.comum.ErroDominio
import com.jakatech.rebanhomais.kmp.domain.comum.MotivoGenitorInvalido
import com.jakatech.rebanhomais.kmp.domain.fakes.AnimaisEmMemoria
import com.jakatech.rebanhomais.kmp.domain.fakes.HOJE
import com.jakatech.rebanhomais.kmp.domain.fakes.IdsSequenciais
import com.jakatech.rebanhomais.kmp.domain.fakes.MovimentacoesEmMemoria
import com.jakatech.rebanhomais.kmp.domain.fakes.PesagensEmMemoria
import com.jakatech.rebanhomais.kmp.domain.fakes.RelogioFixo
import com.jakatech.rebanhomais.kmp.domain.fakes.VacinasEmMemoria
import com.jakatech.rebanhomais.kmp.domain.fakes.falhaCom
import com.jakatech.rebanhomais.kmp.domain.fakes.sucesso
import com.jakatech.rebanhomais.kmp.domain.model.Animal
import com.jakatech.rebanhomais.kmp.domain.model.Origem
import com.jakatech.rebanhomais.kmp.domain.model.Sexo
import com.jakatech.rebanhomais.kmp.domain.model.StatusAnimal
import com.jakatech.rebanhomais.kmp.domain.model.TipoMovimentacao
import com.jakatech.rebanhomais.kmp.domain.regra.CategoriaAnimal
import com.jakatech.rebanhomais.kmp.domain.regra.MotivoPendencia
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CasosDeUsoTest {
    private val ids = IdsSequenciais()
    private val relogio = RelogioFixo()
    private val animais = AnimaisEmMemoria()
    private val movimentacoes = MovimentacoesEmMemoria()
    private val pesagens = PesagensEmMemoria()
    private val vacinas = VacinasEmMemoria()

    private val cadastrarAnimal = CadastrarAnimal(animais, movimentacoes, ids, relogio)
    private val registrarPesagem = RegistrarPesagem(animais, pesagens, ids, relogio)
    private val registrarSaida = RegistrarSaida(animais, movimentacoes, ids, relogio)
    private val cadastrarProtocolo = CadastrarProtocolo(vacinas, ids)
    private val registrarVacinacao = RegistrarVacinacao(animais, vacinas, ids, relogio)

    private fun nascido(brinco: String, sexo: Sexo, nascimento: LocalDate = LocalDate(2026, 3, 10), maeId: String? = null) =
        NovoAnimal(fazendaId = "f1", brinco = brinco, sexo = sexo, origem = Origem.NASCIDO_NA_FAZENDA,
            dataNascimento = nascimento, maeId = maeId)

    private suspend fun vaca(): Animal =
        cadastrarAnimal(nascido("V1", Sexo.FEMEA, LocalDate(2021, 5, 1))).sucesso()

    // Animal

    @Test
    fun cadastrar_animal_normaliza_brinco_e_registra_nascimento() = runTest {
        val animal = cadastrarAnimal(nascido(" br 001 ", Sexo.FEMEA)).sucesso()
        assertEquals("BR001", animal.brinco)
        val entrada = movimentacoes.listarPorAnimal(animal.id).single()
        assertEquals(TipoMovimentacao.NASCIMENTO, entrada.tipo)
        assertEquals(LocalDate(2026, 3, 10), entrada.data)
    }

    @Test
    fun animal_comprado_registra_compra_na_data_de_entrada() = runTest {
        val novo = NovoAnimal(fazendaId = "f1", brinco = "C1", sexo = Sexo.MACHO, origem = Origem.COMPRADO,
            dataEntrada = LocalDate(2026, 10, 1), procedencia = "Fazenda Vizinha")
        val animal = cadastrarAnimal(novo).sucesso()
        val entrada = movimentacoes.listarPorAnimal(animal.id).single()
        assertEquals(TipoMovimentacao.COMPRA, entrada.tipo)
        assertEquals("Fazenda Vizinha", entrada.contraparte)
    }

    @Test
    fun brinco_duplicado_na_mesma_fazenda_falha() = runTest {
        cadastrarAnimal(nascido("A1", Sexo.FEMEA)).sucesso()
        val erro = cadastrarAnimal(nascido("a1", Sexo.MACHO)).falhaCom<ErroDominio.BrincoDuplicado>()
        assertEquals("A1", erro.brinco)
    }

    @Test
    fun mesmo_brinco_em_outra_fazenda_e_permitido() = runTest {
        cadastrarAnimal(nascido("A1", Sexo.FEMEA)).sucesso()
        cadastrarAnimal(nascido("A1", Sexo.FEMEA).copy(fazendaId = "f2")).sucesso()
    }

    @Test
    fun nascimento_no_futuro_falha() = runTest {
        cadastrarAnimal(nascido("A1", Sexo.FEMEA, LocalDate(2027, 1, 1))).falhaCom<ErroDominio.DataNoFuturo>()
    }

    @Test
    fun nascido_na_fazenda_exige_data_de_nascimento() = runTest {
        cadastrarAnimal(nascido("A1", Sexo.FEMEA).copy(dataNascimento = null))
            .falhaCom<ErroDominio.DataNascimentoObrigatoria>()
    }

    @Test
    fun comprado_exige_data_de_entrada() = runTest {
        cadastrarAnimal(NovoAnimal(fazendaId = "f1", brinco = "C1", sexo = Sexo.MACHO, origem = Origem.COMPRADO))
            .falhaCom<ErroDominio.DataEntradaObrigatoria>()
    }

    @Test
    fun mae_precisa_ser_femea() = runTest {
        val touro = cadastrarAnimal(nascido("T1", Sexo.MACHO, LocalDate(2020, 1, 1))).sucesso()
        val erro = cadastrarAnimal(nascido("B1", Sexo.FEMEA, maeId = touro.id)).falhaCom<ErroDominio.GenitorInvalido>()
        assertEquals(MotivoGenitorInvalido.MAE_NAO_E_FEMEA, erro.motivo)
    }

    @Test
    fun mae_nao_pode_ser_mais_nova_que_o_filho() = runTest {
        val novilha = cadastrarAnimal(nascido("N1", Sexo.FEMEA, LocalDate(2026, 5, 1))).sucesso()
        val erro = cadastrarAnimal(nascido("B1", Sexo.FEMEA, LocalDate(2026, 3, 1), maeId = novilha.id))
            .falhaCom<ErroDominio.GenitorInvalido>()
        assertEquals(MotivoGenitorInvalido.NASCEU_DEPOIS_DO_FILHO, erro.motivo)
    }

    @Test
    fun femea_vira_vaca_depois_do_primeiro_filho() = runTest {
        val obterCategoria = ObterCategoria(animais, relogio)
        val mae = vaca()
        assertEquals(CategoriaAnimal.NOVILHA, obterCategoria(mae))
        cadastrarAnimal(nascido("B1", Sexo.MACHO, maeId = mae.id)).sucesso()
        assertEquals(CategoriaAnimal.VACA, obterCategoria(mae))
    }

    // Saída

    @Test
    fun venda_muda_status_e_impede_novos_manejos() = runTest {
        val animal = vaca()
        registrarSaida(NovaSaida(animal.id, TipoMovimentacao.VENDA, HOJE, contraparte = "Frigorífico", valor = 4200.0)).sucesso()

        assertEquals(StatusAnimal.VENDIDO, animais.buscarPorId(animal.id)!!.status)
        registrarSaida(NovaSaida(animal.id, TipoMovimentacao.MORTE, HOJE)).falhaCom<ErroDominio.AnimalInativo>()
        registrarPesagem(NovaPesagem(animal.id, HOJE, 400.0)).falhaCom<ErroDominio.AnimalInativo>()
    }

    @Test
    fun compra_nao_e_tipo_de_saida() = runTest {
        val animal = vaca()
        registrarSaida(NovaSaida(animal.id, TipoMovimentacao.COMPRA, HOJE)).falhaCom<ErroDominio.TipoDeSaidaInvalido>()
    }

    // Pesagem

    @Test
    fun pesagens_geram_resumo_com_ganho_medio() = runTest {
        val animal = cadastrarAnimal(nascido("G1", Sexo.MACHO, LocalDate(2025, 6, 1))).sucesso()
        registrarPesagem(NovaPesagem(animal.id, LocalDate(2026, 10, 1), 300.0)).sucesso()
        registrarPesagem(NovaPesagem(animal.id, LocalDate(2026, 10, 31), 330.0)).sucesso()

        val resumo = ObterResumoDePeso(pesagens)(animal.id)
        assertEquals(330.0, resumo.ultimaPesagem!!.pesoKg)
        assertEquals(1.0, resumo.ganhoMedioDiario!!, 0.0001)
        assertEquals(2, resumo.totalDePesagens)
    }

    @Test
    fun pesagem_de_animal_inexistente_falha() = runTest {
        registrarPesagem(NovaPesagem("nao-existe", HOJE, 300.0)).falhaCom<ErroDominio.AnimalNaoEncontrado>()
    }

    @Test
    fun pesagem_antes_do_nascimento_falha() = runTest {
        val animal = cadastrarAnimal(nascido("G1", Sexo.MACHO, LocalDate(2026, 3, 10))).sucesso()
        registrarPesagem(NovaPesagem(animal.id, LocalDate(2026, 3, 1), 30.0)).falhaCom<ErroDominio.DataAntesDoNascimento>()
    }

    // Vacina

    @Test
    fun vacinacao_deduz_a_dose_e_calcula_a_proxima() = runTest {
        val animal = vaca()
        val protocolo = cadastrarProtocolo(
            NovoProtocolo(fazendaId = "f1", nome = "Clostridiose", doses = 2, intervaloEntreDosesDias = 30, reforcoDias = 365)
        ).sucesso()

        val primeira = registrarVacinacao(NovaVacinacao(animal.id, LocalDate(2026, 10, 1), protocoloId = protocolo.id)).sucesso()
        assertEquals(1, primeira.aplicacao.dose)
        assertEquals(LocalDate(2026, 10, 31), primeira.proxima!!.data)

        val segunda = registrarVacinacao(NovaVacinacao(animal.id, LocalDate(2026, 10, 31), protocoloId = protocolo.id)).sucesso()
        assertEquals(2, segunda.aplicacao.dose)
        assertEquals(MotivoPendencia.REFORCO, segunda.proxima!!.motivo)
        assertEquals(LocalDate(2027, 10, 31), segunda.proxima.data)
    }

    @Test
    fun vacina_avulsa_exige_nome_e_nao_tem_proxima() = runTest {
        val animal = vaca()
        registrarVacinacao(NovaVacinacao(animal.id, HOJE)).falhaCom<ErroDominio.NomeObrigatorio>()
        val avulsa = registrarVacinacao(NovaVacinacao(animal.id, HOJE, nomeVacina = "Vermífugo")).sucesso()
        assertNull(avulsa.proxima)
    }

    @Test
    fun dose_acima_do_protocolo_falha() = runTest {
        val animal = vaca()
        val protocolo = cadastrarProtocolo(NovoProtocolo(fazendaId = "f1", nome = "Dose única")).sucesso()
        registrarVacinacao(NovaVacinacao(animal.id, HOJE, protocoloId = protocolo.id, dose = 2))
            .falhaCom<ErroDominio.DoseInvalida>()
    }

    @Test
    fun protocolo_inexistente_falha() = runTest {
        val animal = vaca()
        registrarVacinacao(NovaVacinacao(animal.id, HOJE, protocoloId = "nao-existe"))
            .falhaCom<ErroDominio.ProtocoloNaoEncontrado>()
    }

    @Test
    fun pendencias_listam_segunda_dose_vencendo() = runTest {
        val animal = vaca()
        val protocolo = cadastrarProtocolo(
            NovoProtocolo(fazendaId = "f1", nome = "Clostridiose", doses = 2, intervaloEntreDosesDias = 30)
        ).sucesso()
        registrarVacinacao(NovaVacinacao(animal.id, LocalDate(2026, 10, 28), protocoloId = protocolo.id)).sucesso()

        val pendencias = ListarPendenciasVacinais(animais, vacinas, relogio)("f1", horizonteDias = 7)
        val pendencia = pendencias.single()
        assertEquals("V1", pendencia.brinco)
        assertEquals(LocalDate(2026, 11, 27), pendencia.dataPrevista)
        assertTrue(!pendencia.atrasada(HOJE))
    }
}
