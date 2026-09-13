package com.notifyshare.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.notifyshare.NotifyShareApp
import com.notifyshare.core.AppEvents
import com.notifyshare.core.Connectivity
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.remember
import com.notifyshare.AppContainer
import com.notifyshare.ui.PersonNotificationsViewModelFactory
import com.notifyshare.ui.RulesViewModelFactory
import com.notifyshare.ui.TabViewModelFactory
import com.notifyshare.ui.feed.FeedScreen
import com.notifyshare.ui.feed.FeedViewModel
import com.notifyshare.ui.friends.FriendsScreen
import com.notifyshare.ui.friends.FriendsViewModel
import com.notifyshare.ui.notifications.PersonNotificationsScreen
import com.notifyshare.ui.notifications.PersonNotificationsViewModel
import com.notifyshare.ui.onboarding.PermissionsScreen
import com.notifyshare.ui.profile.ProfileScreen
import com.notifyshare.ui.profile.ProfileViewModel
import com.notifyshare.ui.share.AppPickerScreen
import com.notifyshare.ui.share.IncomingRequestsScreen
import com.notifyshare.ui.share.OutgoingRequestsScreen
import com.notifyshare.ui.share.RulesScreen
import com.notifyshare.ui.share.RulesViewModel
import com.notifyshare.ui.share.ShareScreen
import com.notifyshare.ui.share.ShareViewModel
import com.notifyshare.ui.theme.NotifyIcons
import kotlinx.coroutines.launch

/** Compartilha o JSON exportado via a folha de compartilhamento do sistema. */
private fun shareExport(context: android.content.Context, text: String) {
    runCatching {
        context.startActivity(
            android.content.Intent.createChooser(
                android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(android.content.Intent.EXTRA_TITLE, "notify-share-dados.json")
                    putExtra(android.content.Intent.EXTRA_TEXT, text)
                },
                "Salvar meus dados",
            ),
        )
    }
}

private sealed class Tab(val route: String, val label: String, val icon: ImageVector) {
    data object Feed : Tab("feed", "Feed", NotifyIcons.Bell)
    data object Friends : Tab("friends", "Amigos", NotifyIcons.Users)
    data object Share : Tab("share", "Compartilhar", NotifyIcons.Share)
    data object Profile : Tab("profile", "Perfil", NotifyIcons.User)
}

private val tabs = listOf(Tab.Feed, Tab.Friends, Tab.Share, Tab.Profile)

