package com.paypulse.rules.application

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.JsonSchema
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component

class RuleSpecValidationException(message: String) : RuntimeException(message)

interface RuleSpecValidator {
  fun validate(jsonSpec: String): JsonNode
}

@Component
class DefaultRuleSpecValidator(private val objectMapper: ObjectMapper) : RuleSpecValidator {

  private val schema: JsonSchema by lazy {
    val factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7)
    ClassPathResource("fraud-rule.schema.json").inputStream.use { factory.getSchema(it) }
  }

  override fun validate(jsonSpec: String): JsonNode {
    val node = try {
      objectMapper.readTree(jsonSpec)
    } catch (e: Exception) {
      throw RuleSpecValidationException("json_spec is not valid JSON: ${e.message}")
    }
    val errors = schema.validate(node)
    if (errors.isNotEmpty()) {
      throw RuleSpecValidationException(errors.joinToString("; ") { it.message })
    }
    return node
  }
}
