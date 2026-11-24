package com.jakatech.rebanhomais.kmp.data.di

import com.jakatech.rebanhomais.kmp.data.auth.SupabaseAuthRepository
import com.jakatech.rebanhomais.kmp.data.criarClienteSupabase
import com.jakatech.rebanhomais.kmp.data.fazendas.SupabaseMinhasFazendasRepository
import com.jakatech.rebanhomais.kmp.data.local.AnimaisLocais
import com.jakatech.rebanhomais.kmp.data.local.LotesLocais
import com.jakatech.rebanhomais.kmp.data.local.MovimentacoesLocais
import com.jakatech.rebanhomais.kmp.data.local.PesagensLocais
import com.jakatech.rebanhomais.kmp.data.local.RebanhoDatabase
import com.jakatech.rebanhomais.kmp.data.local.VacinasLocais
import com.jakatech.rebanhomais.kmp.data.local.moduloDoBancoLocal
import com.jakatech.rebanhomais.kmp.data.preferencias.SettingsPreferenciasRepository
import com.jakatech.rebanhomais.kmp.data.sync.ServidorDoRebanho
import com.jakatech.rebanhomais.kmp.data.sync.ServidorSupabase
import com.jakatech.rebanhomais.kmp.data.sync.SincronizacaoLocal
import com.jakatech.rebanhomais.kmp.data.sync.moduloDeConectividade
import com.jakatech.rebanhomais.kmp.domain.casodeuso.CadastrarAnimal
import com.jakatech.rebanhomais.kmp.domain.casodeuso.CadastrarFazenda
import com.jakatech.rebanhomais.kmp.domain.casodeuso.CadastrarLote
import com.jakatech.rebanhomais.kmp.domain.casodeuso.CadastrarProtocolo
import com.jakatech.rebanhomais.kmp.domain.casodeuso.ConfirmarConta
import com.jakatech.rebanhomais.kmp.domain.casodeuso.CorrigirBrinco
import com.jakatech.rebanhomais.kmp.domain.casodeuso.CriarConta
import com.jakatech.rebanhomais.kmp.domain.casodeuso.EditarAnimal
import com.jakatech.rebanhomais.kmp.domain.casodeuso.ExcluirAnimal
import com.jakatech.rebanhomais.kmp.domain.casodeuso.Entrar
import com.jakatech.rebanhomais.kmp.domain.casodeuso.ListarMinhasFazendas
import com.jakatech.rebanhomais.kmp.domain.casodeuso.ListarPendenciasVacinais
import com.jakatech.rebanhomais.kmp.domain.casodeuso.ManterSincronizado
import com.jakatech.rebanhomais.kmp.domain.casodeuso.ObservarHistoricoDoAnimal
import com.jakatech.rebanhomais.kmp.domain.casodeuso.ObservarLotes
import com.jakatech.rebanhomais.kmp.domain.casodeuso.ObservarPendenciasVacinais
import com.jakatech.rebanhomais.kmp.domain.casodeuso.ObservarEstadoDaSincronizacao
import com.jakatech.rebanhomais.kmp.domain.casodeuso.ObservarRebanho
import com.jakatech.rebanhomais.kmp.domain.casodeuso.ObservarRegistrosComErro
import com.jakatech.rebanhomais.kmp.domain.casodeuso.ObservarResumoDePeso
import com.jakatech.rebanhomais.kmp.domain.casodeuso.ObservarSessao
import com.jakatech.rebanhomais.kmp.domain.casodeuso.ObterCategoria
import com.jakatech.rebanhomais.kmp.domain.casodeuso.ObterResumoDePeso
import com.jakatech.rebanhomais.kmp.domain.casodeuso.PedirRecuperacaoDeSenha
import com.jakatech.rebanhomais.kmp.domain.casodeuso.RedefinirSenha
import com.jakatech.rebanhomais.kmp.domain.casodeuso.ReenviarCodigo
import com.jakatech.rebanhomais.kmp.domain.casodeuso.RegistrarPesagem
import com.jakatech.rebanhomais.kmp.domain.casodeuso.RegistrarSaida
import com.jakatech.rebanhomais.kmp.domain.casodeuso.RegistrarVacinacao
import com.jakatech.rebanhomais.kmp.domain.casodeuso.RenomearLote
import com.jakatech.rebanhomais.kmp.domain.casodeuso.Sair
import com.jakatech.rebanhomais.kmp.domain.casodeuso.SincronizarAgora
import com.jakatech.rebanhomais.kmp.domain.casodeuso.SelecionarFazenda
import com.jakatech.rebanhomais.kmp.domain.comum.GeradorDeId
import com.jakatech.rebanhomais.kmp.domain.comum.GeradorDeUuid
import com.jakatech.rebanhomais.kmp.domain.comum.Relogio
import com.jakatech.rebanhomais.kmp.domain.comum.RelogioDoSistema
import com.jakatech.rebanhomais.kmp.domain.repositorio.AnimalRepository
import com.jakatech.rebanhomais.kmp.domain.repositorio.AuthRepository
import com.jakatech.rebanhomais.kmp.domain.repositorio.LoteRepository
import com.jakatech.rebanhomais.kmp.domain.repositorio.MinhasFazendasRepository
import com.jakatech.rebanhomais.kmp.domain.repositorio.MovimentacaoRepository
import com.jakatech.rebanhomais.kmp.domain.repositorio.PesagemRepository
import com.jakatech.rebanhomais.kmp.domain.repositorio.PreferenciasRepository
import com.jakatech.rebanhomais.kmp.domain.repositorio.SincronizacaoRepository
import com.jakatech.rebanhomais.kmp.domain.repositorio.VacinaRepository
import com.russhwolf.settings.Settings
import io.github.jan.supabase.SupabaseClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Ponto único de injeção de dependência da camada KMP.
 *
 * A UI (Android e iOS) só precisa carregar este módulo:
 *
 * ```
 * startKoin { modules(rebanhoModule) }                          // iOS
 * startKoin { androidContext(this@App); modules(rebanhoModule) }  // Android
 * ```
 *
 * Conta, sessão e minhas fazendas já funcionam (Fase 2). Lotes, animais, movimentações, pesagens,
 * protocolos e aplicações de vacina gravam no banco local (Fase 3).
 */
