package org.jetbrains.sbt.shell.action

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.externalSystem.action.{ExternalSystemAction, ExternalSystemNodeAction}
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.project.Project
import org.jetbrains.sbt.project.data.{SbtCommandData, SbtNamedKey, SbtSettingData, SbtTaskData}
import org.jetbrains.sbt.shell.SbtShellCommunication

abstract class SbtNodeAction[T](c: Class[T]) extends ExternalSystemNodeAction[T](c) {

  protected def buildCmd(data: T): String

  override def perform(project: Project, projectSystemId: ProjectSystemId, externalData: T, e: AnActionEvent): Unit = {
    val comms = SbtShellCommunication.forProject(e.getProject)
    comms.command(buildCmd(externalData)) // TODO indicator
  }
}

abstract class SbtTaskAction extends SbtNodeAction[SbtTaskData](classOf[SbtTaskData])
abstract class SbtSettingAction extends SbtNodeAction[SbtSettingData](classOf[SbtSettingData])
abstract class SbtCommandAction extends SbtNodeAction[SbtCommandData](classOf[SbtCommandData])

/**
  * Created by jast on 2017-02-13.
  */
class RunTaskAction extends SbtTaskAction {
  override def buildCmd(data: SbtTaskData): String = data.name
}

class ShowTaskAction extends SbtTaskAction {
  override def buildCmd(data: SbtTaskData): String = s"show ${data.name}"
}

class InspectTaskAction extends SbtTaskAction {
  override def buildCmd(data: SbtTaskData): String = s"inspect ${data.name}"
}

class ShowSettingAction extends SbtSettingAction {
  override def buildCmd(data: SbtSettingData): String = data.name
}

class InspectSettingAction extends SbtSettingAction {
  override def buildCmd(data: SbtSettingData): String = s"inspect ${data.name}"
}

class RunCommandAction extends SbtCommandAction {
  override def buildCmd(data: SbtCommandData): String = data.name
}

class SbtHelpAction extends SbtNodeAction[SbtNamedKey](classOf[SbtNamedKey]) {
  override def buildCmd(data: SbtNamedKey): String = s"help ${data.name}"
}
