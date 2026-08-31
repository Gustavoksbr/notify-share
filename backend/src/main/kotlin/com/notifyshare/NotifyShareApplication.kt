package com.notifyshare

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan

@SpringBootApplication
@ConfigurationPropertiesScan
class NotifyShareApplication

fun main(args: Array<String>) {
    runApplication<NotifyShareApplication>(*args)
}
