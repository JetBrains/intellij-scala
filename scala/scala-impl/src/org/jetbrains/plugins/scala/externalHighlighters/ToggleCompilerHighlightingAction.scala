package org.jetbrains.plugins.scala.externalHighlighters

import com.intellij.openapi.actionSystem.{AnActionEvent, ToggleAction}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

class ToggleCompilerHighlightingAction extends ToggleAction(
  ScalaBundle.message("scala.project.settings.form.compiler.highlighting"),
  ScalaBundle.message("scala.project.settings.form.compiler.highlighting.tooltip"),
  /* icon = */ null
) {
  override def isSelected(e: AnActionEvent): Boolean =
    ScalaProjectSettings.getInstance(e.getProject).isCompilerHighlighting

  override def setSelected(e: AnActionEvent, state: Boolean): Unit =
    ScalaProjectSettings.getInstance(e.getProject).setCompilerHighlighting(state)
}