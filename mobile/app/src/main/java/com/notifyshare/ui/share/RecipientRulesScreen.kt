package com.notifyshare.ui.share

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.notifyshare.data.ApiResult
import com.notifyshare.data.SocialRepository
import com.notifyshare.data.remote.RecipientAppRuleDto
import com.notifyshare.ui.common.EmptyState
import com.notifyshare.ui.common.ErrorRetry
import com.notifyshare.ui.common.LoadingBox
import com.notifyshare.ui.common.ScreenTitle
import com.notifyshare.ui.common.prettyPackage
import com.notifyshare.ui.theme.NotifyShareColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RecipientRulesUiState(
    val loading: Boolean = true,
    val rules: List<RecipientAppRuleDto> = emptyList(),
    val failedToLoad: Boolean = false,
)

class RecipientRulesViewModel(
    private val repo: SocialRepository,
    private val grantId: String,
) : ViewModel() {

    private val _state = MutableStateFlow(RecipientRulesUiState())
    val state: StateFlow<RecipientRulesUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.value = _state.value.copy(loading = _state.value.rules.isEmpty())
        viewModelScope.launch {
            when (val r = repo.notifyRules(grantId)) {
                is ApiResult.Ok -> _state.value = RecipientRulesUiState(loading = false, rules = r.value)
                is ApiResult.Failure ->
                    _state.value = _state.value.copy(loading = false, failedToLoad = _state.value.rules.isEmpty())
            }
        }
    }

    fun toggleNotify(pkg: String, notify: Boolean) {
        // otimista
        _state.value = _state.value.copy(
            rules = _state.value.rules.map { if (it.packageName == pkg) it.copy(notify = notify) else it },
        )
        viewModelScope.launch {
            if (repo.setNotifyRule(grantId, pkg, notify) is ApiResult.Failure) load()
        }
    }
}

@Composable
fun RecipientRulesScreen(vm: RecipientRulesViewModel, nickname: String, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Notificações de @$nickname", onBack = onBack)

        Text(
            "Desligar um app não para o compartilhamento — as notificações continuam " +
                "aparecendo no seu feed, só deixam de te avisar na hora.",
            style = MaterialTheme.typography.labelMedium,
            color = NotifyShareColors.muted,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
        )

        when {
            state.loading -> LoadingBox()
            state.failedToLoad -> ErrorRetry("Não foi possível carregar.", vm::load)
            state.rules.isEmpty() -> EmptyState("@$nickname ainda não liberou nenhum app.")
            else -> LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.rules, key = { it.packageName }) { rule ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(16.dp))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(prettyPackage(rule.packageName), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (rule.notify) "Notifica você" else "Só no feed — sem aviso",
                                style = MaterialTheme.typography.labelSmall,
                                color = NotifyShareColors.muted,
                            )
                        }
                        Switch(
                            checked = rule.notify,
                            onCheckedChange = { vm.toggleNotify(rule.packageName, it) },
                        )
                    }
                }
            }
        }
    }
}
