package org.jetbrains.plugins.scala.compiler.highlighting

import com.intellij.openapi.actionSystem.{ActionUpdateThread, AnActionEvent, ToggleAction}
import org.jetbrains.plugins.scala.compiler.CompilerIntegrationBundle
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

sealed abstract class ToggleCompilerHighlightingAction(scalaVersion: Either["2", "3"])
  extends ToggleAction(
    CompilerIntegrationBundle.message("scala.project.settings.form.compiler.highlighting.scala.version", scalaVersion.fold(identity, identity)),
    CompilerIntegrationBundle.message("scala.project.settings.form.compiler.highlighting.tooltip"),
    null
  ) {
  final override def update(e: AnActionEvent): Unit = {
    val project = e.getProject
    if (project ne null) {
      (scalaVersion, project.hasScala2, project.hasScala3) match {
        case (Left("2"), true, false) =>
          e.getPresentation.setText(CompilerIntegrationBundle.message("scala.project.settings.form.compiler.highlighting.scala"))
        case (Right("3"), true, false) =>
          e.getPresentation.setEnabled(false)
          e.getPresentation.setVisible(false)
        case (Left("2"), false, true) =>
          e.getPresentation.setEnabled(false)
          e.getPresentation.setVisible(false)
        case (Right("3"), false, true) =>
          e.getPresentation.setText(CompilerIntegrationBundle.message("scala.project.settings.form.compiler.highlighting.scala"))
        case (_, true, true) =>
          // Nothing to do. Both Scala 2 and Scala 3 are present in the project and we will use the regular names for
          // the actions.
        case (_, false, false) =>
          e.getPresentation.setEnabled(false)
          e.getPresentation.setVisible(false)
      }
    }
    super.update(e)
  }

  override def getActionUpdateThread: ActionUpdateThread = ActionUpdateThread.EDT
}

class ToggleCompilerHighlightingScala2Action extends ToggleCompilerHighlightingAction(Left("2")) {
  override def isSelected(e: AnActionEvent): Boolean = {
    ScalaProjectSettings.getInstance(e.getProject).isCompilerHighlightingScala2
  }

  override def setSelected(e: AnActionEvent, state: Boolean): Unit =
    ScalaProjectSettings.getInstance(e.getProject).setCompilerHighlightingScala2(state)
}

// Is effectively a per-project Registry key, accessible vis Find Action.
class ToggleCompilerHighlightingScala3Action extends ToggleCompilerHighlightingAction(Right("3")) {
  override def isSelected(e: AnActionEvent): Boolean =
    ScalaProjectSettings.getInstance(e.getProject).isCompilerHighlightingScala3

  override def setSelected(e: AnActionEvent, state: Boolean): Unit =
    ScalaProjectSettings.getInstance(e.getProject).setCompilerHighlightingScala3(state)
}
