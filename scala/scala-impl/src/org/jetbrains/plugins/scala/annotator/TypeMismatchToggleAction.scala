package org.jetbrains.plugins.scala.annotator

import com.intellij.openapi.actionSystem.{AnActionEvent, ToggleAction}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

class TypeMismatchToggleAction extends ToggleAction(
  ScalaBundle.message("type.mismatch.hints.action.text"),
  ScalaBundle.message("type.mismatch.hints.action.description"),
  /* icon = */ null
) {
  override def isSelected(anActionEvent: AnActionEvent): Boolean =
    settings(anActionEvent).exists(_.isTypeMismatchHints)

  override def setSelected(anActionEvent: AnActionEvent, b: Boolean): Unit = {
    settings(anActionEvent).foreach(_.setTypeMismatchHints(b))
    anActionEvent.getProject.toOption.foreach(TypeMismatchHints.refreshIn)
  }

  private def settings(anActionEvent: AnActionEvent): Option[ScalaProjectSettings] =
    anActionEvent.getProject.toOption.map(ScalaProjectSettings.in)
}