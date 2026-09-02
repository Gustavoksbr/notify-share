package com.notifyshare.messages.adapter.persistence

import com.notifyshare.messages.domain.Message
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface MessageRepository : JpaRepository<Message, UUID> {

    @Query(
        """
        select m from Message m
        where (m.senderId = :a and m.recipientId = :b)
           or (m.senderId = :b and m.recipientId = :a)
        order by m.createdAt desc
        """
    )
    fun conversation(@Param("a") a: UUID, @Param("b") b: UUID, pageable: Pageable): List<Message>

    @Modifying
    @Query(
        """
        update Message m set m.readAt = :now
        where m.senderId = :other and m.recipientId = :me and m.readAt is null
        """
    )
    fun markConversationRead(@Param("me") me: UUID, @Param("other") other: UUID, @Param("now") now: Instant): Int

    @Query("select count(m) from Message m where m.recipientId = :me and m.readAt is null")
    fun countUnread(@Param("me") me: UUID): Long
}
