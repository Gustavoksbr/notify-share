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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.notifyshare.ui.ChatViewModelFactory
import com.notifyshare.ui.PersonNotificationsViewModelFactory
import com.notifyshare.ui.RulesViewModelFactory
import com.notifyshare.ui.TabViewModelFactory
import com.notifyshare.ui.chat.ChatScreen
import com.notifyshare.ui.chat.ChatViewModel
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
import com.notifyshare.ui.share.RequestsScreen
import com.notifyshare.ui.share.RulesScreen
import com.notifyshare.ui.share.RulesViewModel
import com.notifyshare.ui.share.ShareScreen
import com.notifyshare.ui.share.ShareViewModel
import com.notifyshare.ui.theme.NotifyIcons

private sealed class Tab(val route: String, val label: String, val icon: ImageVector) {
    data object Feed : Tab("feed", "Feed", NotifyIcons.Bell)
    data object Friends : Tab("friends", "Amigos", NotifyIcons.Users)
    data object Share : Tab("share", "Compartilhar", NotifyIcons.Share)
    data object Profile : Tab("profile", "Perfil", NotifyIcons.User)
}

private val tabs = listOf(Tab.Feed, Tab.Friends, Tab.Share, Tab.Profile)

@Composable
fun MainShell(container: AppContainer, openTarget: String?, onAddAccount: () -> Unit) {
    val nav = rememberNavController()
    val tabFactory = remember(container) { TabViewModelFactory(container) }
    val context = LocalContext.current

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

    LaunchedEffect(openTarget) {
        when {
            openTarget == "feed" -> nav.navigate(Tab.Feed.route)
            openTarget == "grants" -> nav.navigate(Tab.Share.route)
            openTarget?.startsWith("chat:") == true ->
                nav.navigate("chat/${openTarget.removePrefix("chat:")}")
        }
    }

    val backEntry by nav.currentBackStackEntryAsState()
    val currentRoute = backEntry?.destination?.route
    val showBottomBar = currentRoute in tabs.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, tab.label) },
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
        val netState by Connectivity.state.collectAsStateWithLifecycle()
        Column(Modifier.padding(padding)) {
            val banner = when (netState) {
                com.notifyshare.core.NetState.NO_INTERNET -> "Você está sem internet"
                com.notifyshare.core.NetState.SERVER_DOWN -> "Servidor fora do ar — tentando reconectar"
                com.notifyshare.core.NetState.OK -> null
            }
            if (banner != null) {
                Text(
                    banner,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(vertical = 6.dp),
                )
            }
            NavHost(
                navController = nav,
                startDestination = Tab.Feed.route,
            ) {
            composable(Tab.Feed.route) {
                val vm: FeedViewModel = viewModel(factory = tabFactory)
                FeedScreen(
                    vm = vm,
                    onOpenPerson = { nick -> nav.navigate("person/$nick") },
                )
            }
            composable(Tab.Friends.route) {
                val vm: FriendsViewModel = viewModel(factory = tabFactory)
                FriendsScreen(
                    vm = vm,
                    onOpenRequests = { nav.navigate("requests") },
                    onOpenChat = { nick -> nav.navigate("chat/$nick") },
                    onOpenProfile = { nick -> nav.navigate("user/$nick") },
                )
            }
            composable(Tab.Share.route) {
                val vm: ShareViewModel = viewModel(factory = tabFactory)
                ShareScreen(
                    vm = vm,
                    onOpenRequests = { nav.navigate("requests") },
                    onOpenRules = { grantId, nick -> nav.navigate("rules/$grantId/$nick") },
                    onOpenNotifyRules = { grantId, nick -> nav.navigate("notify-rules/$grantId/$nick") },
                )
            }
            composable(Tab.Profile.route) {
                val vm: ProfileViewModel = viewModel(factory = tabFactory)
                ProfileScreen(
                    vm = vm,
                    onOpenPermissions = { nav.navigate("permissions") },
                    onOpenAccounts = { nav.navigate("accounts") },
                )
            }

            composable("accounts") {
                val vm: com.notifyshare.ui.profile.AccountsViewModel = viewModel(factory = tabFactory)
                com.notifyshare.ui.profile.AccountsScreen(
                    vm = vm,
                    onBack = { nav.popBackStack() },
                    onAddAccount = onAddAccount,
                )
            }

            composable("requests") {
                val vm: ShareViewModel = viewModel(factory = tabFactory)
                RequestsScreen(vm = vm, onBack = { nav.popBackStack() })
            }
            composable("chat/{nickname}") { entry ->
                val nick = entry.arguments?.getString("nickname").orEmpty()
                val vm: ChatViewModel = viewModel(factory = ChatViewModelFactory(container, nick))
                ChatScreen(vm = vm, nickname = nick, onBack = { nav.popBackStack() },
                    onOpenNotifications = { nav.navigate("person/$nick") },
                    onOpenProfile = { nav.navigate("user/$nick") })
            }
            composable("user/{nickname}") { entry ->
                val nick = entry.arguments?.getString("nickname").orEmpty()
                val vm: com.notifyshare.ui.profile.UserProfileViewModel =
                    viewModel(factory = com.notifyshare.ui.UserProfileViewModelFactory(container, nick))
                com.notifyshare.ui.profile.UserProfileScreen(vm = vm, onBack = { nav.popBackStack() })
            }
            composable("person/{nickname}") { entry ->
                val nick = entry.arguments?.getString("nickname").orEmpty()
                val vm: PersonNotificationsViewModel =
                    viewModel(factory = PersonNotificationsViewModelFactory(container, nick))
                PersonNotificationsScreen(vm = vm, nickname = nick, onBack = { nav.popBackStack() })
            }
            composable("rules/{grantId}/{nickname}") { entry ->
                val grantId = entry.arguments?.getString("grantId").orEmpty()
                val nick = entry.arguments?.getString("nickname").orEmpty()
                val vm: RulesViewModel = viewModel(factory = RulesViewModelFactory(container, grantId))
                RulesScreen(
                    vm = vm, nickname = nick, onBack = { nav.popBackStack() },
                    onPickApps = { nav.navigate("apps/$grantId/$nick") },
                )
            }
            composable("apps/{grantId}/{nickname}") { entry ->
                val grantId = entry.arguments?.getString("grantId").orEmpty()
                val nick = entry.arguments?.getString("nickname").orEmpty()
                val vm: RulesViewModel = viewModel(factory = RulesViewModelFactory(container, grantId))
                AppPickerScreen(vm = vm, nickname = nick, onBack = { nav.popBackStack() })
            }
            composable("notify-rules/{grantId}/{nickname}") { entry ->
                val grantId = entry.arguments?.getString("grantId").orEmpty()
                val nick = entry.arguments?.getString("nickname").orEmpty()
                val vm: com.notifyshare.ui.share.RecipientRulesViewModel =
                    viewModel(factory = com.notifyshare.ui.RecipientRulesViewModelFactory(container, grantId))
                com.notifyshare.ui.share.RecipientRulesScreen(
                    vm = vm, nickname = nick, onBack = { nav.popBackStack() },
                )
            }
            composable("permissions") {
                val fcmStatus by container.fcmStatus.collectAsStateWithLifecycle(initialValue = null)
                PermissionsScreen(fcmStatus = fcmStatus, onBack = { nav.popBackStack() })
            }
            }
        }
    }
}
