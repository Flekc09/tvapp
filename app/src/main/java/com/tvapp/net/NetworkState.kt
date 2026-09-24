package com.tvapp.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

interface NetworkState {
    fun isValidated(): Boolean
    /** A network that claims internet access; unlike VALIDATED this holds where Google's connectivity check is blocked (DNS filtering). */
    fun hasInternet(): Boolean = isValidated()
}

class AndroidNetworkState(context: Context) : NetworkState {
    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    override fun isValidated(): Boolean {
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
    override fun hasInternet(): Boolean = cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
}

class SwitchableNetworkState(@Volatile var delegate: NetworkState) : NetworkState {
    override fun isValidated() = delegate.isValidated()
    override fun hasInternet() = delegate.hasInternet()
}
