package me.masonasons.fastsm.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import me.masonasons.fastsm.di.AppContainer
import me.masonasons.fastsm.ui.compose.ComposeScreen
import me.masonasons.fastsm.ui.compose.ComposeViewModel
import androidx.compose.runtime.CompositionLocalProvider
import me.masonasons.fastsm.ui.home.HomeScreen
import me.masonasons.fastsm.ui.home.HomeViewModel
import me.masonasons.fastsm.ui.home.LocalAutoFocusCompose
import me.masonasons.fastsm.ui.home.LocalEnabledPostActions
import me.masonasons.fastsm.ui.home.LocalPostTemplates
import me.masonasons.fastsm.ui.home.LocalSubmitOnImeAction
import me.masonasons.fastsm.ui.home.PostTemplates
import me.masonasons.fastsm.ui.setup.AddAccountScreen
import me.masonasons.fastsm.ui.setup.AddAccountViewModel
import me.masonasons.fastsm.ui.profile.ProfileScreen
import me.masonasons.fastsm.ui.profile.ProfileViewModel
import me.masonasons.fastsm.domain.model.UniversalMedia
import me.masonasons.fastsm.ui.media.MediaViewerScreen
import me.masonasons.fastsm.ui.search.SearchScreen
import me.masonasons.fastsm.ui.search.SearchViewModel
import me.masonasons.fastsm.ui.settings.AccountSettingsScreen
import me.masonasons.fastsm.ui.settings.SettingsScreen
import me.masonasons.fastsm.ui.settings.SettingsViewModel
import me.masonasons.fastsm.ui.thread.ThreadScreen
import me.masonasons.fastsm.ui.thread.ThreadViewModel
import me.masonasons.fastsm.util.CustomTabs
import me.masonasons.fastsm.util.MediaLauncher

private object Routes {
    const val LOADING = "loading"
    const val ADD_ACCOUNT = "add-account"
    const val HOME = "home"
    const val COMPOSE = "compose"
    // Query args (not path args) so Bluesky AT-URIs with slashes don't break routing.
    const val THREAD = "thread?statusId={statusId}"
    fun thread(statusId: String): String =
        "thread?statusId=${java.net.URLEncoder.encode(statusId, "UTF-8")}"
    const val PROFILE = "profile?userId={userId}"
    fun profile(userId: String): String =
        "profile?userId=${java.net.URLEncoder.encode(userId, "UTF-8")}"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val ACCOUNT_SETTINGS = "account-settings?accountId={accountId}"
    fun accountSettings(accountId: Long): String = "account-settings?accountId=$accountId"
    const val MEDIA = "media?url={url}&type={type}&desc={desc}"
    fun media(url: String, type: String, description: String?): String {
        val enc = { s: String -> java.net.URLEncoder.encode(s, "UTF-8") }
        val descPart = description?.let { "&desc=${enc(it)}" } ?: ""
        return "media?url=${enc(url)}&type=${enc(type)}$descPart"
    }
}

