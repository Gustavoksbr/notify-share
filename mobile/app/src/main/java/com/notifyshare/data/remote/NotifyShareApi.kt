package com.notifyshare.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface NotifyShareApi {

    /** Ping barato para saber se o servidor responde (usado pelo poller). */
    @GET("actuator/health")
    suspend fun health(): Response<okhttp3.ResponseBody>

    // --- auth ---------------------------------------------------------
    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequest): Response<TokenResponse>

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): Response<TokenResponse>

    @POST("auth/google")
    suspend fun googleLogin(@Body body: GoogleLoginRequest): Response<TokenResponse>

    @POST("auth/refresh")
    suspend fun refresh(@Body body: RefreshRequest): Response<TokenResponse>

    @POST("auth/logout")
    suspend fun logout(@Body body: LogoutRequest): Response<Unit>

    @GET("me")
    suspend fun me(): Response<UserDto>

    // --- aparelhos --------------------------------------------------
    @PUT("devices")
    suspend fun registerDevice(@Body body: RegisterDeviceBody): Response<Unit>

    @HTTP(method = "DELETE", path = "devices", hasBody = true)
    suspend fun unregisterDevice(@Body body: UnregisterDeviceBody): Response<Unit>

    @POST("devices/heartbeat")
    suspend fun heartbeat(): Response<Unit>

    // --- amigos ----------------------------------------------------
    @GET("users/search")
    suspend fun search(@Query("q") query: String): Response<List<SearchResultDto>>

    @GET("friends")
    suspend fun friends(): Response<List<FriendDto>>

    @GET("friends/requests")
    suspend fun friendRequests(): Response<PendingRequestsDto>

    @POST("friends/requests")
    suspend fun sendFriendRequest(@Body body: NicknameBody): Response<SearchResultDto>

    @POST("friends/requests/{id}/accept")
    suspend fun acceptFriend(@Path("id") id: String): Response<Unit>

    @POST("friends/requests/{id}/decline")
    suspend fun declineFriend(@Path("id") id: String): Response<Unit>

    @DELETE("friends/{nickname}")
    suspend fun removeFriend(@Path("nickname") nickname: String): Response<Unit>

    // --- perfil de outra pessoa / bloqueio -----------------------
    @GET("users/{nickname}/profile")
    suspend fun userProfile(@Path("nickname") nickname: String): Response<UserProfileDto>

    @GET("blocks")
    suspend fun blocks(): Response<List<BlockedUserDto>>

    @POST("blocks")
    suspend fun block(@Body body: NicknameBody): Response<BlockedUserDto>

    @DELETE("blocks/{nickname}")
    suspend fun unblock(@Path("nickname") nickname: String): Response<Unit>

    // --- grants ---------------------------------------------------
    @GET("grants")
    suspend fun grants(@Query("role") role: String): Response<List<GrantDto>>

    @GET("grants/pending")
    suspend fun pendingGrants(): Response<PendingGrantsDto>

    @POST("grants/offers")
    suspend fun offerGrant(@Body body: NicknameBody): Response<GrantDto>

    @POST("grants/requests")
    suspend fun requestGrant(@Body body: NicknameBody): Response<GrantDto>

    @POST("grants/{id}/accept")
    suspend fun acceptGrant(@Path("id") id: String): Response<GrantDto>

    @POST("grants/{id}/decline")
    suspend fun declineGrant(@Path("id") id: String): Response<Unit>

    @POST("grants/{id}/pause")
    suspend fun pauseGrant(@Path("id") id: String): Response<GrantDto>

    @POST("grants/{id}/resume")
    suspend fun resumeGrant(@Path("id") id: String): Response<GrantDto>

    @DELETE("grants/{id}")
    suspend fun revokeGrant(@Path("id") id: String): Response<Unit>

    @GET("grants/{id}/rules")
    suspend fun grantRules(@Path("id") id: String): Response<List<RuleDto>>

    @PUT("grants/{id}/rules")
    suspend fun setGrantRules(@Path("id") id: String, @Body body: RulesBody): Response<List<RuleDto>>

    @GET("grants/{id}/notify-rules")
    suspend fun notifyRules(@Path("id") id: String): Response<List<RecipientAppRuleDto>>

    @PUT("grants/{id}/notify-rules")
    suspend fun setNotifyRule(@Path("id") id: String, @Body body: NotifyRuleBody): Response<Unit>

    // --- eventos -------------------------------------------------
    @POST("events")
    suspend fun ingest(@Body body: IngestEventBody): Response<IngestResultDto>

    @GET("events")
    suspend fun feed(
        @Query("from") from: String? = null,
        @Query("package") packageName: String? = null,
        @Query("type") type: String? = null,
        @Query("sender") sender: String? = null,
        @Query("period") period: String = "all",
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 50,
    ): Response<List<FeedItemDto>>

    @GET("events/conversation")
    suspend fun conversationEvents(
        @Query("with") with: String,
        @Query("direction") direction: String = "received",
        @Query("package") packageName: String? = null,
        @Query("type") type: String? = null,
        @Query("sender") sender: String? = null,
        @Query("period") period: String = "all",
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 50,
    ): Response<List<FeedItemDto>>

    @POST("events/deliveries/{id}/read")
    suspend fun markEventRead(@Path("id") id: String): Response<Unit>

    @GET("events/unread-count")
    suspend fun eventUnreadCount(): Response<CountDto>

    // --- conversas ---------------------------------------------
    @GET("conversations/{nickname}")
    suspend fun conversation(
        @Path("nickname") nickname: String,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 50,
    ): Response<List<TimelineItemDto>>

    @POST("conversations/{nickname}/messages")
    suspend fun sendMessage(
        @Path("nickname") nickname: String,
        @Body body: SendMessageBody,
    ): Response<MessageDto>

    @POST("conversations/{nickname}/read")
    suspend fun markConversationRead(@Path("nickname") nickname: String): Response<Unit>

    @GET("conversations/unread-count")
    suspend fun messageUnreadCount(): Response<CountDto>

    // --- presenca --------------------------------------------
    @GET("presence")
    suspend fun presence(@Query("users") users: String): Response<List<PresenceDto>>
}
