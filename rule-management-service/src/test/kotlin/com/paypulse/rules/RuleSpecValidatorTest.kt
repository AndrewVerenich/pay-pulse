package com.paypulse.rules

import com.fasterxml.jackson.databind.ObjectMapper
import com.paypulse.rules.application.RuleSpecValidationException
import com.paypulse.rules.application.DefaultRuleSpecValidator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class RuleSpecValidatorTest {

  private val validator = DefaultRuleSpecValidator(ObjectMapper())

  private val validSpec = """
    {"maxAmount":10000,"velocityWindowMs":3600000,"velocityMaxCount":50,
     "structuringThreshold":9900,"structuringWindowHours":24,"structuringMinPayments":3}
  """.trimIndent()

  @Test
  fun `accepts a well-formed spec`() {
    val node = validator.validate(validSpec)
    assertEquals(10000, node.path("maxAmount").asInt())
  }

  @Test
  fun `rejects malformed json`() {
    assertThrows<RuleSpecValidationException> { validator.validate("{not json") }
  }

  @Test
  fun `rejects missing required field`() {
    assertThrows<RuleSpecValidationException> {
      validator.validate("""{"maxAmount":10000}""")
    }
  }

  @Test
  fun `rejects out-of-range velocity window`() {
    val bad = validSpec.replace("\"velocityWindowMs\":3600000", "\"velocityWindowMs\":10")
    assertThrows<RuleSpecValidationException> { validator.validate(bad) }
  }

  @Test
  fun `rejects unknown additional property`() {
    val bad = validSpec.dropLast(1) + ",\"bogus\":1}"
    assertThrows<RuleSpecValidationException> { validator.validate(bad) }
  }
}
