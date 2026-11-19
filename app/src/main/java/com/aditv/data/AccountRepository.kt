package com.aditv.data

import com.aditv.core.Endpoint
import com.aditv.core.Redactor
import com.aditv.data.db.AccountEntity
import com.aditv.data.db.AditvDatabase
import com.aditv.data.prefs.AppPrefs
import com.aditv.data.provider.AccountStatus
import com.aditv.data.provider.DemoAdapter
import com.aditv.data.provider.ProviderAdapter
import com.aditv.data.provider.ProviderCredentials
import com.aditv.data.provider.ProviderError
import com.aditv.data.provider.ProviderKind
import com.aditv.data.provider.XtreamAdapter
import com.aditv.net.CleartextGate
import com.aditv.net.HttpClients
import com.aditv.security.ProviderSecret
import com.aditv.security.SecretStore
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

sealed interface Session {
    data object Loading : Session
    data class SignedOut(val notice: String? = null) : Session
    data class Active(val account: AccountEntity, val adapter: ProviderAdapter) : Session {
        val isDemo get() = adapter.kind == ProviderKind.DEMO
    }
}

/** Un seul compte actif dans le MVP. Changer de compte efface les données locales de l'ancien. */
class AccountRepository(
    private val db: AditvDatabase,
    private val secrets: SecretStore,
    private val prefs: AppPrefs,
    private val http: HttpClients,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _session = MutableStateFlow<Session>(Session.Loading)
    val session: StateFlow<Session> = _session

    suspend fun restore() {
        CleartextGate.setAllowedHosts(prefs.cleartextHostsNow())
        val id = prefs.activeAccountIdNow() ?: return run { _session.value = Session.SignedOut() }
        val account = db.accounts().get(id) ?: return run { _session.value = Session.SignedOut() }
        val adapter = when (ProviderKind.valueOf(account.kind)) {
            ProviderKind.DEMO -> DemoAdapter()
            ProviderKind.XTREAM -> {
                val s = account.secretRef?.let(secrets::get)
                    ?: return run { _session.value = Session.SignedOut("Vos accès enregistrés ne sont plus lisibles. Reconnectez-vous ; le catalogue local sera réimporté.") }
                Redactor.registerSecret(s.username); Redactor.registerSecret(s.password)
                XtreamAdapter(http.providerApi, ProviderCredentials(account.endpoint.toHttpUrl(), s.username, s.password)).apply {
                    val formats = account.liveFormats?.split(',')
                    if (formats != null && "ts" !in formats && "m3u8" in formats) liveExtension = "m3u8"
                }
            }
        }
        _session.value = Session.Active(account, adapter)
    }

    /** Teste les accès sans rien enregistrer. Le consentement HTTP doit avoir été donné avant l'appel. */
    suspend fun test(endpoint: HttpUrl, username: String, password: String, cleartextConsent: Boolean): AccountStatus {
        Redactor.registerSecret(username); Redactor.registerSecret(password)
        if (!endpoint.isHttps) {
            if (!cleartextConsent) throw ProviderError.CleartextNotAllowed(endpoint.host)
            CleartextGate.setAllowedHosts(CleartextGate.allowedHosts() + endpoint.host)
        }
        return XtreamAdapter(http.providerApi, ProviderCredentials(endpoint, username, password)).authenticate()
    }

    suspend fun saveXtream(endpoint: HttpUrl, username: String, password: String, cleartextConsent: Boolean, status: AccountStatus) {
        clearCurrent()
        val id = UUID.randomUUID().toString()
        val ref = "provider:$id"
        secrets.put(ref, ProviderSecret(username, password))
        val hosts = if (!endpoint.isHttps && cleartextConsent) setOf(endpoint.host) else emptySet()
        prefs.setCleartextHosts(hosts)
        CleartextGate.setAllowedHosts(hosts)
        db.accounts().upsert(account(id, ProviderKind.XTREAM, Endpoint.display(endpoint), ref, cleartextConsent, status).copy(liveFormats = status.liveFormats?.joinToString(",")))
        prefs.setActiveAccount(id)
        restore()
    }

    suspend fun startDemo() {
        clearCurrent()
        val id = UUID.randomUUID().toString()
        db.accounts().upsert(account(id, ProviderKind.DEMO, "demo://aditv", null, false, DemoAdapter().authenticate()))
        prefs.setActiveAccount(id)
        restore()
    }

    suspend fun refreshStatus(): AccountStatus? {
        val s = _session.value as? Session.Active ?: return null
        val status = s.adapter.authenticate()
        val updated = s.account.copy(
            status = status.status, expiresAtEpochSec = status.expiresAtEpochSec, maxConnections = status.maxConnections,
            activeConnections = status.activeConnections, isTrial = status.isTrial, serverTimezone = status.serverTimezone,
            statusCheckedAt = clock(),
        )
        db.accounts().upsert(updated)
        _session.value = s.copy(account = updated)
        return status
    }

    suspend fun signOut() {
        clearCurrent()
        _session.value = Session.SignedOut()
    }

    /** Efface compte, catalogue local, secrets et autorisations HTTP du compte courant. */
    private suspend fun clearCurrent() {
        prefs.activeAccountIdNow()?.let { id ->
            db.catalog().deleteItems(id); db.catalog().deleteCategories(id); db.catalog().deleteSyncStates(id)
            db.library().deleteEpisodes(id); db.library().deleteProgress(id); db.library().deleteWatchlist(id)
            db.metadata().deleteMatches(id)
            db.live().clearAccount(id)
            db.accounts().get(id)?.secretRef?.let(secrets::remove)
            db.accounts().delete(id)
        }
        prefs.setActiveAccount(null)
        prefs.setCleartextHosts(emptySet())
        CleartextGate.setAllowedHosts(emptySet())
        Redactor.clearSecrets()
    }

    private fun account(id: String, kind: ProviderKind, endpoint: String, ref: String?, consent: Boolean, s: AccountStatus) = AccountEntity(
        id = id, kind = kind.name, endpoint = endpoint, secretRef = ref, cleartextConsent = consent, createdAt = clock(),
        status = s.status, expiresAtEpochSec = s.expiresAtEpochSec, maxConnections = s.maxConnections,
        activeConnections = s.activeConnections, isTrial = s.isTrial, serverTimezone = s.serverTimezone, statusCheckedAt = clock(),
    )
}
