package com.notifyshare.data

import com.notifyshare.data.local.JsonCache
import com.notifyshare.data.remote.CountDto
import com.notifyshare.data.remote.FeedItemDto
import com.notifyshare.data.remote.IngestEventBody
import com.notifyshare.data.remote.IngestResultDto
import com.notifyshare.data.remote.NotifyShareApi
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

data class FeedQuery(
    val from: String? = null,
    val packageName: String? = null,
    val type: String? = null,
    val sender: String? = null,
    /** all | today | 7d */
    val period: String = "all",
)

/** Feed do destinatario + ingestao dos eventos capturados no proprio aparelho. */
class FeedRepository(
    private val api: NotifyShareApi,
    private val json: Json,
    private val cache: JsonCache,
) {

    private val feedSerializer = ListSerializer(FeedItemDto.serializer())

    /** Teto de itens por pagina — igual ao maximo aceito pelo backend. */
    val pageSize = 100

    suspend fun feed(query: FeedQuery = FeedQuery(), page: Int = 0): ApiResult<List<FeedItemDto>> {
        val isMainFeed = page == 0 && query == FeedQuery()
        val result = apiCall(json) {
            api.feed(query.from, query.packageName, query.type, query.sender, query.period, page, pageSize)
        }
        return when (result) {
            is ApiResult.Ok -> {
                if (isMainFeed) cache.save(KEY_FEED, feedSerializer, result.value)
                result
            }
            is ApiResult.Failure -> {
                // Offline (sem internet, servidor fora, ou token expirado sem rede
                // para renovar): devolve o ultimo feed conhecido. A barra global
                // avisa que os dados estao velhos. So cai no cache quando o
                // problema e de conexao — erro real do servidor propaga.
                if (isMainFeed && isConnectivityFailure(result)) {
                    cache.load(KEY_FEED, feedSerializer)?.let { return ApiResult.Ok(it) }
                }
                result
            }
        }
    }

    suspend fun conversation(
        with: String,
        direction: String,
        query: FeedQuery = FeedQuery(),
        page: Int = 0,
    ): ApiResult<List<FeedItemDto>> = apiCall(json) {
        api.conversationEvents(
            with, direction, query.packageName, query.type, query.sender, query.period, page, pageSize,
        )
    }

    suspend fun ingest(body: IngestEventBody): ApiResult<IngestResultDto> =
        apiCall(json) { api.ingest(body) }

    suspend fun markRead(deliveryId: String): ApiResult<Unit> =
        apiCall(json) { api.markEventRead(deliveryId) }

    suspend fun unreadCount(): ApiResult<CountDto> = apiCall(json) { api.eventUnreadCount() }

    private companion object {
        const val KEY_FEED = "feed"
    }
}
