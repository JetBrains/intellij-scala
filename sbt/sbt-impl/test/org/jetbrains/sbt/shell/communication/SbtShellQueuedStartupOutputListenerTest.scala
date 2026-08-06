package org.jetbrains.sbt.shell.communication

import org.junit.Assert.assertEquals
import org.junit.Test

import scala.collection.mutable.ArrayBuffer

class SbtShellQueuedStartupOutputListenerTest {

  @Test
  def startupOutputIsForwardedAndOldShellReadyPromptIsIgnored(): Unit = {
    val listener = new CollectingStartupOutputListener(OldShellModeProvider)

    listener.processCompleteLines("[debug] started (old-shell)\n")
    listener.processCompleteLines("[debug] starting command loop (old-shell)\n")
    listener.processCompleteLines("[IJ]>")

    assertEquals(
      Seq(
        "[debug] started (old-shell)",
        "[debug] starting command loop (old-shell)",
      ),
      listener.outputLines,
    )
  }

  @Test
  def startupOutputIsForwardedAndNewShellReadyPromptIsIgnored(): Unit = {
    val listener = new CollectingStartupOutputListener(NewShellModeProvider)

    listener.processCompleteLines("[debug] started (new-shell)\n")
    listener.processCompleteLines("sbt:mock>")

    assertEquals(
      Seq("[debug] started (new-shell)"),
      listener.outputLines,
    )
  }

  @Test
  def projectLoadingFailurePromptIsForwardedAsStartupOutput(): Unit = {
    val listener = new CollectingStartupOutputListener(OldShellModeProvider)
    val prompt = "Project loading failed: (r)etry, (q)uit, (l)ast, or (i)gnore?"

    listener.processCompleteLines(prompt)

    assertEquals(Seq(prompt), listener.outputLines)
  }

  private final class CollectingStartupOutputListener(shellModeProvider: SbtShellModeProvider) {
    private val lines = ArrayBuffer.empty[String]
    private val listener = new SbtShellQueuedStartupOutputListener(shellModeProvider, lines += _)

    def processCompleteLines(text: String): Unit =
      listener.processCompleteLines(text)

    def outputLines: Seq[String] =
      lines.toSeq
  }

  private object OldShellModeProvider extends SbtShellModeProvider {
    override def isNewShell: Boolean = false
  }

  private object NewShellModeProvider extends SbtShellModeProvider {
    override def isNewShell: Boolean = true
  }
}