@Composable
fun MainShell(
    container: AppContainer,
    openTarget: String?,
    onAddAccount: () -> Unit,
    mode: com.notifyshare.data.local.AppMode,
    onModeChange: (com.notifyshare.data.local.AppMode) -> Unit,
) {
    val nav = rememberNavController()
    val tabFactory = remember(container) { TabViewModelFactory(container) }
    val context = LocalContext.current

    // Aquece as abas de dados ao entrar: criadas aqui (escopo do shell, nao de
    // cada rota), o init{load()} de cada uma dispara agora, em paralelo — em vez
    // de so quando a aba e aberta pela primeira vez. Trocar de aba passa a ser
    // instantaneo, com os dados ja em maos.
    val feedVm: FeedViewModel = viewModel(factory = tabFactory)
    val friendsVm: FriendsViewModel = viewModel(factory = tabFactory)
    val shareVm: ShareViewModel = viewModel(factory = tabFactory)

    // Android 13+: pede a permissao de POSTAR notificacoes uma vez. Sem ela o
    // sistema engole todo aviso do app. Ler notificacoes dos outros (NLS) e
    // outra permissao, tratada na tela de Permissoes.
    val postNotifLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) {}
    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS,
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            postNotifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // O WebSocket em si e ligado/desligado pelo ProcessLifecycleOwner (primeiro
    // plano). Aqui sincronizamos o servico de compartilhamento e garantimos o
    // registro do token do FCM agora que ha sessao.
    LaunchedEffect(Unit) {
        (context.applicationContext as? NotifyShareApp)?.refreshSharing()
        com.notifyshare.fcm.FcmSyncWorker.ensureRegistered(context.applicationContext)
        AppEvents.bus.collect {
            if (it == AppEvents.GRANTS) {
                (context.applicationContext as? NotifyShareApp)?.refreshSharing()
            }
        }
    }

    // Badges de "pedidos recebidos" nos icones de baixo (Amigos/Compartilhar).
    LaunchedEffect(Unit) {
        container.refreshRequestBadges()
        AppEvents.bus.collect {
            if (it == AppEvents.GRANTS || it == AppEvents.FRIENDS) container.refreshRequestBadges()
        }
    }

    LaunchedEffect(openTarget) {
        when {
            openTarget == "feed" -> nav.navigate(Tab.Feed.route)
            openTarget == "accounts" -> nav.navigate("accounts")
            openTarget == "grants" -> nav.navigate(Tab.Share.route)
            openTarget == "permissions" -> nav.navigate("permissions")
            openTarget?.startsWith("chat:") == true ->
                nav.navigate("hub/${openTarget.removePrefix("chat:")}/conversa")
        }
    }

    val backEntry by nav.currentBackStackEntryAsState()
    val currentRoute = backEntry?.destination?.route
    val showBottomBar = currentRoute in tabs.map { it.route }

    val requestBadges by container.requestBadges.collectAsStateWithLifecycle()

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    tabs.forEach { tab ->
                        val badgeCount = when (tab) {
                            Tab.Friends -> requestBadges.incomingFriendRequests
                            Tab.Share -> requestBadges.incomingShareRequests
                            else -> 0
                        }
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                if (badgeCount > 0) {
                                    androidx.compose.material3.BadgedBox(
                                        badge = {
                                            androidx.compose.material3.Badge {
                                                Text(if (badgeCount > 99) "99+" else "$badgeCount")
                                            }
                                        },
                                    ) { Icon(tab.icon, tab.label) }
                                } else {
                                    Icon(tab.icon, tab.label)
                                }
                            },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            com.notifyshare.ui.shell.AppModeSwitcher(mode, onModeChange)
            com.notifyshare.ui.common.AppWarningBanners(
                container = container,
                onOpenPermissions = { nav.navigate("permissions") },
            )
            NavHost(
                navController = nav,
                startDestination = Tab.Feed.route,
            ) {
            composable(Tab.Feed.route) {
                FeedScreen(
                    vm = feedVm,
                    onOpenPerson = { nick -> nav.navigate("person/$nick?from=feed") },
                    onReplyToNotification = { eventId, from ->
                        nav.navigate("hub/$from/conversa?linkedEvent=$eventId")
                    },
                )
            }
            composable(Tab.Friends.route) {
                FriendsScreen(
                    vm = friendsVm,
                    onOpenHub = { nick, hubTab -> nav.navigate("hub/$nick/$hubTab") },
                    onOpenProfile = { nick -> nav.navigate("user/$nick?from=friends") },
                )
            }
            composable(Tab.Share.route) {
                ShareScreen(
                    vm = shareVm,
                    onOpenIncomingRequests = { nav.navigate("requests-incoming") },
                    onOpenOutgoingRequests = { nav.navigate("requests-outgoing") },
                    onOpenRules = { grantId, nick -> nav.navigate("rules/$grantId/$nick") },
                    onOpenNotifyRules = { grantId, nick -> nav.navigate("notify-rules/$grantId/$nick") },
                    onOpenNotifications = { nick -> nav.navigate("person/$nick?from=share") },
                )
            }
            composable(Tab.Profile.route) {
                val vm: ProfileViewModel = viewModel(factory = tabFactory)
                ProfileScreen(
                    vm = vm,
                    onOpenPermissions = { nav.navigate("permissions") },
                    onOpenAccounts = { nav.navigate("accounts") },
                    onOpenPrivacy = { nav.navigate("privacy") },
                    onOpenDangerZone = { nav.navigate("danger-zone") },
                )
            }

            composable("privacy") {
                val ctx = LocalContext.current
                com.notifyshare.ui.settings.PrivacyScreen(
                    onBack = { nav.popBackStack() },
                    onExport = { container.authRepository.exportData() },
                    onSaveExport = { text -> shareExport(ctx, text) },
                )
            }

            composable("danger-zone") {
                // mesma instancia do ProfileViewModel (o tabFactory nao guarda estado
                // entre navegacoes, mas deleteAccount so precisa do que ja esta nela)
                val vm: ProfileViewModel = viewModel(factory = tabFactory)
                val ctx = LocalContext.current
                com.notifyshare.ui.profile.DangerZoneScreen(
                    vm = vm,
                    onBack = { nav.popBackStack() },
                    onDeleteHistory = {
                        container.feedRepository.deleteMyHistory() is com.notifyshare.data.ApiResult.Ok
                    },
                    onAccountDeleted = { stillLoggedIn ->
                        com.notifyshare.ui.restartUiForAccountChange(
                            ctx, if (stillLoggedIn) "accounts" else null,
                        )
                    },
                    onExport = { container.authRepository.exportData() },
                    onSaveExport = { text -> shareExport(ctx, text) },
                )
            }

            composable("accounts") {
                val vm: com.notifyshare.ui.profile.AccountsViewModel = viewModel(factory = tabFactory)
                com.notifyshare.ui.profile.AccountsScreen(
                    vm = vm,
                    onBack = { 
                        // Volta para a tela anterior
                        nav.popBackStack()
                    },
                    onAddAccount = onAddAccount,
                )
            }

            composable("requests-incoming") {
                IncomingRequestsScreen(
                    vm = shareVm,
                    onBack = { nav.popBackStack() },
                    onOpenNotifications = { nick -> nav.navigate("person/$nick?from=requests") },
                )
            }
            composable("requests-outgoing") {
                OutgoingRequestsScreen(
                    vm = shareVm,
                    onBack = { nav.popBackStack() },
                )
            }
            composable(
                "user/{nickname}?from={from}",
                arguments = listOf(
                    androidx.navigation.navArgument("from") {
                        type = androidx.navigation.NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { entry ->
                val nick = entry.arguments?.getString("nickname").orEmpty()
                val from = entry.arguments?.getString("from")
                val vm: com.notifyshare.ui.profile.UserProfileViewModel =
                    viewModel(factory = com.notifyshare.ui.UserProfileViewModelFactory(container, nick))
                com.notifyshare.ui.profile.UserProfileScreen(
                    vm = vm,
                    onBack = {
                        // Volta para onde veio
                        when (from) {
                            "friends" -> nav.popBackStack(Tab.Friends.route, inclusive = false)
                            else -> nav.popBackStack()
                        }
                    },
                    onOpenChat = { n -> nav.navigate("hub/$n/conversa") },
                )
            }
            composable(
                "person/{nickname}?highlight={highlight}&from={from}",
                arguments = listOf(
                    androidx.navigation.navArgument("highlight") {
                        type = androidx.navigation.NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    androidx.navigation.navArgument("from") {
                        type = androidx.navigation.NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { entry ->
                val nick = entry.arguments?.getString("nickname").orEmpty()
                val highlight = entry.arguments?.getString("highlight")
                val vm: PersonNotificationsViewModel =
                    viewModel(factory = PersonNotificationsViewModelFactory(container, nick))
                PersonNotificationsScreen(
                    vm = vm, nickname = nick, onBack = { nav.popBackStack() },
                    highlightEventId = highlight,
                    onReplyToNotification = { eventId ->
                        nav.navigate("hub/$nick/conversa?linkedEvent=$eventId")
                    },
                )
            }
            composable(
                "hub/{nickname}/{tab}?linkedEvent={linkedEvent}",
                arguments = listOf(
                    androidx.navigation.navArgument("linkedEvent") {
                        type = androidx.navigation.NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { entry ->
                val nick = entry.arguments?.getString("nickname").orEmpty()
                val hubTab = entry.arguments?.getString("tab")
                val linkedEvent = entry.arguments?.getString("linkedEvent")
                com.notifyshare.ui.hub.PersonHubScreen(
                    container = container,
                    hubEntry = entry,
                    nickname = nick,
                    initialTab = hubTab,
                    initialLinkedEvent = linkedEvent,
                    onBack = { nav.popBackStack() },
                    onOpenProfile = { nav.navigate("user/$nick?from=hub") },
                    onOpenAppPicker = { grantId -> nav.navigate("hub-apps/$grantId/$nick") },
                    onOpenSystemAlerts = { nav.navigate("system-alerts") },
                )
            }
            composable("hub-apps/{grantId}/{nickname}") { entry ->
                val grantId = entry.arguments?.getString("grantId").orEmpty()
                val nick = entry.arguments?.getString("nickname").orEmpty()
                // Mesmo RulesViewModel da aba "Apps" do hub: o seletor mexe no
                // estado nao salvo (dirty) que a aba precisa manter ao voltar.
                val hostEntry = remember(entry) { nav.previousBackStackEntry }
                val vm: RulesViewModel = if (hostEntry != null) {
                    viewModel(
                        viewModelStoreOwner = hostEntry,
                        key = "hub-rules-$grantId",
                        factory = RulesViewModelFactory(container, grantId),
                    )
                } else {
                    viewModel(factory = RulesViewModelFactory(container, grantId))
                }
                AppPickerScreen(vm = vm, nickname = nick, onBack = { nav.popBackStack() })
            }
            composable("rules/{grantId}/{nickname}") { entry ->
                val grantId = entry.arguments?.getString("grantId").orEmpty()
                val nick = entry.arguments?.getString("nickname").orEmpty()
                val vm: RulesViewModel = viewModel(factory = RulesViewModelFactory(container, grantId))
                RulesScreen(
                    vm = vm, nickname = nick, onBack = {
                        // Volta para a tela anterior
                        nav.popBackStack()
                    },
                    onPickApps = { nav.navigate("apps/$grantId/$nick") },
                    onOpenSystemAlerts = { nav.navigate("system-alerts") },
                )
            }
            composable("apps/{grantId}/{nickname}") { entry ->
                val grantId = entry.arguments?.getString("grantId").orEmpty()
                val nick = entry.arguments?.getString("nickname").orEmpty()
                // O seletor DEVE compartilhar o mesmo RulesViewModel da tela de
                // regras (de onde ele sempre é aberto). Sem isto, `addApp` mexia
                // numa instância paralela e o app escolhido nunca aparecia lá —
                // ele "sumia" da lista do seletor (virava "já adicionado") e nada
                // acontecia ao voltar.
                val rulesEntry = remember(entry) { nav.getBackStackEntry("rules/$grantId/$nick") }
                val vm: RulesViewModel = viewModel(
                    viewModelStoreOwner = rulesEntry,
                    factory = RulesViewModelFactory(container, grantId),
                )
                AppPickerScreen(vm = vm, nickname = nick, onBack = { 
                    // Volta para Rules (pai direto)
                    nav.popBackStack()
                })
            }
            composable("notify-rules/{grantId}/{nickname}") { entry ->
                val grantId = entry.arguments?.getString("grantId").orEmpty()
                val nick = entry.arguments?.getString("nickname").orEmpty()
                val vm: com.notifyshare.ui.share.RecipientRulesViewModel =
                    viewModel(factory = com.notifyshare.ui.RecipientRulesViewModelFactory(container, grantId))
                com.notifyshare.ui.share.RecipientRulesScreen(
                    vm = vm, nickname = nick, onBack = { 
                        // Volta para a tela anterior
                        nav.popBackStack()
                    },
                )
            }
            composable("system-alerts") {
                com.notifyshare.ui.settings.SystemAlertsScreen(
                    container = container,
                    onBack = { nav.popBackStack() },
                )
            }
            composable("permissions") {
                val fcmStatus by container.fcmStatus.collectAsStateWithLifecycle(initialValue = null)
                val serviceEnabled by container.serviceSwitch.enabled
                    .collectAsStateWithLifecycle(initialValue = true)
                val scope = androidx.compose.runtime.rememberCoroutineScope()
                PermissionsScreen(
                    fcmStatus = fcmStatus,
                    onBack = { nav.popBackStack() },
                    onSendTest = {
                        (container.feedRepository.sendTestNotification()
                            as? com.notifyshare.data.ApiResult.Ok)?.value?.deliveries
                    },
                    serviceEnabled = serviceEnabled,
                    onSetService = { on ->
                        scope.launch {
                            container.serviceSwitch.set(on)
                            (context.applicationContext as? NotifyShareApp)?.refreshSharing()
                        }
                    },
                )
            }
            }
        }
    }
}