val rebanhoModule: Module = module {
    includes(moduloDoBancoLocal, moduloDeConectividade)

    single<Relogio> { RelogioDoSistema }
    single<GeradorDeId> { GeradorDeUuid }

    // Infraestrutura
    single<CoroutineScope> { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    single<Settings> { Settings() }
    single<SupabaseClient> { criarClienteSupabase() }
    single { RebanhoDatabase(get()) }

    // Repositórios
    single<AuthRepository> { SupabaseAuthRepository(get()) }
    single<MinhasFazendasRepository> { SupabaseMinhasFazendasRepository(get(), get(), get()) }
    single<PreferenciasRepository> { SettingsPreferenciasRepository(get()) }
    single<LoteRepository> { LotesLocais(get()) }
    single<AnimalRepository> { AnimaisLocais(get()) }
    single<MovimentacaoRepository> { MovimentacoesLocais(get()) }
    single<PesagemRepository> { PesagensLocais(get()) }
    single<VacinaRepository> { VacinasLocais(get()) }
    single<ServidorDoRebanho> { ServidorSupabase(get()) }
    single<SincronizacaoRepository> { SincronizacaoLocal(get(), get(), get(), get()) }

    // Conta e sessão (Fase 2)
    factory { CriarConta(get()) }
    factory { ConfirmarConta(get()) }
    factory { ReenviarCodigo(get()) }
    factory { Entrar(get()) }
    factory { PedirRecuperacaoDeSenha(get()) }
    factory { RedefinirSenha(get()) }
    factory { Sair(get(), get(), get(), get()) }
    factory { ObservarSessao(get(), get(), get()) }

    // Minhas fazendas (Fase 2)
    factory { CadastrarFazenda(get(), get(), get()) }
    factory { ListarMinhasFazendas(get()) }
    factory { SelecionarFazenda(get(), get()) }

    // Rebanho: animais e saídas (banco local, Fase 3 fatia 2)
    factory { CadastrarAnimal(get(), get(), get(), get(), get()) }
    factory { ObterCategoria(get(), get()) }
    factory { RegistrarSaida(get(), get(), get(), get()) }
    factory { ObservarRebanho(get(), get(), get()) }
    factory { EditarAnimal(get(), get()) }
    factory { CorrigirBrinco(get()) }
    factory { ExcluirAnimal(get(), get()) }
    factory { ObservarHistoricoDoAnimal(get(), get(), get()) }

    // Lotes (banco local, Fase 3 fatia 4)
    factory { CadastrarLote(get(), get()) }
    factory { RenomearLote(get()) }
    factory { ObservarLotes(get(), get(), get()) }

    // Sincronização com o Supabase (Fase 4)
    factory { ObservarEstadoDaSincronizacao(get()) }
    factory { ObservarRegistrosComErro(get()) }
    factory { SincronizarAgora(get()) }
    single { ManterSincronizado(get(), get(), get()) }

    // Pesagens e vacinas (banco local, Fase 3 fatia 3)
    factory { RegistrarPesagem(get(), get(), get(), get()) }
    factory { ObterResumoDePeso(get()) }
    factory { ObservarResumoDePeso(get()) }
    factory { CadastrarProtocolo(get(), get()) }
    factory { RegistrarVacinacao(get(), get(), get(), get()) }
    factory { ListarPendenciasVacinais(get(), get(), get()) }
    factory { ObservarPendenciasVacinais(get(), get(), get(), get(), get()) }
}
