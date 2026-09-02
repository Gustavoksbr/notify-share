package com.notifyshare.auth.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "users")
class User(

    @Id
    @Column(name = "id", nullable = false)
    val id: UUID = UUID.randomUUID(),

    /** Identificador publico. Guardado ja normalizado em minusculas. */
    @Column(name = "nickname", nullable = false, length = 30)
    var nickname: String,

    /** Privado. Nunca sai numa resposta que nao seja a do proprio dono. */
    @Column(name = "email", nullable = false, length = 254)
    var email: String,

    /** Nulo em conta criada so pelo Google (sem senha). */
    @Column(name = "password_hash", length = 255)
    var passwordHash: String? = null,

    /** `sub` do Google, quando a conta esta vinculada ao login com Google. */
    @Column(name = "google_sub", length = 255)
    var googleSub: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)
