package com.example.tavi.tools

import org.junit.Assert.*
import org.junit.Test

class ToolArgumentValidatorTest {
    private val schema = """{"type":"object","required":["recipient","body"],"properties":{
        "recipient":{"type":"string"},"body":{"type":"string"},
        "count":{"type":"integer","minimum":1,"maximum":10},
        "mode":{"type":"string","enum":["draft","send"]}},"additionalProperties":false}"""
    private val valid = mapOf("recipient" to "Sam", "body" to "Hello")
    @Test fun validatesTypesAndRequiredFields() {
        assertNull(ToolArgumentValidator.validate(schema, valid))
        assertNotNull(ToolArgumentValidator.validate(schema, valid - "body"))
        assertNotNull(ToolArgumentValidator.validate(schema, valid + ("body" to 4)))
        assertNotNull(ToolArgumentValidator.validate(schema, valid + ("count" to 1.5)))
        assertNotNull(ToolArgumentValidator.validate(schema, valid + ("count" to Double.NaN)))
        assertNotNull(ToolArgumentValidator.validate(schema, valid + ("count" to 11)))
        assertNotNull(ToolArgumentValidator.validate(schema, valid + ("mode" to "delete")))
    }
    @Test fun rejectsInjectedHostFlagsAndUnknownProperties() {
        assertNotNull(ToolArgumentValidator.validate(schema, valid + ("__user_confirmed" to true)))
        assertNotNull(ToolArgumentValidator.validate(schema, valid + ("other" to true)))
    }
}
