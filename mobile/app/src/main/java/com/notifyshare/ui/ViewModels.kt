package com.notifyshare.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.notifyshare.AppContainer
import com.notifyshare.ui.chat.ChatViewModel
import com.notifyshare.ui.feed.FeedViewModel
import com.notifyshare.ui.friends.FriendsViewModel
import com.notifyshare.ui.notifications.PersonNotificationsViewModel
import com.notifyshare.ui.profile.ProfileViewModel
import com.notifyshare.ui.share.RulesViewModel
import com.notifyshare.ui.share.ShareViewModel

/** Factory unica para os ViewModels sem argumento (as abas). */
class TabViewModelFactory(private val c: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(FeedViewModel::class.java) ->
            FeedViewModel(c.feedRepository, c.session)
        modelClass.isAssignableFrom(FriendsViewModel::class.java) ->
            FriendsViewModel(c.socialRepository)
        modelClass.isAssignableFrom(ShareViewModel::class.java) ->
            ShareViewModel(c.socialRepository)
        modelClass.isAssignableFrom(ProfileViewModel::class.java) ->
            ProfileViewModel(c.authRepository, c.deviceRepository, c.session, c.cache)
        modelClass.isAssignableFrom(com.notifyshare.ui.profile.AccountsViewModel::class.java) ->
            com.notifyshare.ui.profile.AccountsViewModel(c.authRepository)
        else -> error("ViewModel nao mapeado: ${modelClass.name}")
    } as T
}

/** ViewModels que dependem de um argumento de navegacao. */
class ChatViewModelFactory(
    private val c: AppContainer,
    private val nickname: String,
    private val linkedEventId: String? = null,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ChatViewModel(c.chatRepository, c.realtime, nickname, linkedEventId) as T
}

class RulesViewModelFactory(private val c: AppContainer, private val grantId: String) :
    ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        RulesViewModel(c.socialRepository, grantId) as T
}

class RecipientRulesViewModelFactory(private val c: AppContainer, private val grantId: String) :
    ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        com.notifyshare.ui.share.RecipientRulesViewModel(c.socialRepository, grantId) as T
}

class PersonNotificationsViewModelFactory(private val c: AppContainer, private val nickname: String) :
    ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        PersonNotificationsViewModel(c.feedRepository, nickname) as T
}

class UserProfileViewModelFactory(private val c: AppContainer, private val nickname: String) :
    ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        com.notifyshare.ui.profile.UserProfileViewModel(c.socialRepository, nickname) as T
}

class PersonHubViewModelFactory(private val c: AppContainer, private val nickname: String) :
    ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        com.notifyshare.ui.hub.PersonHubViewModel(c.socialRepository, nickname) as T
}
