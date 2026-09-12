package com.notifyshare.data.local

import android.content.Context
import com.notifyshare.data.remote.RuleDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** Uma notificação (ou aviso do sistema) guardada no cofre local. */
@Serializable
data class VaultItem(
    val id: String,
    val packageName: String,
    val eventType: String = "message",
    val occurredAt: String,
    val savedAt: String,
    val title: String? = null,
    val body: String? = null,
    val sender: String? = null,
    val group: String? = null,
    /** content | sender_only — como a regra do cofre estava configurada. */
    val mode: String = "content",
)

/**
 * "Cofre" de notificações: um espaço privado que vive SÓ neste aparelho e nunca
 * sai para a nuvem. Global — não é por conta nem por login (dá para usar
 * deslogado). Guardado como dois arquivos JSON em filesDir (mesmo estilo do
 * JsonCache): regras (quais apps/contatos capturar) e itens capturados.
 *
 * A captura roda no aparelho: o NotificationRelayService aplica as regras daqui
 * com o VaultMatcher e grava o que casar — independente de haver login ou
 * compartilhamento ativo.
 */
class VaultStore(context: Context) {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    private val dir = File(context.filesDir, "vault").apply { mkdirs() }
    private val rulesFile = File(dir, "rules.json")
    private val itemsFile = File(dir, "items.json")
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _rules = MutableStateFlow<List<RuleDto>>(emptyList())
    val rules: StateFlow<List<RuleDto>> = _rules.asStateFlow()

    private val _items = MutableStateFlow<List<VaultItem>>(emptyList())
    val items: StateFlow<List<VaultItem>> = _items.asStateFlow()

    init {
        scope.launch {
            _rules.value = runCatching {
                if (rulesFile.exists()) json.decodeFromString<List<RuleDto>>(rulesFile.readText()) else emptyList()
            }.getOrDefault(emptyList())
            _items.value = runCatching {
                if (itemsFile.exists()) json.decodeFromString<List<VaultItem>>(itemsFile.readText()) else emptyList()
            }.getOrDefault(emptyList()).sortedByDescending { it.occurredAt }
        }
    }

    /** Snapshot síncrono das regras — para o callback do NotificationListener. */
    fun rulesSnapshot(): List<RuleDto> = _rules.value

    fun rulesNow(): List<RuleDto> = _rules.value

    suspend fun setRules(list: List<RuleDto>) = mutex.withLock {
        _rules.value = list
        val text = json.encodeToString(list)
        withContext(Dispatchers.IO) { runCatching { rulesFile.writeText(text) } }
        Unit
    }

    /** Grava um item capturado (dedup por id, mais novo no topo, teto de MAX). */
    suspend fun addItem(item: VaultItem) = mutex.withLock {
        if (_items.value.any { it.id == item.id }) return@withLock
        _items.value = (listOf(item) + _items.value).take(MAX)
        persistItems()
    }

    suspend fun deleteItem(id: String) = mutex.withLock {
        _items.value = _items.value.filterNot { it.id == id }
        persistItems()
    }

    suspend fun clearItems() = mutex.withLock {
        _items.value = emptyList()
        persistItems()
    }

    private suspend fun persistItems() {
        val text = json.encodeToString(_items.value)
        withContext(Dispatchers.IO) { runCatching { itemsFile.writeText(text) } }
    }

    private companion object {
        const val MAX = 3000
    }
}
