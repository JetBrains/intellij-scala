package org.jetbrains.sbt.runner.console

import com.intellij.execution.ui.{ConsoleView, ConsoleViewContentType}
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.EdtInvocationManager
import org.jetbrains.annotations.{Nls, VisibleForTesting}
import org.jetbrains.plugins.scala.extensions.invokeLater
import org.jetbrains.sbt.SbtBundle
import org.jetbrains.sbt.runner.console.inlayHint.InlineActionHintFilter
import org.jetbrains.sbt.shell.SbtShellToolWindowFactory

/**
 * Displays a grey hint in the Run/Debug console when it's waiting for SBT shell to become ready:
 * ```text
 * Waiting for sbt shell to become ready... (Open sbt shell)
 * ```
 *
 * ## High-level implementation overview:
 * 1. The waiting text itself is printed as normal console output with a custom grey italic content type.
 * 2. The clickable "Open sbt shell" part is added separately as a console inlay:
 *    a message filter recognizes the freshly printed waiting line and returns an `InlayProvider`
 *    whose renderer creates the rounded action hint.
 */
private[runner] object SbtShellWaitingForReadyHint {
  @Nls
  @VisibleForTesting
  val HintText: String = SbtBundle.message("waiting.for.sbt.shell.to.become.ready")

  @Nls
  @VisibleForTesting
  val OpenShellText: String = SbtBundle.message("open.sbt.shell")

  private val GrayItalicStatusContentType: ConsoleViewContentType =
    new ConsoleViewContentType(
      "SBT_SHELL_WAITING_HINT_GRAY_ITALIC_STATUS",
      SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES.toTextAttributes,
    )

  def print(consoleView: ConsoleView): Unit = EdtInvocationManager.invokeAndWaitIfNeeded { () =>
    installInlineActionHintFilterIfNeeded(consoleView)
    printGrayItalicStatusWithInlineActionHint(consoleView)
  }

  private def printGrayItalicStatusWithInlineActionHint(consoleView: ConsoleView): Unit = {
    consoleView.print(HintText, GrayItalicStatusContentType)
    consoleView.print(System.lineSeparator(), GrayItalicStatusContentType)
  }

  private val InstalledProperty: String =
    "SbtShellWaitingForReadyHint.inlineActionHintFilterInstalled"

  @RequiresEdt
  private def installInlineActionHintFilterIfNeeded(consoleView: ConsoleView): Unit = {
    val component = consoleView.getComponent

    if (component.getClientProperty(InstalledProperty) != java.lang.Boolean.TRUE) {
      installInlineActionHintFilter(consoleView)

      // Console filters accumulate on the console instance, so mark the component after installing our filter once.
      component.putClientProperty(InstalledProperty, java.lang.Boolean.TRUE)
    }
  }

  @RequiresEdt
  private def installInlineActionHintFilter(consoleView: ConsoleView): Unit = {
    val filter = new InlineActionHintFilter(
      markerLineText = HintText,
      inlayHintActionText = OpenShellText,
      inlayHintAction = activateSbtShell
    )
    consoleView.addMessageFilter(filter)
  }

  private def activateSbtShell(project: Project): Unit = invokeLater {
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(SbtShellToolWindowFactory.ID)
    if (toolWindow != null) {
      toolWindow.activate(null, true)
    }
  }
}