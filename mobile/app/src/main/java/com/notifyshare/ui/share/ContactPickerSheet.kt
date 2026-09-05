package com.notifyshare.ui.share

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.notifyshare.ui.common.EmptyState
import com.notifyshare.ui.common.PillButton
import com.notifyshare.ui.common.SectionLabel
import com.notifyshare.ui.theme.NotifyShareColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class ContactEntry(val name: String, val phone: String)

/**
 * Em vez de digitar um nome "no escuro", mostra a agenda do aparelho com o
 * telefone do lado — o mesmo nome que o WhatsApp usa na notificacao quando o
 * numero esta salvo. So pede o acesso quando o usuario abre isto (nao no
 * onboarding), e explica por que precisa.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactPickerSheet(onDismiss: () -> Unit, onPick: (name: String) -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasContactsPermission(context)) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted = it }

    LaunchedEffect(Unit) {
        if (!granted) launcher.launch(Manifest.permission.READ_CONTACTS)
    }

    var query by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp)) {
            SectionLabel("Escolher contato")

            if (!granted) {
                Text(
                    "Precisa do acesso à sua agenda para mostrar nome e número — assim você " +
                        "escolhe olhando, sem digitar de cabeça.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NotifyShareColors.muted,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
                PillButton(
                    "Permitir acesso aos contatos",
                    onClick = { launcher.launch(Manifest.permission.READ_CONTACTS) },
                )
                return@Column
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Buscar contato") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )

            val contacts by produceState(initialValue = null as List<ContactEntry>?) {
                value = withContext(Dispatchers.IO) { loadContacts(context) }
            }

            when {
                contacts == null -> androidx.compose.foundation.layout.Box(
                    Modifier.fillMaxWidth().heightIn(min = 120.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
                contacts!!.isEmpty() -> EmptyState("Nenhum contato encontrado.")
                else -> {
                    val filtered = contacts!!.filter {
                        query.isBlank() || it.name.contains(query, ignoreCase = true) ||
                            it.phone.contains(query)
                    }
                    LazyColumn(Modifier.heightIn(max = 420.dp)) {
                        items(filtered, key = { it.name + it.phone }) { c ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPick(c.name) }
                                    .padding(vertical = 10.dp),
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(c.name, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        c.phone,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = NotifyShareColors.muted,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun hasContactsPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
        PackageManager.PERMISSION_GRANTED

private fun loadContacts(context: Context): List<ContactEntry> = runCatching {
    val out = LinkedHashMap<String, ContactEntry>()
    context.contentResolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
        ),
        null, null,
        "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC",
    )?.use { cursor ->
        val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
        val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
        while (cursor.moveToNext() && out.size < 800) {
            val name = cursor.getString(nameIdx)?.trim().takeUnless { it.isNullOrBlank() } ?: continue
            val phone = cursor.getString(numIdx)?.trim().orEmpty()
            out.putIfAbsent(name, ContactEntry(name, phone))
        }
    }
    out.values.toList()
}.getOrDefault(emptyList())
