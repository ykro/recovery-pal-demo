package dev.ykro.recoverypal

import dev.ykro.recoverypal.agent.WoundObservationSchema
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WoundObservationSchemaTest {
  @Test
  fun `parses fenced json and defaults missing fields to UNSURE`() {
    val text = "```json\n{\"rednessAroundIncision\":\"MILD\",\"discharge\":\"NONE\",\"imageQuality\":\"GOOD\",\"freeText\":\"Three small closed port sites.\"}\n```"
    val obs = WoundObservationSchema.parse(text)!!
    assertEquals("MILD", obs.rednessAroundIncision)
    assertEquals("UNSURE", obs.edgesClosed)
    assertEquals("UNSURE", obs.swelling)
  }

  @Test
  fun `rejects prose`() {
    assertNull(WoundObservationSchema.parse("I cannot see a wound."))
  }

  @Test
  fun `schema has no severity or infection field`() {
    val keys = WoundObservationSchema.schema.properties!!.keys
    assertTrue(keys.none { it.contains("infect", ignoreCase = true) || it.contains("sever", ignoreCase = true) })
    assertEquals(6, keys.size)
  }
}
