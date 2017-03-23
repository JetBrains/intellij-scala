package org.jetbrains.sbt.shell.action

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.externalSystem.action.ExternalSystemAction
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.sbt.project.SbtProjectSystem
import org.jetbrains.sbt.settings.SbtSystemSettings
import org.jetbrains.sbt.shell.SbtProcessManager

/**
  * Created by jast on 2016-11-04.
  */
class SbtShellStartAction extends ExternalSystemAction {

  setText("Run SBT Shell")

  override def update(e: AnActionEvent): Unit = {
    super.update(e)
    e.getPresentation.setIcon(Icons.SCALA_CONSOLE)
  }

  override def actionPerformed(event: AnActionEvent): Unit = {
    SbtProcessManager.forProject(event.getProject).openShellRunner(focus = true)
  }

  // hide for non-sbt project toolwindows
  override def isVisible(e: AnActionEvent): Boolean =
    super.isVisible(e) &&
    (SbtProjectSystem.Id == getSystemId(e) ||
      ExternalSystemDataKeys.VIEW.getData(e.getDataContext) == null &&
      !SbtSystemSettings.getInstance(e.getProject).getLinkedProjectsSettings.isEmpty)

  override def isEnabled(e: AnActionEvent): Boolean = hasProject(e)
}
