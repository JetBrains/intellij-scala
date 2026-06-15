package org.jetbrains.sbt.shell

import org.jetbrains.sbt.SbtVersion
import org.junit.Assert.assertEquals
import org.junit.Test

class SbtShellCommunicationTest {

  @Test
  def loadFailureIgnoreCommand_OldSbtOldShellUsesNewline(): Unit =
    assertLoadFailureIgnoreCommand(
      sbtVersion = SbtVersion("1.3.13"),
      isNewShell = false,
      isLinux = false,
      expected = "i\n",
    )

  @Test
  def loadFailureIgnoreCommand_OldSbtNewShellUsesNewline(): Unit =
    assertLoadFailureIgnoreCommand(
      sbtVersion = SbtVersion("1.3.13"),
      isNewShell = true,
      isLinux = false,
      expected = "i\n",
    )

  @Test
  def loadFailureIgnoreCommand_NewSbtLinuxOldShellUsesNewline(): Unit =
    assertLoadFailureIgnoreCommand(
      sbtVersion = SbtVersion("1.12.0"),
      isNewShell = false,
      isLinux = true,
      expected = "i\n",
    )

  @Test
  def loadFailureIgnoreCommand_NewSbtLinuxNewShellUsesRawInput(): Unit =
    assertLoadFailureIgnoreCommand(
      sbtVersion = SbtVersion("1.12.0"),
      isNewShell = true,
      isLinux = true,
      expected = "i",
    )

  @Test
  def loadFailureIgnoreCommand_NewSbtMacOldShellUsesRawInput(): Unit =
    assertLoadFailureIgnoreCommand(
      sbtVersion = SbtVersion("1.12.0"),
      isNewShell = false,
      isLinux = false,
      expected = "i",
    )

  @Test
  def loadFailureIgnoreCommand_NewSbtWindowsOldShellUsesRawInput(): Unit =
    assertLoadFailureIgnoreCommand(
      sbtVersion = SbtVersion("1.12.0"),
      isNewShell = false,
      isLinux = false,
      expected = "i",
    )

  private def assertLoadFailureIgnoreCommand(
    sbtVersion: SbtVersion,
    isNewShell: Boolean,
    isLinux: Boolean,
    expected: String,
  ): Unit = {
    val actual = SbtShellCommunication.loadFailureIgnoreCommand(
      sbtVersion,
      isNewShell,
      isLinux,
      lineSeparator = "\n",
    )

    assertEquals(expected, actual)
  }
}
