package com.notifyshare.friends.adapter.persistence

import com.notifyshare.friends.domain.Friendship
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface FriendshipRepository : JpaRepository<Friendship, UUID> {

    @Query(
        """
        select f from Friendship f
        where (f.requesterId = :a and f.addresseeId = :b)
           or (f.requesterId = :b and f.addresseeId = :a)
        """
    )
    fun findBetween(@Param("a") a: UUID, @Param("b") b: UUID): Friendship?

    @Query(
        """
        select f from Friendship f
        where f.status = 'accepted' and (f.requesterId = :u or f.addresseeId = :u)
        """
    )
    fun findAcceptedFor(@Param("u") userId: UUID): List<Friendship>

    fun findAllByAddresseeIdAndStatus(addresseeId: UUID, status: String): List<Friendship>

    fun findAllByRequesterIdAndStatus(requesterId: UUID, status: String): List<Friendship>
}
