package com.aditv.net

import com.aditv.BuildConfig
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

/** Clients HTTP partagés : un pool de connexions, des délais distincts connexion / lecture. */
class HttpClients {
    private val base: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", "ADITV/${BuildConfig.VERSION_NAME}").build())
        }
        .build()

    /** API fournisseur : identifiants dans l'URL, donc aucune redirection vers un autre hôte. */
    val providerApi: OkHttpClient = base.newBuilder()
        .readTimeout(90, TimeUnit.SECONDS) // listes VOD volumineuses
        .followRedirects(false)
        .followSslRedirects(false)
        .addInterceptor(SameHostRedirects())
        .addNetworkInterceptor(CleartextGate(blockCrossHostRedirects = true))
        .build()

    /** Images (logos, affiches) : pas d'identifiants, redirections permises mais HTTP toujours filtré. */
    val images: OkHttpClient = base.newBuilder()
        .addNetworkInterceptor(CleartextGate(blockCrossHostRedirects = false))
        .build()
}
