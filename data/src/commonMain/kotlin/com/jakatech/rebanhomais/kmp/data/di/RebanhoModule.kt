package com.jakatech.rebanhomais.kmp.data.di

import com.jakatech.rebanhomais.kmp.data.auth.SupabaseAuthRepository
import com.jakatech.rebanhomais.kmp.data.criarClienteSupabase
import com.jakatech.rebanhomais.kmp.domain.casodeuso.CadastrarAnimal
import com.jakatech.rebanhomais.kmp.domain.casodeuso.CadastrarFazenda
import com.jakatech.rebanhomais.kmp.domain.casodeuso.CadastrarProtocolo
import com.jakatech.rebanhomais.kmp.domain.casodeuso.ConfirmarConta
import com.jakatech.rebanhomais.kmp.domain.casodeuso.CriarConta
import com.jakatech.rebanhomais.kmp.domain.casodeuso.Entrar
import com.jakatech.rebanhomais.kmp.domain.casodeuso.ListarPendenciasVacinais
import com.jakatech.rebanhomais.kmp.domain.casodeuso.ObservarSessao
import com.jakatech.rebanhomais.kmp.domain.casodeuso.ObterCategoria
import com.jakatech.rebanhomais.kmp.domain.casodeuso.ObterResumoDePeso
import com.jakatech.rebanhomais.kmp.domain.casodeuso.ReenviarCodigo
import com.jakatech.rebanhomais.kmp.domain.casodeuso.RegistrarPesagem
import com.jakatech.rebanhomais.kmp.domain.casodeuso.RegistrarSaida
import com.jakatech.rebanhomais.kmp.domain.casodeuso.RegistrarVacinacao
import com.jakatech.rebanhomais.kmp.domain.casodeuso.Sair
import com.jakatech.rebanhomais.kmp.domain.comum.GeradorDeId
import com.jakatech.rebanhomais.kmp.domain.comum.GeradorDeUuid
import com.jakatech.rebanhomais.kmp.domain.comum.Relogio
import com.jakatech.rebanhomais.kmp.domain.comum.RelogioDoSistema
import com.jakatech.rebanhomais.kmp.domain.repositorio.AuthRepository
import io.github.jan.supabase.SupabaseClient
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Ponto único de injeção de dependência da camada KMP.
 *
 * A UI (Android e iOS) só precisa carregar este módulo:
 *
 * ```
 * startKoin { modules(rebanhoModule) }
 * ```
 *
 * Conta e sessão já funcionam (Fase 2). Os repositórios do rebanho (banco local)
 * entram na Fase 3; até lá, os casos de uso do rebanho estão declarados mas não podem ser criados.
 */
val rebanhoModule: Module = module {
    single<Relogio> { RelogioDoSistema }
    single<GeradorDeId> { GeradorDeUuid }

    // Supabase
    single<SupabaseClient> { criarClienteSupabase() }
    single<AuthRepository> { SupabaseAuthRepository(get()) }

    // Conta e sessão (Fase 2)
    factory { CriarConta(get()) }
    factory { ConfirmarConta(get()) }
    factory { ReenviarCodigo(get()) }
    factory { Entrar(get()) }
    factory { Sair(get()) }
    factory { ObservarSessao(get()) }

    // Rebanho (Fase 1; repositórios na Fase 3)
    factory { CadastrarFazenda(get(), get()) }
    factory { CadastrarAnimal(get(), get(), get(), get()) }
    factory { ObterCategoria(get(), get()) }
    factory { RegistrarSaida(get(), get(), get(), get()) }
    factory { RegistrarPesagem(get(), get(), get(), get()) }
    factory { ObterResumoDePeso(get()) }
    factory { CadastrarProtocolo(get(), get()) }
    factory { RegistrarVacinacao(get(), get(), get(), get()) }
    factory { ListarPendenciasVacinais(get(), get(), get()) }
}
