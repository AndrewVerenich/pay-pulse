package com.paypulse.projection.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.ReactiveTransactionManager
import org.springframework.transaction.reactive.TransactionalOperator

@Configuration
class R2dbcTransactionConfiguration {

  @Bean
  fun transactionalOperator(manager: ReactiveTransactionManager): TransactionalOperator =
    TransactionalOperator.create(manager)
}
