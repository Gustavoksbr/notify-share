package com.notifyshare.grants.adapter.persistence

import com.notifyshare.grants.domain.Grant
import com.notifyshare.grants.domain.GrantAudit
import com.notifyshare.grants.domain.GrantRule
import com.notifyshare.grants.domain.GrantRuleSender
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface GrantRepository : JpaRepository<Grant, UUID> {

    fun findBySharerIdAndRecipientId(sharerId: UUID, recipientId: UUID): Grant?

    fun findAllBySharerIdAndStatusIn(sharerId: UUID, statuses: Collection<String>): List<Grant>

    fun findAllByRecipientIdAndStatusIn(recipientId: UUID, statuses: Collection<String>): List<Grant>

    @Query(
        """
        select g from Grant g
        where (g.sharerId = :a and g.recipientId = :b)
           or (g.sharerId = :b and g.recipientId = :a)
        """
    )
    fun findAllBetween(@Param("a") a: UUID, @Param("b") b: UUID): List<Grant>

    @Query(
        """
        select g from Grant g
        where g.status in ('offered_by_sharer', 'requested_by_recipient')
          and (g.sharerId = :u or g.recipientId = :u)
        """
    )
    fun findPendingFor(@Param("u") userId: UUID): List<Grant>
}

interface GrantRuleRepository : JpaRepository<GrantRule, UUID> {

    fun findAllByGrantId(grantId: UUID): List<GrantRule>

    fun findAllByGrantIdIn(grantIds: Collection<UUID>): List<GrantRule>

    fun findByGrantIdAndPackageName(grantId: UUID, packageName: String): GrantRule?

    fun deleteAllByGrantId(grantId: UUID)
}

interface GrantRuleSenderRepository : JpaRepository<GrantRuleSender, UUID> {

    fun findAllByRuleId(ruleId: UUID): List<GrantRuleSender>

    fun findAllByRuleIdIn(ruleIds: Collection<UUID>): List<GrantRuleSender>

    fun deleteAllByRuleIdIn(ruleIds: Collection<UUID>)
}

interface GrantAuditRepository : JpaRepository<GrantAudit, UUID> {

    fun findAllByGrantIdInOrderByCreatedAtDesc(grantIds: Collection<UUID>): List<GrantAudit>
}
