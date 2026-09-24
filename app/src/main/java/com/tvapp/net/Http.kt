package com.tvapp.net

import com.tvapp.BuildConfig
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object Http {
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS)
            .followRedirects(true).followSslRedirects(true)
            .addInterceptor { chain ->
                val req = chain.request()
                chain.proceed(if (req.header("User-Agent") == null) req.newBuilder().header("User-Agent", BuildConfig.USER_AGENT).build() else req)
            }
            .build()
    }
    fun client(): OkHttpClient = client
}
