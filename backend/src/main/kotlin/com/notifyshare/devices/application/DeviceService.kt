package com.notifyshare.devices.application

import com.notifyshare.devices.adapter.persistence.DeviceRepository
import com.notifyshare.devices.domain.Device
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class DeviceService(private val devices: DeviceRepository) {

    private val log = LoggerFactory.getLogger(javaClass)

    /** Idempotente: um token ja conhecido so tem o dono e o carimbo atualizados. */
    @Transactional
    fun register(userId: UUID, fcmToken: String, platform: String?, deviceLabel: String?): Device {
        val device = devices.findByFcmToken(fcmToken) ?: Device(userId = userId, fcmToken = fcmToken)
        device.userId = userId
        platform?.let { device.platform = it }
        deviceLabel?.let { device.deviceLabel = it.take(80) }
        device.lastSeenAt = Instant.now()
        val saved = devices.save(device)
        log.debug("aparelho registrado para {}", userId)
        return saved
    }

    @Transactional
    fun unregister(userId: UUID, fcmToken: String) {
        val device = devices.findByFcmToken(fcmToken) ?: return
        if (device.userId == userId) devices.delete(device)
    }

    /** Marca todos os aparelhos do usuario como vistos agora. */
    @Transactional
    fun heartbeat(userId: UUID) {
        val now = Instant.now()
        devices.findAllByUserId(userId).forEach { it.lastSeenAt = now }
    }

    /** Tokens de push agrupados por usuario, para o roteamento de eventos. */
    @Transactional(readOnly = true)
    fun tokensByUser(userIds: Collection<UUID>): Map<UUID, List<String>> =
        devices.findAllByUserIdIn(userIds).groupBy({ it.userId }, { it.fcmToken })

    @Transactional(readOnly = true)
    fun lastSeen(userId: UUID): Instant? =
        devices.findAllByUserId(userId).maxOfOrNull { it.lastSeenAt }

    /** Chamado pelo envio de push quando o FCM diz que um token morreu. */
    @Transactional
    fun dropTokens(tokens: Collection<String>) {
        if (tokens.isNotEmpty()) {
            devices.deleteAllByFcmTokenIn(tokens.toSet())
            log.info("{} token(s) de FCM removidos por rejeicao", tokens.size)
        }
    }
}
