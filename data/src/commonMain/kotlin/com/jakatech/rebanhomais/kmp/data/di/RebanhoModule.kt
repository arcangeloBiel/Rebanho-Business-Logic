package com.jakatech.rebanhomais.kmp.data.di

import com.jakatech.rebanhomais.kmp.data.auth.SupabaseAuthRepository
import com.jakatech.rebanhomais.kmp.data.criarClienteSupabase
import com.jakatech.rebanhomais.kmp.data.fazendas.SupabaseMinhasFazendasRepository
import com.jakatech.rebanhomais.kmp.data.local.RebanhoDatabase
import com.jakatech.rebanhomais.kmp.data.local.moduloDoBancoLocal
import com.jakatech.rebanhomais.kmp.data.preferencias.SettingsPreferenciasRepository
import com.jakatech.rebanhomais.kmp.domain.casodeuso.CadastrarAnimal
import com.jakatech.rebanhomais.kmp.domain.casodeuso.CadastrarFazenda
import com.jakatech.rebanhomais.kmp.domain.casodeuso.CadastrarProtocolo
import com.jakatech.rebanhomais.kmp.domain.casodeuso.ConfirmarConta
import com.jakatech.rebanhomais.kmp.domain.casodeuso.CriarConta
import com.jakatech.rebanhomais.kmp.domain.casodeuso.Entrar
import com.jakatech.rebanhomais.kmp.domain.casodeuso.ListarMinhasFazendas
import com.jakatech.rebanhomais.kmp.domain.casodeuso.ListarPendenciasVacinais
import com.jakatech.rebanhomais.kmp.domain.casodeuso.ObservarSessao
import com.jakatech.rebanhomais.kmp.domain.casodeuso.ObterCategoria
import com.jakatech.rebanhomais.kmp.domain.casodeuso.ObterResumoDePeso
import com.jakatech.rebanhomais.kmp.domain.casodeuso.PedirRecuperacaoDeSenha
import com.jakatech.rebanhomais.kmp.domain.casodeuso.RedefinirSenha
import com.jakatech.rebanhomais.kmp.domain.casodeuso.ReenviarCodigo
import com.jakatech.rebanhomais.kmp.domain.casodeuso.RegistrarPesagem
import com.jakatech.rebanhomais.kmp.domain.casodeuso.RegistrarSaida
import com.jakatech.rebanhomais.kmp.domain.casodeuso.RegistrarVacinacao
import com.jakatech.rebanhomais.kmp.domain.casodeuso.Sair
import com.jakatech.rebanhomais.kmp.domain.casodeuso.SelecionarFazenda
import com.jakatech.rebanhomais.kmp.domain.comum.GeradorDeId
import com.jakatech.rebanhomais.kmp.domain.comum.GeradorDeUuid
import com.jakatech.rebanhomais.kmp.domain.comum.Relogio
import com.jakatech.rebanhomais.kmp.domain.comum.RelogioDoSistema
import com.jakatech.rebanhomais.kmp.domain.repositorio.AuthRepository
import com.jakatech.rebanhomais.kmp.domain.repositorio.MinhasFazendasRepository
import com.jakatech.rebanhomais.kmp.domain.repositorio.PreferenciasRepository
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
 * Conta, sessão e minhas fazendas já funcionam (Fase 2). Os repositórios do rebanho (banco local)
 * entram na Fase 3; até lá, os casos de uso do rebanho estão declarados mas não podem ser criados.
 */
val rebanhoModule: Module = module {
    includes(moduloDoBancoLocal)

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

    // Conta e sessão (Fase 2)
    factory { CriarConta(get()) }
    factory { ConfirmarConta(get()) }
    factory { ReenviarCodigo(get()) }
    factory { Entrar(get()) }
    factory { PedirRecuperacaoDeSenha(get()) }
    factory { RedefinirSenha(get()) }
    factory { Sair(get(), get(), get()) }
    factory { ObservarSessao(get(), get(), get()) }

    // Minhas fazendas (Fase 2)
    factory { CadastrarFazenda(get(), get(), get()) }
    factory { ListarMinhasFazendas(get()) }
    factory { SelecionarFazenda(get(), get()) }

    // Rebanho (Fase 1; repositórios na Fase 3)
    factory { CadastrarAnimal(get(), get(), get(), get()) }
    factory { ObterCategoria(get(), get()) }
    factory { RegistrarSaida(get(), get(), get(), get()) }
    factory { RegistrarPesagem(get(), get(), get(), get()) }
    factory { ObterResumoDePeso(get()) }
    factory { CadastrarProtocolo(get(), get()) }
    factory { RegistrarVacinacao(get(), get(), get(), get()) }
    factory { ListarPendenciasVacinais(get(), get(), get()) }
}
