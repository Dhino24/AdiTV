package com.aditv.ui.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aditv.core.Endpoint
import com.aditv.data.provider.AccountStatus
import com.aditv.data.provider.ProviderError
import com.aditv.di.AppContainer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.HttpUrl

sealed interface LoginPhase {
    data object Editing : LoginPhase
    data object Testing : LoginPhase
    data class NeedsCleartextConsent(val host: String) : LoginPhase
    data class Tested(val status: AccountStatus) : LoginPhase
    data class Failed(val message: String, val hint: String? = null) : LoginPhase
    data object Saving : LoginPhase
}

class LoginViewModel(private val c: AppContainer) : ViewModel() {
    var server by mutableStateOf("")
    var username by mutableStateOf("")
    var password by mutableStateOf("")
    var revealPassword by mutableStateOf(false); private set
    var phase by mutableStateOf<LoginPhase>(LoginPhase.Editing); private set

    private var cleartextConsentFor: String? = null
    private var tested: Pair<HttpUrl, AccountStatus>? = null
    private var revealJob: Job? = null

    fun onEdited() {
        tested = null
        if (phase !is LoginPhase.Testing && phase !is LoginPhase.Saving) phase = LoginPhase.Editing
    }

    /** « Afficher » révèle le mot de passe 5 secondes seulement. */
    fun revealMomentarily() {
        revealJob?.cancel()
        revealPassword = true
        revealJob = viewModelScope.launch { delay(5_000); revealPassword = false }
    }

    fun test() {
        if (username.isBlank() || password.isEmpty()) { phase = LoginPhase.Failed("Renseignez l'identifiant et le mot de passe."); return }
        val parsed = Endpoint.canonicalize(server)
        if (parsed is Endpoint.Result.Invalid) { phase = LoginPhase.Failed(parsed.reason); return }
        val ok = parsed as Endpoint.Result.Ok
        if (!ok.url.isHttps && cleartextConsentFor != ok.url.host) { phase = LoginPhase.NeedsCleartextConsent(ok.url.host); return }
        phase = LoginPhase.Testing
        viewModelScope.launch {
            phase = try {
                val status = c.accounts.test(ok.url, username.trim(), password, cleartextConsentFor == ok.url.host)
                tested = ok.url to status
                LoginPhase.Tested(status)
            } catch (e: ProviderError) {
                val hint = if (ok.schemeWasAssumed && (e is ProviderError.Tls || e is ProviderError.Unreachable || e is ProviderError.Timeout))
                    "Aucun schéma saisi : ADI TV a essayé https://. Si votre fournisseur indique http://, saisissez-le explicitement." else null
                LoginPhase.Failed(e.message ?: "Erreur", hint)
            } catch (e: Exception) {
                LoginPhase.Failed("Erreur inattendue (${e.javaClass.simpleName}).")
            }
        }
    }

    fun acceptCleartext() {
        (phase as? LoginPhase.NeedsCleartextConsent)?.let { cleartextConsentFor = it.host; test() }
    }

    fun declineCleartext() { phase = LoginPhase.Editing }

    fun save() {
        val (url, status) = tested ?: return
        phase = LoginPhase.Saving
        viewModelScope.launch {
            try {
                c.accounts.saveXtream(url, username.trim(), password, cleartextConsentFor == url.host, status)
                password = ""
                c.prefs.setCatalogParseVersion(com.aditv.di.CATALOG_PARSE_VERSION)
                c.syncNow()
            } catch (e: Exception) {
                phase = LoginPhase.Failed("Enregistrement impossible (${e.javaClass.simpleName}).")
            }
        }
    }

    fun demo() {
        phase = LoginPhase.Saving
        viewModelScope.launch { c.accounts.startDemo(); c.syncNow() }
    }
}
