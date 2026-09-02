package com.notifyshare.data

import com.notifyshare.data.remote.GrantDto
import com.notifyshare.data.remote.FriendRequestBody
import com.notifyshare.data.remote.NicknameBody
import com.notifyshare.data.remote.NotifyShareApi
import com.notifyshare.data.remote.PendingGrantsDto
import com.notifyshare.data.remote.PendingRequestsDto
import com.notifyshare.data.remote.RuleDto
import com.notifyshare.data.remote.RulesBody
import com.notifyshare.data.remote.SearchResultDto
import com.notifyshare.data.remote.FriendDto
import com.notifyshare.data.remote.BlockedUserDto
import com.notifyshare.data.remote.UserProfileDto
import com.notifyshare.data.local.JsonCache
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Amigos + compartilhamentos + regras. Tudo que vive nas abas Amigos e Compartilhar. */
class SocialRepository(
    private val api: NotifyShareApi,
    private val json: Json,
    private val cache: JsonCache,
) {

    private val friendsSerializer = ListSerializer(FriendDto.serializer())
    private val grantsSerializer = ListSerializer(GrantDto.serializer())

    // --- amigos ---------------------------------------------------
    suspend fun search(query: String): ApiResult<List<SearchResultDto>> =
        apiCall(json) { api.search(query) }

    suspend fun friends(): ApiResult<List<FriendDto>> {
        val result = apiCall(json) { api.friends() }
        return when (result) {
            is ApiResult.Ok -> {
                cache.save(KEY_FRIENDS, friendsSerializer, result.value)
                result
            }
            is ApiResult.Failure -> {
                if (isConnectivityFailure(result)) {
                    cache.load(KEY_FRIENDS, friendsSerializer)?.let { return ApiResult.Ok(it) }
                }
                result
            }
        }
    }

    suspend fun friendRequests(): ApiResult<PendingRequestsDto> = apiCall(json) { api.friendRequests() }

    suspend fun addFriend(
        nickname: String,
        alsoOfferShare: Boolean = false,
        alsoRequestShare: Boolean = false,
    ): ApiResult<SearchResultDto> =
        apiCall(json) {
            api.sendFriendRequest(FriendRequestBody(nickname, alsoOfferShare, alsoRequestShare))
        }

    suspend fun acceptFriend(id: String): ApiResult<Unit> = apiCall(json) { api.acceptFriend(id) }

    suspend fun declineFriend(id: String): ApiResult<Unit> = apiCall(json) { api.declineFriend(id) }

    suspend fun cancelFriendRequest(id: String): ApiResult<Unit> =
        apiCall(json) { api.cancelFriendRequest(id) }

    suspend fun removeFriend(nickname: String): ApiResult<Unit> = apiCall(json) { api.removeFriend(nickname) }

    // --- perfil de outra pessoa / bloqueio ----------------------
    suspend fun userProfile(nickname: String): ApiResult<UserProfileDto> =
        apiCall(json) { api.userProfile(nickname) }

    suspend fun blockedUsers(): ApiResult<List<BlockedUserDto>> = apiCall(json) { api.blocks() }

    suspend fun block(nickname: String): ApiResult<BlockedUserDto> =
        apiCall(json) { api.block(NicknameBody(nickname)) }

    suspend fun unblock(nickname: String): ApiResult<Unit> = apiCall(json) { api.unblock(nickname) }

    // --- grants -------------------------------------------------
    suspend fun grants(role: String): ApiResult<List<GrantDto>> {
        val result = apiCall(json) { api.grants(role) }
        return when (result) {
            is ApiResult.Ok -> {
                cache.save("grants:$role", grantsSerializer, result.value)
                result
            }
            is ApiResult.Failure -> {
                if (isConnectivityFailure(result)) {
                    cache.load("grants:$role", grantsSerializer)?.let { return ApiResult.Ok(it) }
                }
                result
            }
        }
    }

    suspend fun pendingGrants(): ApiResult<PendingGrantsDto> = apiCall(json) { api.pendingGrants() }

    suspend fun offerGrant(nickname: String): ApiResult<GrantDto> =
        apiCall(json) { api.offerGrant(NicknameBody(nickname)) }

    suspend fun requestGrant(nickname: String): ApiResult<GrantDto> =
        apiCall(json) { api.requestGrant(NicknameBody(nickname)) }

    suspend fun acceptGrant(id: String): ApiResult<GrantDto> = apiCall(json) { api.acceptGrant(id) }

    suspend fun declineGrant(id: String): ApiResult<Unit> = apiCall(json) { api.declineGrant(id) }

    suspend fun pauseGrant(id: String): ApiResult<GrantDto> = apiCall(json) { api.pauseGrant(id) }

    suspend fun resumeGrant(id: String): ApiResult<GrantDto> = apiCall(json) { api.resumeGrant(id) }

    suspend fun revokeGrant(id: String): ApiResult<Unit> = apiCall(json) { api.revokeGrant(id) }

    // --- regras ------------------------------------------------
    suspend fun rules(grantId: String): ApiResult<List<RuleDto>> = apiCall(json) { api.grantRules(grantId) }

    suspend fun setRules(grantId: String, rules: List<RuleDto>): ApiResult<List<RuleDto>> =
        apiCall(json) { api.setGrantRules(grantId, RulesBody(rules)) }

    // --- regras de quem recebe (silenciar app) ------------------
    suspend fun notifyRules(grantId: String): ApiResult<List<com.notifyshare.data.remote.RecipientAppRuleDto>> =
        apiCall(json) { api.notifyRules(grantId) }

    suspend fun setNotifyRule(grantId: String, packageName: String, notify: Boolean): ApiResult<Unit> =
        apiCall(json) { api.setNotifyRule(grantId, com.notifyshare.data.remote.NotifyRuleBody(packageName, notify)) }

    private companion object {
        const val KEY_FRIENDS = "friends"
    }
}