@Composable
fun FastSmNavGraph(container: AppContainer) {
    val navController = rememberNavController()
    val accounts by container.accountRepository.accounts.collectAsStateWithLifecycle(initialValue = null)
    val factory = remember(container) { ViewModelFactory(container) }
    val context = androidx.compose.ui.platform.LocalContext.current

    // All media (image / gifv / video / audio) routes through the in-app
    // viewer. The launcher is still imported as a fallback for unexpected
    // types, but in practice every media.type we receive is handled inline.
    val onOpenMedia: (UniversalMedia) -> Unit = { media ->
        navController.navigate(Routes.media(media.url, media.type, media.description))
    }
    val onOpenLink: (String) -> Unit = { url ->
        runCatching { CustomTabs.launch(context, android.net.Uri.parse(url)) }
    }

    val startDestination = when {
        accounts == null -> Routes.LOADING
        accounts!!.isEmpty() -> Routes.ADD_ACCOUNT
        else -> Routes.HOME
    }

    LaunchedEffect(accounts) {
        val list = accounts ?: return@LaunchedEffect
        if (list.isEmpty()) {
            val current = navController.currentBackStackEntry?.destination?.route
            if (current != null && current != Routes.ADD_ACCOUNT) {
                navController.navigate(Routes.ADD_ACCOUNT) {
                    popUpTo(0) { inclusive = true }
                    launchSingleTop = true
                }
            }
        }
    }

    val enabledPostActions by container.appPrefs.enabledPostActions.collectAsStateWithLifecycle()
    val postTpl by container.appPrefs.postTemplate.collectAsStateWithLifecycle()
    val boostTpl by container.appPrefs.boostTemplate.collectAsStateWithLifecycle()
    val notifTpl by container.appPrefs.notificationTemplate.collectAsStateWithLifecycle()
    val cwMode by container.appPrefs.cwMode.collectAsStateWithLifecycle()
    val includeMedia by container.appPrefs.includeMediaDescriptions.collectAsStateWithLifecycle()
    val demojifyNames by container.appPrefs.demojifyDisplayNames.collectAsStateWithLifecycle()
    val maxUsernames by container.appPrefs.maxUsernamesDisplay.collectAsStateWithLifecycle()
    val templates = PostTemplates(
        post = postTpl,
        boost = boostTpl,
        notification = notifTpl,
        config = me.masonasons.fastsm.domain.template.TemplateConfig(
            cwMode = cwMode,
            includeMediaDescriptions = includeMedia,
            demojifyDisplayNames = demojifyNames,
            maxUsernamesDisplay = maxUsernames,
        ),
    )
    val autoFocusCompose by container.appPrefs.autoFocusCompose.collectAsStateWithLifecycle()
    val submitOnImeAction by container.appPrefs.submitOnImeAction.collectAsStateWithLifecycle()

    CompositionLocalProvider(
        LocalEnabledPostActions provides enabledPostActions,
        LocalPostTemplates provides templates,
        LocalAutoFocusCompose provides autoFocusCompose,
        LocalSubmitOnImeAction provides submitOnImeAction,
    ) {
    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.LOADING) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        composable(Routes.ADD_ACCOUNT) {
            val vm: AddAccountViewModel = viewModel(factory = factory)
            AddAccountScreen(
                viewModel = vm,
                onLoggedIn = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.ADD_ACCOUNT) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(Routes.HOME) {
            val vm: HomeViewModel = viewModel(factory = factory)
            HomeScreen(
                viewModel = vm,
                onReply = { status ->
                    vm.prepareReply(status)
                    navController.navigate(Routes.COMPOSE)
                },
                onOpenThread = { status -> navController.navigate(Routes.thread(status.id)) },
                onOpenProfile = { userId -> navController.navigate(Routes.profile(userId)) },
                onOpenMedia = onOpenMedia,
                onOpenLink = onOpenLink,
                onCompose = { navController.navigate(Routes.COMPOSE) },
                onAddAccount = { navController.navigate(Routes.ADD_ACCOUNT) },
                onOpenSearch = { navController.navigate(Routes.SEARCH) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenAccountSettings = { id -> navController.navigate(Routes.accountSettings(id)) },
            )
        }
        composable(Routes.COMPOSE) {
            val vm: ComposeViewModel = viewModel(factory = factory)
            ComposeScreen(
                viewModel = vm,
                onClose = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.THREAD,
            arguments = listOf(navArgument("statusId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val statusId = backStackEntry.arguments?.getString("statusId").orEmpty()
            val threadFactory = remember(container, statusId) {
                ThreadViewModelFactory(container, statusId)
            }
            val vm: ThreadViewModel = viewModel(
                key = "thread-$statusId",
                factory = threadFactory,
            )
            ThreadScreen(
                viewModel = vm,
                onReply = { status ->
                    vm.prepareReply(status)
                    navController.navigate(Routes.COMPOSE)
                },
                onOpenThread = { status -> navController.navigate(Routes.thread(status.id)) },
                onOpenProfile = { userId -> navController.navigate(Routes.profile(userId)) },
                onOpenMedia = onOpenMedia,
                onOpenLink = onOpenLink,
                onCompose = { navController.navigate(Routes.COMPOSE) },
                onClose = { navController.popBackStack() },
            )
        }
        composable(Routes.SETTINGS) {
            val vm: SettingsViewModel = viewModel(factory = factory)
            SettingsScreen(viewModel = vm, onClose = { navController.popBackStack() })
        }
        composable(
            route = Routes.ACCOUNT_SETTINGS,
            arguments = listOf(navArgument("accountId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getLong("accountId") ?: return@composable
            AccountSettingsScreen(
                accountId = id,
                accountRepository = container.accountRepository,
                feedback = container.feedbackManager,
                onClose = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.MEDIA,
            arguments = listOf(
                navArgument("url") { type = NavType.StringType },
                navArgument("type") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("desc") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { backStackEntry ->
            MediaViewerScreen(
                url = backStackEntry.arguments?.getString("url").orEmpty(),
                type = backStackEntry.arguments?.getString("type"),
                description = backStackEntry.arguments?.getString("desc"),
                onClose = { navController.popBackStack() },
            )
        }
        composable(Routes.SEARCH) {
            val vm: SearchViewModel = viewModel(factory = factory)
            SearchScreen(
                viewModel = vm,
                onOpenProfile = { userId -> navController.navigate(Routes.profile(userId)) },
                onOpenThread = { status -> navController.navigate(Routes.thread(status.id)) },
                onAddedHashtag = { navController.popBackStack() },
                onClose = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.PROFILE,
            arguments = listOf(navArgument("userId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId").orEmpty()
            val profileFactory = remember(container, userId) {
                ProfileViewModelFactory(container, userId)
            }
            val vm: ProfileViewModel = viewModel(
                key = "profile-$userId",
                factory = profileFactory,
            )
            ProfileScreen(
                viewModel = vm,
                onReply = { status ->
                    vm.prepareReply(status)
                    navController.navigate(Routes.COMPOSE)
                },
                onOpenThread = { status -> navController.navigate(Routes.thread(status.id)) },
                onOpenProfile = { userId -> navController.navigate(Routes.profile(userId)) },
                onOpenMedia = onOpenMedia,
                onOpenLink = onOpenLink,
                onCompose = { navController.navigate(Routes.COMPOSE) },
                onClose = { navController.popBackStack() },
            )
        }
    }
    }
}

private class ProfileViewModelFactory(
    private val container: AppContainer,
    private val userId: String,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass == ProfileViewModel::class.java) { "Unexpected $modelClass" }
        return ProfileViewModel(
            userId = userId,
            accountRepository = container.accountRepository,
            timelineRepository = container.timelineRepository,
            platformFactory = container.platformFactory,
            composeDraftStore = container.composeDraftStore,
            feedback = container.feedbackManager,
        ) as T
    }
}

private class ThreadViewModelFactory(
    private val container: AppContainer,
    private val statusId: String,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass == ThreadViewModel::class.java) { "Unexpected $modelClass" }
        return ThreadViewModel(
            rootStatusId = statusId,
            accountRepository = container.accountRepository,
            platformFactory = container.platformFactory,
            composeDraftStore = container.composeDraftStore,
            feedback = container.feedbackManager,
        ) as T
    }
}

private class ViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when (modelClass) {
        AddAccountViewModel::class.java -> AddAccountViewModel(
            httpClient = container.httpClient,
            accountRepository = container.accountRepository,
            oauthCoordinator = container.oauthCoordinator,
        ) as T
        HomeViewModel::class.java -> HomeViewModel(
            accountRepository = container.accountRepository,
            timelineRepository = container.timelineRepository,
            timelinePositionRepository = container.timelinePositionRepository,
            platformFactory = container.platformFactory,
            composeDraftStore = container.composeDraftStore,
            appPrefs = container.appPrefs,
            feedback = container.feedbackManager,
        ) as T
        ComposeViewModel::class.java -> ComposeViewModel(
            accountRepository = container.accountRepository,
            platformFactory = container.platformFactory,
            draftStore = container.composeDraftStore,
            feedback = container.feedbackManager,
        ) as T
        SearchViewModel::class.java -> SearchViewModel(
            accountRepository = container.accountRepository,
            timelineRepository = container.timelineRepository,
            platformFactory = container.platformFactory,
        ) as T
        SettingsViewModel::class.java -> SettingsViewModel(
            appPrefs = container.appPrefs,
            feedbackPrefs = container.feedbackPrefs,
            feedback = container.feedbackManager,
            notificationPrefs = container.notificationPrefs,
            appUpdater = container.appUpdater,
        ) as T
        else -> error("Unknown ViewModel: $modelClass")
    }
}
