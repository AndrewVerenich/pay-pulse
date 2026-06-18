package com.paypulse.gateway.auth.repository

import com.paypulse.gateway.auth.entity.User
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono

@Repository
interface UserRepository : ReactiveCrudRepository<User, Long> {
  fun findByUsername(username: String): Mono<User>

  fun existsByUsername(username: String): Mono<Boolean>

  fun existsByEmail(email: String): Mono<Boolean>
}
