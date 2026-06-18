package com.paypulse.participant.risk

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories

@SpringBootApplication
@EnableR2dbcRepositories
class ParticipantRiskScoringApplication

fun main(args: Array<String>) {
  runApplication<ParticipantRiskScoringApplication>(*args)
}
