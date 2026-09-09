package com.notifyshare.grants.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

/**
 * Remetente liberado de um app. `senderHash`, nao `senderName`: da para filtrar
 * por igualdade sem o servidor saber o nome. `senderLabel` e o nome legivel: a
 * UI de quem compartilha usa para a lista, e o destinatario tambem ve (em
 * "recebo de") para saber de quais contatos daquele app ele recebe — de todo
 * jeito ele veria o nome chegar na notificacao.
 */
@Entity
@Table(name = "grant_rule_senders")
class GrantRuleSender(

    @Id
    @Column(name = "id", nullable = false)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "rule_id", nullable = false)
    val ruleId: UUID,

    @Column(name = "sender_hash", nullable = false, length = 64)
    val senderHash: String,

    @Column(name = "sender_label", length = 120)
    var senderLabel: String? = null,
)
