package com.numera.shared.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.Executor
import java.util.concurrent.Executors

@Configuration
class AsyncConfig {
    @Bean
    fun taskExecutor(): Executor = Executors.newVirtualThreadPerTaskExecutor()
}