package me.masonasons.fastsm.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.masonasons.fastsm.data.repo.AccountRepository
import me.masonasons.fastsm.domain.model.PlatformType
import me.masonasons.fastsm.platform.bluesky.BlueskyApi
import me.masonasons.fastsm.platform.mastodon.MastodonApi
import me.masonasons.fastsm.platform.mastodon.MastodonOAuth

class AddAccountViewModel(
    private val httpClient: HttpClient,
    private val accountRepository: AccountRepository,
    private val oauthCoordinator: OAuthCoordinator,
) : ViewModel() {

    private val _state = MutableStateFlow(AddAccountState())
    val state: StateFlow<AddAccountState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<AddAccountEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<AddAccountEvent> = _events.asSharedFlow()

    private var pendingMastodonApp: PendingMastodonApp? = null

    init {
        viewModelScope.launch {
            oauthCoordinator.callbacks.collect { cb ->
                when (cb) {
                    is OAuthCallback.Code -> completeMastodonLogin(cb.code)
                    is OAuthCallback.Error -> _state.value = _state.value.copy(
                        status = Status.Idle,
                        error = "Authorization failed: ${cb.error}",
                    )
                }
            }
        }
    }

    fun onPlatformChanged(platform: PlatformType) {
        _state.value = _state.value.copy(platform = platform, error = null)
    }

    fun onInstanceChanged(value: String) {
        _state.value = _state.value.copy(instance = value, error = null)
    }

    fun onHandleChanged(value: String) {
        _state.value = _state.value.copy(handle = value, error = null)
    }

    fun onAppPasswordChanged(value: String) {
        _state.value = _state.value.copy(appPassword = value, error = null)
    }

    fun startLogin() {
        when (_state.value.platform) {
            PlatformType.MASTODON -> startMastodonLogin()
            PlatformType.BLUESKY -> startBlueskyLogin()
        }
    }

    private fun startMastodonLogin() {
        val instance = MastodonOAuth.normalizeInstance(_state.value.instance)
        if (instance.isBlank()) {
            _state.value = _state.value.copy(error = "Enter an instance URL (e.g. mastodon.social)")
            return
        }
        _state.value = _state.value.copy(status = Status.Registering, error = null)
        viewModelScope.launch {
            runCatching {
                val api = MastodonApi(httpClient, instance) { null }
                api.registerApp(
                    clientName = MastodonOAuth.CLIENT_NAME,
                    redirectUri = MastodonOAuth.REDIRECT_URI,
                    scopes = MastodonOAuth.SCOPES,
                    website = MastodonOAuth.WEBSITE,
                )
            }.onSuccess { app ->
                pendingMastodonApp = PendingMastodonApp(instance, app.client_id, app.client_secret)
                val authorize = MastodonOAuth.buildAuthorizeUrl(instance, app.client_id)
                _state.value = _state.value.copy(status = Status.AwaitingBrowser)
                _events.tryEmit(AddAccountEvent.OpenBrowser(authorize.toString()))
            }.onFailure { e ->
                _state.value = _state.value.copy(
                    status = Status.Idle,
                    error = "Could not register app on $instance: ${e.message ?: e.javaClass.simpleName}",
                )
            }
        }
    }

    private fun completeMastodonLogin(code: String) {
        val pending = pendingMastodonApp
        if (pending == null) {
            _state.value = _state.value.copy(
                status = Status.Idle,
                error = "Received OAuth code without a pending request",
            )
            return
        }
        _state.value = _state.value.copy(status = Status.ExchangingToken)
        viewModelScope.launch {
            runCatching {
                val unauthed = MastodonApi(httpClient, pending.instance) { null }
                val token = unauthed.exchangeToken(
                    clientId = pending.clientId,
                    clientSecret = pending.clientSecret,
                    code = code,
                    redirectUri = MastodonOAuth.REDIRECT_URI,
                    scopes = MastodonOAuth.SCOPES,
                )
                val authed = MastodonApi(httpClient, pending.instance) { token.access_token }
                val user = authed.verifyCredentials()
                Triple(token.access_token, user, pending)
            }.onSuccess { (accessToken, user, p) ->
                val accountId = accountRepository.saveMastodonAccount(
                    instance = p.instance,
                    userId = user.id,
                    acct = if (user.acct.contains('@')) user.acct else "${user.acct}@${instanceHost(p.instance)}",
                    displayName = user.display_name.ifBlank { user.username },
                    avatar = user.avatar,
                    accessToken = accessToken,
                    clientId = p.clientId,
                    clientSecret = p.clientSecret,
                )
                pendingMastodonApp = null
                _state.value = AddAccountState()
                _events.tryEmit(AddAccountEvent.LoggedIn(accountId))
            }.onFailure { e ->
                _state.value = _state.value.copy(
                    status = Status.Idle,
                    error = "Login failed: ${e.message ?: e.javaClass.simpleName}",
                )
            }
        }
    }

    private fun startBlueskyLogin() {
        val handle = _state.value.handle.trim().removePrefix("@")
        val password = _state.value.appPassword
        if (handle.isBlank() || password.isBlank()) {
            _state.value = _state.value.copy(error = "Enter your handle and app password")
            return
        }
        _state.value = _state.value.copy(status = Status.ExchangingToken, error = null)
        viewModelScope.launch {
            runCatching {
                val pds = BlueskyApi.DEFAULT_PDS
                val api = BlueskyApi(httpClient, pds)
                val session = api.createSession(handle, password)
                val authed = BlueskyApi(httpClient, pds) { session.accessJwt }
                val profile = authed.getProfile(session.did)
                Triple(session, profile, pds)
            }.onSuccess { (session, profile, pds) ->
                val accountId = accountRepository.saveBlueskyAccount(
                    pdsBase = pds,
                    did = session.did,
                    handle = session.handle,
                    displayName = profile.displayName?.ifBlank { null } ?: session.handle,
                    avatar = profile.avatar,
                    accessJwt = session.accessJwt,
                    refreshJwt = session.refreshJwt,
                )
                _state.value = AddAccountState()
                _events.tryEmit(AddAccountEvent.LoggedIn(accountId))
            }.onFailure { e ->
                _state.value = _state.value.copy(
                    status = Status.Idle,
                    error = "Bluesky login failed: ${e.message ?: e.javaClass.simpleName}",
                )
            }
        }
    }

    private fun instanceHost(base: String): String =
        base.removePrefix("https://").removePrefix("http://").removeSuffix("/")

    private data class PendingMastodonApp(val instance: String, val clientId: String, val clientSecret: String)
}

data class AddAccountState(
    val platform: PlatformType = PlatformType.MASTODON,
    val instance: String = "",
    val handle: String = "",
    val appPassword: String = "",
    val status: Status = Status.Idle,
    val error: String? = null,
) {
    val busy: Boolean get() = status != Status.Idle
}

enum class Status { Idle, Registering, AwaitingBrowser, ExchangingToken }

sealed interface AddAccountEvent {
    data class OpenBrowser(val url: String) : AddAccountEvent
    data class LoggedIn(val accountId: Long) : AddAccountEvent
}
