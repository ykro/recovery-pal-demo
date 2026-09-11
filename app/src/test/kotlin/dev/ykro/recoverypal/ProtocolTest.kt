package dev.ykro.recoverypal

import dev.ykro.recoverypal.data.Surgery
import dev.ykro.recoverypal.data.protocolDay
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/** Pins the phase boundaries the skills promise (the agent loads exactly one phase asset per day). */
class ProtocolTest {
  @Test
  fun `day number counts from surgery day and never goes negative`() {
    val surgery = LocalDate.of(2026, 9, 1)
    assertEquals(0, protocolDay(surgery, surgery, null))
    assertEquals(12, protocolDay(surgery, surgery.plusDays(12), null))
    assertEquals(0, protocolDay(surgery, surgery.minusDays(3), null))
    assertEquals(30, protocolDay(surgery, surgery.plusDays(2), simulatedDay = 30))
  }

  @Test
  fun `appendectomy phases split at day 4 and day 15`() {
    val s = Surgery.APPENDECTOMY
    assertEquals("assets/phase-1-days-0-3.md", s.phaseFor(3).assetFile)
    assertEquals("assets/phase-2-days-4-14.md", s.phaseFor(4).assetFile)
    assertEquals("assets/phase-2-days-4-14.md", s.phaseFor(14).assetFile)
    assertEquals("assets/phase-3-days-15-42.md", s.phaseFor(15).assetFile)
    assertEquals(3, s.phaseFor(200).number)
  }

  @Test
  fun `ankle protocols split at their own boundaries`() {
    assertEquals(1, Surgery.ANKLE_CONSERVATIVE.phaseFor(21).number)
    assertEquals(2, Surgery.ANKLE_CONSERVATIVE.phaseFor(22).number)
    assertEquals(3, Surgery.ANKLE_CONSERVATIVE.phaseFor(43).number)
    assertEquals(1, Surgery.ANKLE_ORIF.phaseFor(14).number)
    assertEquals(2, Surgery.ANKLE_ORIF.phaseFor(15).number)
    assertEquals(2, Surgery.ANKLE_ORIF.phaseFor(42).number)
    assertEquals(3, Surgery.ANKLE_ORIF.phaseFor(43).number)
  }

  @Test
  fun `the demo day 12 lands in ORIF phase 1 and appendectomy phase 2`() {
    assertEquals("assets/phase-1-days-0-14.md", Surgery.ANKLE_ORIF.phaseFor(12).assetFile)
    assertEquals("assets/phase-2-days-4-14.md", Surgery.APPENDECTOMY.phaseFor(12).assetFile)
  }
}
