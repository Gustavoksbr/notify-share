package com.notifyshare.friends.application

import com.notifyshare.auth.adapter.persistence.UserRepository
import com.notifyshare.friends.adapter.persistence.FriendshipRepository
import com.notifyshare.friends.domain.Friendship
import com.notifyshare.realtime.application.RealtimePort
import com.notifyshare.shared.web.ConflictException
import com.notifyshare.shared.web.ForbiddenException
import com.notifyshare.shared.web.NotFoundException
import com.notifyshare.shared.web.ValidationException
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

data class SearchResult(
    val nickname: String,
    /** none | friend | request_sent | request_received */
    val relation: String,
    val mutualFriends: Int,
)

data class FriendView(val nickname: String, val since: Instant, val online: Boolean)

data class FriendRequestView(val id: UUID, val nickname: String, val createdAt: Instant)

data class PendingRequests(
    val incoming: List<FriendRequestView>,
    val outgoing: List<FriendRequestView>,
)

@Service
class FriendService(
    private val friendships: FriendshipRepository,
    private val users: UserRepository,
    private val realtime: RealtimePort,
    private val eventPublisher: ApplicationEventPublisher,
) {

    // --- consultas usadas por outras features --------------------------------

    @Transactional(readOnly = true)
    fun areFriends(a: UUID, b: UUID): Boolean =
        friendships.findBetween(a, b)?.status == Friendship.ACCEPTED

    @Transactional(readOnly = true)
    fun friendIds(userId: UUID): Set<UUID> =
        friendships.findAcceptedFor(userId).map { it.otherSide(userId) }.toSet()

    // --- busca --------------------------------------------------------------

    @Transactional(readOnly = true)
    fun search(viewerId: UUID, rawQuery: String): List<SearchResult> {
        val query = rawQuery.trim().lowercase().removePrefix("@")
        if (query.isEmpty()) return emptyList()

        val mine = friendIds(viewerId)
        val outgoing = friendships.findAllByRequesterIdAndStatus(viewerId, Friendship.PENDING)
            .map { it.addresseeId }.toSet()
        val incoming = friendships.findAllByAddresseeIdAndStatus(viewerId, Friendship.PENDING)
            .map { it.requesterId }.toSet()

        return users.findTop20ByNicknameStartingWithAndIdNot(query, viewerId).map { u ->
            SearchResult(
                nickname = u.nickname,
                relation = when (u.id) {
                    in mine -> "friend"
                    in outgoing -> "request_sent"
                    in incoming -> "request_received"
                    else -> "none"
                },
                mutualFriends = (mine intersect friendIds(u.id)).size,
            )
        }
    }

    // --- lista de amigos ---------------------------------------------------

    @Transactional(readOnly = true)
    fun listFriends(userId: UUID): List<FriendView> {
        val accepted = friendships.findAcceptedFor(userId)
        val otherIds = accepted.map { it.otherSide(userId) }
        val names = users.findAllByIdIn(otherIds).associate { it.id to it.nickname }
        val online = realtime.onlineAmong(otherIds)
        return accepted
            .mapNotNull { f ->
                val otherId = f.otherSide(userId)
                names[otherId]?.let {
                    FriendView(it, f.respondedAt ?: f.createdAt, online = otherId in online)
                }
            }
            .sortedWith(compareByDescending<FriendView> { it.online }.thenBy { it.nickname })
    }

    @Transactional(readOnly = true)
    fun pending(userId: UUID): PendingRequests {
        val incoming = friendships.findAllByAddresseeIdAndStatus(userId, Friendship.PENDING)
        val outgoing = friendships.findAllByRequesterIdAndStatus(userId, Friendship.PENDING)
        val names = users.findAllByIdIn(
            (incoming.map { it.requesterId } + outgoing.map { it.addresseeId }).toSet()
        ).associate { it.id to it.nickname }
        return PendingRequests(
            incoming = incoming.mapNotNull { f ->
                names[f.requesterId]?.let { FriendRequestView(f.id, it, f.createdAt) }
            }.sortedByDescending { it.createdAt },
            outgoing = outgoing.mapNotNull { f ->
                names[f.addresseeId]?.let { FriendRequestView(f.id, it, f.createdAt) }
            }.sortedByDescending { it.createdAt },
        )
    }

    // --- mutacoes --------------------------------------------------------

    @Transactional
    fun request(
        requesterId: UUID,
        targetNickname: String,
        alsoOfferShare: Boolean = false,
        alsoRequestShare: Boolean = false,
    ): SearchResult {
        val target = users.findByNickname(targetNickname.trim().lowercase().removePrefix("@"))
            ?: throw NotFoundException("user_not_found", "Nao existe ninguem com esse nickname")
        if (target.id == requesterId) {
            throw ValidationException("self_friend", "Voce nao pode adicionar voce mesmo")
        }

        val existing = friendships.findBetween(requesterId, target.id)
        val relation = when {
            existing == null -> {
                friendships.save(
                    Friendship(
                        requesterId = requesterId,
                        addresseeId = target.id,
                        status = Friendship.PENDING,
                        alsoOfferShare = alsoOfferShare,
                        alsoRequestShare = alsoRequestShare,
                    )
                )
                "request_sent"
            }
            existing.status == Friendship.ACCEPTED ->
                throw ConflictException("already_friends", "Voces ja sao amigos")
            existing.requesterId == requesterId ->
                throw ConflictException("request_pending", "Voce ja enviou um pedido para @${target.nickname}")
            else -> {
                // O outro lado ja tinha pedido: aceitar direto. As intencoes de
                // compartilhamento sao as DESTA chamada (quem esta aceitando).
                existing.status = Friendship.ACCEPTED
                existing.respondedAt = Instant.now()
                friendships.save(existing)
                publishAccepted(requesterId, target.nickname, alsoOfferShare, alsoRequestShare)
                "friend"
            }
        }
        return SearchResult(target.nickname, relation, 0)
    }

    /** O solicitante cancela o proprio pedido pendente. Leva junto qualquer
     *  grant/pedido de compartilhamento que ja exista entre os dois. */
    @Transactional
    fun cancelRequest(requesterId: UUID, friendshipId: UUID) {
        val f = friendships.findById(friendshipId)
            .orElseThrow { NotFoundException("request_not_found", "Pedido nao encontrado") }
        if (f.requesterId != requesterId || f.status != Friendship.PENDING) {
            throw ForbiddenException("not_your_request", "Esse pedido nao e seu para cancelar")
        }
        friendships.delete(f)
        eventPublisher.publishEvent(FriendLinkClearedEvent(f.requesterId, f.addresseeId))
    }

    @Transactional
    fun respond(userId: UUID, friendshipId: UUID, accept: Boolean) {
        val f = friendships.findById(friendshipId)
            .orElseThrow { NotFoundException("request_not_found", "Pedido nao encontrado") }
        if (f.addresseeId != userId || f.status != Friendship.PENDING) {
            throw ForbiddenException("not_your_request", "Esse pedido nao e seu para responder")
        }
        if (accept) {
            f.status = Friendship.ACCEPTED
            f.respondedAt = Instant.now()
            friendships.save(f)
            val addresseeNickname = users.findById(userId).map { it.nickname }.orElse(null)
            if (addresseeNickname != null) {
                publishAccepted(f.requesterId, addresseeNickname, f.alsoOfferShare, f.alsoRequestShare)
            }
        } else {
            friendships.delete(f)
        }
    }

    private fun publishAccepted(
        requesterId: UUID,
        addresseeNickname: String,
        alsoOfferShare: Boolean,
        alsoRequestShare: Boolean,
    ) {
        val event = FriendshipAcceptedEvent(requesterId, addresseeNickname, alsoOfferShare, alsoRequestShare)
        if (event.wantsAnything) eventPublisher.publishEvent(event)
    }

    @Transactional
    fun remove(userId: UUID, otherNickname: String) {
        val other = users.findByNickname(otherNickname.trim().lowercase().removePrefix("@")) ?: return
        val link = friendships.findBetween(userId, other.id) ?: return
        friendships.delete(link)
        // sem amizade nao ha compartilhamento: derruba os grants entre os dois.
        eventPublisher.publishEvent(FriendLinkClearedEvent(userId, other.id))
    }
}
