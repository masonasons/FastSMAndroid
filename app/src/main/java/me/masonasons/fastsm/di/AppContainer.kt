package me.masonasons.fastsm.di

import android.content.Context
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import me.masonasons.fastsm.data.db.FastSmDatabase
import me.masonasons.fastsm.data.feedback.FeedbackManager
import me.masonasons.fastsm.data.feedback.FeedbackPrefs
import me.masonasons.fastsm.data.feedback.HapticsEngine
import me.masonasons.fastsm.data.feedback.SoundEngine
import me.masonasons.fastsm.data.feedback.SpeechEngine
import me.masonasons.fastsm.data.prefs.AppPrefs
import me.masonasons.fastsm.data.prefs.TokenStore
import me.masonasons.fastsm.data.repo.AccountRepository
import me.masonasons.fastsm.data.repo.TimelinePositionRepository
import me.masonasons.fastsm.data.repo.TimelineRepository
import me.masonasons.fastsm.platform.PlatformFactory
import me.masonasons.fastsm.platform.bluesky.BlueskyAuthCoordinator
import me.masonasons.fastsm.ui.compose.ComposeDraftStore
import me.masonasons.fastsm.ui.setup.OAuthCoordinator
import me.masonasons.fastsm.util.AppUpdater

class AppContainer(context: Context) {

    val appContext: Context = context.applicationContext

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    val httpClient: HttpClient = HttpClient(OkHttp) {
        // Throw on non-2xx so platform code can catch ClientRequestException
        // (needed for the Bluesky JWT-refresh-on-401 path).
        expectSuccess = true
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 30_000
        }
        install(Logging) { level = LogLevel.INFO }
        install(WebSockets)
        install(DefaultRequest) {
            header(HttpHeaders.Accept, "application/json")
            header(HttpHeaders.UserAgent, "FastSM-Android/0.2.1")
        }
    }

    val database: FastSmDatabase by lazy { FastSmDatabase.create(appContext) }
    val tokenStore: TokenStore by lazy { TokenStore(appContext) }
    val appPrefs: AppPrefs by lazy { AppPrefs(appContext) }

    val accountRepository: AccountRepository by lazy {
        AccountRepository(database.accountDao(), tokenStore, appPrefs)
    }

    val timelineRepository: TimelineRepository by lazy {
        TimelineRepository(database.timelineDao())
    }

    val timelinePositionRepository: TimelinePositionRepository by lazy {
        TimelinePositionRepository(database.timelinePositionDao())
    }

    val blueskyAuthCoordinator: BlueskyAuthCoordinator by lazy {
        BlueskyAuthCoordinator(httpClient, tokenStore)
    }

    val platformFactory: PlatformFactory by lazy {
        PlatformFactory(httpClient, accountRepository, blueskyAuthCoordinator)
    }

    val oauthCoordinator: OAuthCoordinator by lazy { OAuthCoordinator() }

    val composeDraftStore: ComposeDraftStore by lazy { ComposeDraftStore() }

    val feedbackPrefs: FeedbackPrefs by lazy { FeedbackPrefs(appContext) }
    val notificationPrefs: me.masonasons.fastsm.data.notify.NotificationPrefs by lazy {
        me.masonasons.fastsm.data.notify.NotificationPrefs(appContext)
    }
    val appUpdater: AppUpdater by lazy { AppUpdater(appContext, httpClient) }

    val feedbackManager: FeedbackManager by lazy {
        FeedbackManager(
            prefs = feedbackPrefs,
            speech = SpeechEngine(appContext, feedbackPrefs),
            sound = SoundEngine(appContext, feedbackPrefs, accountRepository),
            haptics = HapticsEngine(appContext, feedbackPrefs),
        )
    }
}
