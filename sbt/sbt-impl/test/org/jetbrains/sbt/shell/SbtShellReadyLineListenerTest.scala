package org.jetbrains.sbt.shell

import org.jetbrains.sbt.shell.communication.SbtShellModeProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

import scala.collection.mutable.ArrayBuffer

@RunWith(classOf[JUnit4])
class SbtShellReadyLineListenerTest:
  import SbtShellReadyLineListenerTest.*
  import StateChange.*

  // On Windows, the PTY was observed to follow the old sbt shell prompt with empty lines
  @Test
  def oldShellPromptFollowedByEmptyLinesStaysReady(): Unit =
    assertStateChanges(OldShell, Seq("[IJ]>", "\r\n\r\n\r\n\r\n\r\n"), Seq(Ready))

  @Test
  def oldShellPromptFollowedByEmptyLinesInTheSameChunkStaysReady(): Unit =
    assertStateChanges(OldShell, Seq("[IJ]>\r\n\r\n\r\n"), Seq(Ready))

  @Test
  def newShellPromptFollowedByEmptyLinesStaysReady(): Unit =
    assertStateChanges(NewShell, Seq("sbt:mock> ", "\r\n\r\n"), Seq(Ready))

  @Test
  def oldShellCommandAfterPromptWithEmptyLinesIsWorkingUntilNextPrompt(): Unit =
    assertStateChanges(
      OldShell,
      Seq("[IJ]>", "\r\n\r\n", " compile\r\n", "\r\n", "[info] done compiling\r\n", "[IJ]>"),
      Seq(Ready, Working, Ready),
    )

  private def assertStateChanges(
    shellModeProvider: SbtShellModeProvider,
    outputChunks: Seq[String],
    expectedStateChanges: Seq[StateChange],
  ): Unit =
    val stateChanges = ArrayBuffer.empty[StateChange]
    def record(stateChange: StateChange): Unit =
      stateChanges += stateChange

    val listener = SbtShellReadyLineListener(
      "test",
      whenReady = record(Ready),
      whenWorking = record(Working),
      shellModeProvider,
    )
    outputChunks.foreach(chunk => listener.processCompleteLines(chunk))

    assertEquals(expectedStateChanges, stateChanges.toSeq)

private object SbtShellReadyLineListenerTest:

  private enum StateChange:
    case Ready, Working

  private object OldShell extends SbtShellModeProvider:
    override def isNewShell: Boolean = false

  private object NewShell extends SbtShellModeProvider:
    override def isNewShell: Boolean = true
