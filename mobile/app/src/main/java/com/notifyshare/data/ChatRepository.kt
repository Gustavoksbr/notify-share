package com.notifyshare.data

import com.notifyshare.data.local.JsonCache
import com.notifyshare.data.remote.CountDto
import com.notifyshare.data.remote.MessageDto
import com.notifyshare.data.remote.NotifyShareApi
import com.notifyshare.data.remote.PresenceDto
import com.notifyshare.data.remote.EditMessageBody
import com.notifyshare.data.remote.SendMessageBody
import com.notifyshare.data.remote.TimelineItemDto
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Conversa (mensagens + auditoria) e presenca. */
class ChatRepository(
    private val api: NotifyShareApi,
    private val json: Json,
    private val cache: JsonCache,
) {

    private val timelineSerializer = ListSerializer(TimelineItemDto.serializer())

    suspend fun timeline(nickname: String, page: Int = 0): ApiResult<List<TimelineItemDto>> {
        val result = apiCall(json) { api.conversation(nickname, page) }
        return when (result) {
            is ApiResult.Ok -> {
                if (page == 0) cache.save(key(nickname), timelineSerializer, result.value)
                result
            }
            is ApiResult.Failure -> {
                if (page == 0 && isConnectivityFailure(result)) {
                    cache.load(key(nickname), timelineSerializer)?.let { return ApiResult.Ok(it) }
                }
                result
            }
        }
    }

    /** Puxa TODAS as páginas da conversa (para exportar). null = falhou já na 1ª. */
    suspend fun fullHistory(nickname: String): List<TimelineItemDto>? {
        val all = mutableListOf<TimelineItemDto>()
        var page = 0
        while (page < 200) {
            val r = apiCall(json) { api.conversation(nickname, page) }
            val batch = (r as? ApiResult.Ok)?.value ?: return if (page == 0) null else all
            if (batch.isEmpty()) break
            all += batch
            if (batch.size < 50) break
            page++
        }
        return all
    }

    suspend fun send(
        nickname: String,
        body: String,
        replyToId: String? = null,
        linkedEventId: String? = null,
    ): ApiResult<MessageDto> =
        apiCall(json) { api.sendMessage(nickname, SendMessageBody(body, replyToId, linkedEventId)) }

    suspend fun edit(messageId: String, body: String): ApiResult<MessageDto> =
        apiCall(json) { api.editMessage(messageId, EditMessageBody(body)) }

    suspend fun delete(messageId: String): ApiResult<Unit> =
        apiCall(json) { api.deleteMessage(messageId) }

    suspend fun markRead(nickname: String): ApiResult<Unit> =
        apiCall(json) { api.markConversationRead(nickname) }

    suspend fun unreadCount(): ApiResult<CountDto> = apiCall(json) { api.messageUnreadCount() }

    suspend fun presence(nicknames: List<String>): ApiResult<List<PresenceDto>> =
        apiCall(json) { api.presence(nicknames.joinToString(",")) }

    private fun key(nickname: String) = "conv:${nickname.lowercase()}"
}
