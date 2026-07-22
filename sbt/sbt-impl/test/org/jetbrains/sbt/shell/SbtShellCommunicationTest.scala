package org.jetbrains.sbt.shell

import org.jetbrains.sbt.SbtVersion
import org.junit.Assert.assertEquals
import org.junit.Test

class SbtShellCommunicationTest {

  @Test
  def loadFailureIgnoreNewline_Sbt13RequiresNewline(): Unit =
    assertNewlineRequired(SbtVersion("1.3.13"), expected = true)

  @Test
  def loadFailureIgnoreNewline_Sbt14UsesRawInput(): Unit =
    assertNewlineRequired(SbtVersion("1.4.0"), expected = false)

  @Test
  def loadFailureIgnoreNewline_Sbt112UseRawInput(): Unit =
    assertNewlineRequired(SbtVersion("1.12.0"), expected = false)

  @Test
  def loadFailureIgnoreNewline_Sbt2UseRawInput(): Unit =
    assertNewlineRequired(SbtVersion("2.0.0"), expected = false)

  private def assertNewlineRequired(sbtVersion: SbtVersion, expected: Boolean): Unit = {
    val actual = SbtShellCommunication.isLoadFailureIgnoreNewlineRequired(sbtVersion)
    assertEquals(expected, actual)
  }
}
