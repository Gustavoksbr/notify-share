package com.notifyshare.data.remote

import kotlinx.serialization.Serializable

// --- amigos ---------------------------------------------------------------

@Serializable
data class SearchResultDto(
    val nickname: String,
    /** none | friend | request_sent | request_received */
    val relation: String,
    val mutualFriends: Int = 0,
)

@Serializable
data class FriendDto(val nickname: String, val since: String, val online: Boolean = false)

@Serializable
data class FriendRequestDto(val id: String, val nickname: String, val createdAt: String)

@Serializable
data class PendingRequestsDto(
    val incoming: List<FriendRequestDto> = emptyList(),
    val outgoing: List<FriendRequestDto> = emptyList(),
)

@Serializable
data class NicknameBody(val nickname: String)

@Serializable
data class FriendRequestBody(
    val nickname: String,
    /** ao aceitar a amizade, já criar um grant oferecendo minhas notificações */
    val alsoOfferShare: Boolean = false,
    /** ao aceitar a amizade, já pedir para receber as notificações dessa pessoa */
    val alsoRequestShare: Boolean = false,
)

// --- perfil de outra pessoa / bloqueio ----------------------------------

@Serializable
data class UserProfileDto(
    val nickname: String,
    val friend: Boolean = false,
    val blockedByMe: Boolean = false,
    val sharingWithThem: List<GrantDto> = emptyList(),
    val receivingFromThem: List<GrantDto> = emptyList(),
    /** pedidos/ofertas pendentes que eu iniciei — dá pra cancelar */
    val outgoingPending: List<GrantDto> = emptyList(),
)

@Serializable
data class BlockedUserDto(val nickname: String, val since: String)

// --- compartilhamentos ---------------------------------------------------

@Serializable
data class GrantDto(
    val id: String,
    val counterpart: String,
    /** sharer | recipient — meu papel */
    val role: String,
    val status: String,
    val enabledApps: Int = 0,
    val hasSpecificSenders: Boolean = false,
    val initiatedByMe: Boolean = false,
    val createdAt: String,
    val updatedAt: String,
) {
    val isActive get() = status == "active"
    val isPausedByMe get() =
        (role == "sharer" && status == "paused_by_sharer") ||
            (role == "recipient" && status == "paused_by_recipient")
}

@Serializable
data class PendingGrantsDto(
    val incoming: List<GrantDto> = emptyList(),
    val outgoing: List<GrantDto> = emptyList(),
)

@Serializable
data class SenderDto(val senderHash: String, val senderLabel: String? = null)

@Serializable
data class RuleDto(
    val packageName: String,
    val enabled: Boolean = true,
    /** content | sender_only | paused */
    val contentMode: String = "content",
    val allSenders: Boolean = true,
    val batteryThreshold: Int? = null,
    val senders: List<SenderDto> = emptyList(),
    /** só entrega quando o título/corpo contém algum destes termos (YouTube etc.) */
    val textFilters: List<String> = emptyList(),
    /** desliga a proteção contra códigos de verificação para este app */
    val allowCodes: Boolean = false,
)

@Serializable
data class RulesBody(val rules: List<RuleDto>)

/** Regra do lado de quem recebe: silenciar o push de um app (segue no feed). */
@Serializable
data class RecipientAppRuleDto(
    val packageName: String,
    /** content | sender_only | paused — o que o sharer libera */
    val sharerMode: String = "content",
    val enabledBySharer: Boolean = true,
    val notify: Boolean = true,
)

@Serializable
data class NotifyRuleBody(val packageName: String, val notify: Boolean)

// --- aparelhos / presenca ---------------------------------------------

@Serializable
data class RegisterDeviceBody(
    val fcmToken: String,
    val platform: String = "android",
    val deviceLabel: String? = null,
)

@Serializable
data class UnregisterDeviceBody(val fcmToken: String)

@Serializable
data class PresenceDto(
    val nickname: String,
    val online: Boolean,
    val lastSeen: String? = null,
)
