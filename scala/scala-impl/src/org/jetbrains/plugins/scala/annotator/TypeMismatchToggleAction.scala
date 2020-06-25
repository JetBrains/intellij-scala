package org.jetbrains.plugins.scala.annotator

import com.intellij.openapi.actionSystem.{AnActionEvent, ToggleAction}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

class TypeMismatchToggleAction extends ToggleAction(
  ScalaBundle.message("type.mismatch.hints.action.text"),
  ScalaBundle.message("type.mismatch.hints.action.description"),
  /* icon = */ null
) {
  override def isSelected(anActionEvent: AnActionEvent): Boolean =
    ScalaProjectSettings.in(anActionEvent.getProject).isTypeMismatchHints

  override def setSelected(anActionEvent: AnActionEvent, b: Boolean): Unit = {
    ScalaProjectSettings.in(anActionEvent.getProject).setTypeMismatchHints(b)
    TypeMismatchHints.refreshIn(anActionEvent.getProject)
  }
}