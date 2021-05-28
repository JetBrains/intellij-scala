package org.jetbrains.plugins.scala.externalHighlighters

import com.intellij.openapi.actionSystem.{AnActionEvent, ToggleAction}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

class ToggleCompilerHighlightingScala2Action extends ToggleAction(
  ScalaBundle.message("scala.project.settings.form.compiler.highlighting.scala2"),
  ScalaBundle.message("scala.project.settings.form.compiler.highlighting.tooltip"),
  /* icon = */ null
) {
  override def isSelected(e: AnActionEvent): Boolean =
    ScalaProjectSettings.getInstance(e.getProject).isCompilerHighlightingScala2

  override def setSelected(e: AnActionEvent, state: Boolean): Unit =
    ScalaProjectSettings.getInstance(e.getProject).setCompilerHighlightingScala2(state)
}

class ToggleCompilerHighlightingScala3Action extends ToggleAction(
  ScalaBundle.message("scala.project.settings.form.compiler.highlighting.scala3"),
  ScalaBundle.message("scala.project.settings.form.compiler.highlighting.tooltip"),
  /* icon = */ null
) {
  override def isSelected(e: AnActionEvent): Boolean =
    ScalaProjectSettings.getInstance(e.getProject).isCompilerHighlightingScala3

  override def setSelected(e: AnActionEvent, state: Boolean): Unit =
    ScalaProjectSettings.getInstance(e.getProject).setCompilerHighlightingScala3(state)
}
