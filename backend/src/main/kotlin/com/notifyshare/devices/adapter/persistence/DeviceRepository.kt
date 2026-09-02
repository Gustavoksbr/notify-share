package com.notifyshare.devices.adapter.persistence

import com.notifyshare.devices.domain.Device
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface DeviceRepository : JpaRepository<Device, UUID> {

    fun findByFcmToken(fcmToken: String): Device?

    fun findAllByUserId(userId: UUID): List<Device>

    fun findAllByUserIdIn(userIds: Collection<UUID>): List<Device>

    fun deleteAllByFcmTokenIn(fcmTokens: Collection<String>)
}
