package com.notifyshare.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class ReplySnippetDto(
    val id: String,
    val preview: String = "",
    val mine: Boolean = false,
    val deleted: Boolean = false,
)

@Serializable
data class LinkedEventRefDto(
    val eventId: String,
    val packageName: String,
    val eventType: String,
    /** ISO-8601 */
    val occurredAt: String,
)

@Serializable
data class TimelineItemDto(
    /** message | audit */
    val kind: String,
    val id: String,
    /** ISO-8601 */
    val at: String,
    val mine: Boolean,
    val body: String? = null,
    val readAt: String? = null,
    /** so em audit: offered | activated | paused | resumed | revoked | rules_changed */
    val action: String? = null,
    val edited: Boolean = false,
    val deleted: Boolean = false,
    val replyTo: ReplySnippetDto? = null,
    val linkedEvent: LinkedEventRefDto? = null,
)

@Serializable
data class MessageDto(
    val id: String,
    val mine: Boolean,
    val body: String,
    val createdAt: String,
    val readAt: String? = null,
    val edited: Boolean = false,
)

@Serializable
data class SendMessageBody(
    val body: String,
    val replyToId: String? = null,
    val linkedEventId: String? = null,
)

@Serializable
data class EditMessageBody(val body: String)
