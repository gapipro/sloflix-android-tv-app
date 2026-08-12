package com.sloflix.tv.data.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

fun interface NetworkStatus {
    fun isOnline(): Boolean

    companion object {
        val AlwaysOnline = NetworkStatus { true }
    }
}

class AndroidNetworkStatus(context: Context) : NetworkStatus {
    private val connectivityManager: ConnectivityManager? =
        context.getSystemService(ConnectivityManager::class.java)

    override fun isOnline(): Boolean {
        val manager = connectivityManager ?: return true
        val capabilities = manager.activeNetwork?.let(manager::getNetworkCapabilities) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
