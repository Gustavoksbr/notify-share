package com.notifyshare.blocks.adapter.persistence

import com.notifyshare.blocks.domain.Block
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface BlockRepository : JpaRepository<Block, UUID> {

    fun findByBlockerIdAndBlockedId(blockerId: UUID, blockedId: UUID): Block?

    fun existsByBlockerIdAndBlockedId(blockerId: UUID, blockedId: UUID): Boolean

    fun findAllByBlockerId(blockerId: UUID): List<Block>

    /** Existe bloqueio em qualquer sentido entre os dois? */
    @Query(
        """
        select count(b) > 0 from Block b
        where (b.blockerId = :a and b.blockedId = :b)
           or (b.blockerId = :b and b.blockedId = :a)
        """
    )
    fun existsEitherDirection(@Param("a") a: UUID, @Param("b") b: UUID): Boolean
}
