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

/** Uma página do feed, com o total de itens que batem com o filtro (sem paginar) —
 *  usado para montar "Página X de Y" na UI. */
data class FeedPage(val items: List<FeedItemDto>, val total: Long)

/** Feed do destinatario + ingestao dos eventos capturados no proprio aparelho. */
class FeedRepository(
    private val api: NotifyShareApi,
    private val json: Json,
    private val cache: JsonCache,
) {

    private val feedSerializer = ListSerializer(FeedItemDto.serializer())

    /** Teto de itens por pagina — igual ao maximo aceito pelo backend. */
    val pageSize = 100

    suspend fun feed(query: FeedQuery = FeedQuery(), page: Int = 0): ApiResult<FeedPage> {
        val isMainFeed = page == 0 && query == FeedQuery()
        val result = apiCall(json) {
            api.feed(query.from, query.packageName, query.type, query.sender, query.period, page, pageSize)
        }
        return when (result) {
            is ApiResult.Ok -> {
                if (isMainFeed) cache.save(KEY_FEED, feedSerializer, result.value.items)
                ApiResult.Ok(FeedPage(result.value.items, result.value.total))
            }
            is ApiResult.Failure -> {
                // Offline (sem internet, servidor fora, ou token expirado sem rede
                // para renovar): devolve o ultimo feed conhecido. A barra global
                // avisa que os dados estao velhos. So cai no cache quando o
                // problema e de conexao — erro real do servidor propaga. O total
                // exato fica indisponivel offline; usa o tamanho do cache.
                if (isMainFeed && isConnectivityFailure(result)) {
                    cache.load(KEY_FEED, feedSerializer)
                        ?.let { return ApiResult.Ok(FeedPage(it, it.size.toLong())) }
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
    ): ApiResult<FeedPage> = when (
        val result = apiCall(json) {
            api.conversationEvents(
                with, direction, query.packageName, query.type, query.sender, query.period, page, pageSize,
            )
        }
    ) {
        is ApiResult.Ok -> ApiResult.Ok(FeedPage(result.value.items, result.value.total))
        is ApiResult.Failure -> result
    }

    /** Puxa TODAS as páginas das notificações trocadas com alguém, numa direção
     *  (para exportar). null = falhou já na 1ª página. */
    suspend fun fullConversation(
        with: String,
        direction: String,
        query: FeedQuery = FeedQuery(),
    ): List<FeedItemDto>? {
        val all = mutableListOf<FeedItemDto>()
        var page = 0
        while (page < 200) {
            val batch = (conversation(with, direction, query, page = page) as? ApiResult.Ok)?.value?.items
                ?: return if (page == 0) null else all
            if (batch.isEmpty()) break
            all += batch
            if (batch.size < pageSize) break
            page++
        }
        return all
    }

    suspend fun ingest(body: IngestEventBody): ApiResult<IngestResultDto> =
        apiCall(json) { api.ingest(body) }

    suspend fun sendTestNotification(): ApiResult<IngestResultDto> =
        apiCall(json) { api.sendTestNotification() }

    suspend fun deleteMyHistory(): ApiResult<Unit> = apiCall(json) { api.deleteMyHistory() }

    suspend fun markRead(deliveryId: String): ApiResult<Unit> =
        apiCall(json) { api.markEventRead(deliveryId) }

    suspend fun unreadCount(): ApiResult<CountDto> = apiCall(json) { api.eventUnreadCount() }

    suspend fun eventLocation(
        eventId: String,
        query: FeedQuery = FeedQuery(),
        pageSize: Int = 50,
    ): ApiResult<com.notifyshare.data.remote.EventLocationDto> = apiCall(json) {
        api.eventLocation(
            eventId,
            query.from,
            query.packageName,
            query.type,
            query.sender,
            query.period,
            pageSize,
        )
    }

    suspend fun eventContext(
        eventId: String,
        query: FeedQuery = FeedQuery(),
        size: Int = 50,
    ): ApiResult<List<FeedItemDto>> = apiCall(json) {
        api.eventContext(
            eventId,
            query.from,
            query.packageName,
            query.type,
            query.sender,
            query.period,
            size,
        )
    }

    private companion object {
        const val KEY_FEED = "feed"
    }
}
