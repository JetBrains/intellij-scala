package org.jetbrains.plugins.scala.compiler.highlighting

import com.intellij.openapi.actionSystem.{ActionUpdateThread, AnActionEvent, ToggleAction}
import org.jetbrains.plugins.scala.compiler.CompilerIntegrationBundle
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

class ToggleCompilerHighlightingScala2Action extends ToggleAction(
  CompilerIntegrationBundle.message("scala.project.settings.form.compiler.highlighting.scala2"),
  CompilerIntegrationBundle.message("scala.project.settings.form.compiler.highlighting.tooltip"),
  /* icon = */ null
) {
  override def isSelected(e: AnActionEvent): Boolean =
    ScalaProjectSettings.getInstance(e.getProject).isCompilerHighlightingScala2

  override def setSelected(e: AnActionEvent, state: Boolean): Unit =
    ScalaProjectSettings.getInstance(e.getProject).setCompilerHighlightingScala2(state)

  override def getActionUpdateThread: ActionUpdateThread = ActionUpdateThread.EDT
}

// Is effectively a per-project Registry key, accessible vis Find Action.
class ToggleCompilerHighlightingScala3Action extends ToggleAction(
  CompilerIntegrationBundle.message("scala.project.settings.form.compiler.highlighting.scala3"),
  CompilerIntegrationBundle.message("scala.project.settings.form.compiler.highlighting.tooltip"),
  /* icon = */ null
) {
  override def update(e: AnActionEvent): Unit = {
    e.getPresentation.setVisible(false) // Can still be found explicitly.
    super.update(e)
  }

  override def isSelected(e: AnActionEvent): Boolean =
    ScalaProjectSettings.getInstance(e.getProject).isCompilerHighlightingScala3

  override def setSelected(e: AnActionEvent, state: Boolean): Unit =
    ScalaProjectSettings.getInstance(e.getProject).setCompilerHighlightingScala3(state)

  override def getActionUpdateThread: ActionUpdateThread = ActionUpdateThread.EDT
}
