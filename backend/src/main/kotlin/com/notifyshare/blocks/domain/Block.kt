package com.notifyshare.blocks.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * "blockerId bloqueou blockedId". Direcional: o sentido inverso e outra linha.
 * A amizade e os grants continuam existindo no banco — o bloqueio so barra a
 * troca de mensagens (ver MessageService).
 */
@Entity
@Table(name = "blocks")
class Block(

    @Id
    @Column(name = "id", nullable = false)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "blocker_id", nullable = false)
    val blockerId: UUID,

    @Column(name = "blocked_id", nullable = false)
    val blockedId: UUID,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
)
