package com.numera

import com.numera.shared.config.NumeraProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableConfigurationProperties(NumeraProperties::class)
@EnableScheduling
class NumeraApplication

fun main(args: Array<String>) {
    runApplication<NumeraApplication>(*args)
}