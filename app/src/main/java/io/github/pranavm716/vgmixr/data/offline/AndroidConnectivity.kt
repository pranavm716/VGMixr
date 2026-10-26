package io.github.pranavm716.vgmixr.data.offline

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.pranavm716.vgmixr.domain.offline.Connectivity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Online while the default network has validated internet access. */
@Singleton
class AndroidConnectivity @Inject constructor(@ApplicationContext context: Context) : Connectivity {
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private val state =
        MutableStateFlow(hasInternet(manager.getNetworkCapabilities(manager.activeNetwork)))
    override val online: StateFlow<Boolean> = state

    init {
        manager.registerDefaultNetworkCallback(
            object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    state.value = hasInternet(caps)
                }

                override fun onLost(network: Network) {
                    state.value = false
                }
            }
        )
    }

    private fun hasInternet(caps: NetworkCapabilities?) = caps != null &&
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
