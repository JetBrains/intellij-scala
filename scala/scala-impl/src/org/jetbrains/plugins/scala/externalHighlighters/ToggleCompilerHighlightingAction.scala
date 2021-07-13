package org.jetbrains.plugins.scala.externalHighlighters

import com.intellij.openapi.actionSystem.{AnActionEvent, ToggleAction}
import org.jetbrains.plugins.scala.{ScalaBundle, isInternalMode}
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

class ToggleCompilerHighlightingScala2Action extends ToggleAction(
  ScalaBundle.message("scala.project.settings.form.compiler.highlighting.scala2"),
  ScalaBundle.message("scala.project.settings.form.compiler.highlighting.tooltip"),
  /* icon = */ null
) {
  override def update(e: AnActionEvent): Unit = {
    val text = e.getPresentation.getText
    if (!isInternalMode && text.endsWith(" 2")) {
      e.getPresentation.setText(text.dropRight(2))
    }
  }

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
  override def update(e: AnActionEvent): Unit =
    e.getPresentation.setVisible(isInternalMode) // Accessible vis Find Action even in normal mode

  override def isSelected(e: AnActionEvent): Boolean =
    ScalaProjectSettings.getInstance(e.getProject).isCompilerHighlightingScala3

  override def setSelected(e: AnActionEvent, state: Boolean): Unit =
    ScalaProjectSettings.getInstance(e.getProject).setCompilerHighlightingScala3(state)
}
