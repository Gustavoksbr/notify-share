package com.notifyshare.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Idioma corrente da interface ("pt" | "en"), fornecido no topo da árvore de
 * composição (ver `NotifyShareRoot`) a partir do `LanguageStore` persistido.
 *
 * Isto é o começo da tradução, não o fim: hoje só as telas novas
 * (Configurações/Preferências) e a barra de baixo do modo Local usam [tr] — o
 * resto do app continua com texto fixo em português. Migrar tela por tela é
 * trabalho futuro (ver `ideias/`), não algo que dá para fazer de uma vez sem
 * risco de quebrar string por string.
 */
val LocalAppLanguage: ProvidableCompositionLocal<String> = staticCompositionLocalOf { "pt" }

/** Escolhe [pt] ou [en] conforme o idioma corrente. Uso: `Text(tr("Salvos", "Saved"))`. */
@Composable
fun tr(pt: String, en: String): String = if (LocalAppLanguage.current == "en") en else pt
