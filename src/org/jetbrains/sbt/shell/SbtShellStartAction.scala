package org.jetbrains.sbt.shell

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.externalSystem.action.ExternalSystemAction
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.sbt.project.SbtProjectSystem
import org.jetbrains.sbt.settings.SbtSystemSettings

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
    SbtShellRunner.run(event.getProject)
  }

  override def isEnabled(e: AnActionEvent): Boolean =
    true

  // hide for non-sbt project toolwindows
  override def isVisible(e: AnActionEvent): Boolean =
    super.isVisible(e) &&
    (SbtProjectSystem.Id == getSystemId(e) ||
      ExternalSystemDataKeys.VIEW.getData(e.getDataContext) == null &&
      !SbtSystemSettings.getInstance(e.getProject).getLinkedProjectsSettings.isEmpty)
}
