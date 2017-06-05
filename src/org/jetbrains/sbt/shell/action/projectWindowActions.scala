package org.jetbrains.sbt.shell.action

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.externalSystem.action.ExternalSystemNodeAction
import com.intellij.openapi.externalSystem.model.{ExternalSystemDataKeys, ProjectSystemId}
import com.intellij.openapi.externalSystem.view.ModuleNode
import com.intellij.openapi.project.Project
import org.jetbrains.sbt.SbtUtil
import org.jetbrains.sbt.project.data.{SbtCommandData, SbtNamedKey, SbtSettingData, SbtTaskData}
import org.jetbrains.sbt.shell.SbtShellCommunication
import SbtNodeAction._

import scala.collection.JavaConverters._

abstract class SbtNodeAction[T <: SbtNamedKey](c: Class[T]) extends ExternalSystemNodeAction[T](c) {

  protected def buildCmd(projectId: String, key: String): String

  override def perform(project: Project, projectSystemId: ProjectSystemId, externalData: T, e: AnActionEvent): Unit = {
     val projectScope = for {
       selected <- ExternalSystemDataKeys.SELECTED_NODES.getData(e.getDataContext).asScala.headOption
       groupNode <- Option(selected.getParent)
       moduleNode@(_moduleNode: ModuleNode) <- Option(groupNode.getParent)
       esModuleData <- Option(moduleNode.getData)
       sbtModuleData <- SbtUtil.getSbtModuleData(e.getProject, esModuleData.getId)
     } yield {
       SbtUtil.makeSbtProjectId(sbtModuleData)
     }


    val comms = SbtShellCommunication.forProject(e.getProject)
    val projectPart = projectScope.getOrElse("")
    val keyPart = externalData.name
    comms.command(buildCmd(projectPart, keyPart)) // TODO indicator
  }
}

object SbtNodeAction {
  def scopedKey(project:String, key: String): String = if (project.nonEmpty) s"$project/$key" else key
}

abstract class SbtTaskAction extends SbtNodeAction[SbtTaskData](classOf[SbtTaskData])
abstract class SbtSettingAction extends SbtNodeAction[SbtSettingData](classOf[SbtSettingData])
abstract class SbtCommandAction extends SbtNodeAction[SbtCommandData](classOf[SbtCommandData])

/**
  * Created by jast on 2017-02-13.
  */
class RunTaskAction extends SbtTaskAction {
  override def buildCmd(project: String, key: String): String = scopedKey(project,key)
}

class ShowTaskAction extends SbtTaskAction {
  override def buildCmd(project: String, key: String): String = s"show ${scopedKey(project,key)}"
}

class InspectTaskAction extends SbtTaskAction {
  override def buildCmd(project: String, key: String): String = s"inspect ${scopedKey(project,key)}"
}

class ShowSettingAction extends SbtSettingAction {
  override def buildCmd(project: String, key: String): String = scopedKey(project,key)
}

class InspectSettingAction extends SbtSettingAction {
  override def buildCmd(project: String, key: String): String = s"inspect ${scopedKey(project,key)}"
}

class RunCommandAction extends SbtCommandAction {
  override def buildCmd(project: String, key: String): String = s";project $project; $key"
}

class SbtHelpAction extends SbtNodeAction[SbtNamedKey](classOf[SbtNamedKey]) {
  override def buildCmd(project: String, key: String): String = s"help $key"
}
