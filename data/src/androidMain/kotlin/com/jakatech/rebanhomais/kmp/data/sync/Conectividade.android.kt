package com.jakatech.rebanhomais.kmp.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.jakatech.rebanhomais.kmp.domain.repositorio.Conectividade
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

/** Rede padrão do Android (ConnectivityManager). Precisa de ACCESS_NETWORK_STATE, já no manifesto do data. */
internal class ConectividadeAndroid(context: Context) : Conectividade {
    private val gerenciador = context.getSystemService(ConnectivityManager::class.java)

    private fun temInternet(rede: Network?): Boolean =
        gerenciador.getNetworkCapabilities(rede)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

    override val online: Flow<Boolean> = callbackFlow {
        val retorno = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }

            override fun onLost(network: Network) {
                trySend(temInternet(gerenciador.activeNetwork))
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySend(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
            }
        }
        trySend(temInternet(gerenciador.activeNetwork))
        gerenciador.registerDefaultNetworkCallback(retorno)
        awaitClose { gerenciador.unregisterNetworkCallback(retorno) }
    }.distinctUntilChanged()
}

internal actual val moduloDeConectividade: Module = module {
    single<Conectividade> { ConectividadeAndroid(androidContext()) }
}
