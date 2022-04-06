package org.jetbrains.sbt.shell.action

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.externalSystem.action.ExternalSystemNodeAction
import com.intellij.openapi.externalSystem.model.{ExternalSystemDataKeys, ProjectSystemId}
import com.intellij.openapi.externalSystem.view.ModuleNode
import com.intellij.openapi.project.Project
import org.jetbrains.sbt.{SbtBundle, SbtUtil}
import org.jetbrains.sbt.project.data.{SbtCommandData, SbtNamedKey, SbtSettingData, SbtTaskData}
import org.jetbrains.sbt.shell.SbtShellCommunication
import SbtNodeAction._
import org.jetbrains.annotations.NonNls

import scala.jdk.CollectionConverters._

abstract class SbtNodeAction[T <: SbtNamedKey](c: Class[T]) extends ExternalSystemNodeAction[T](c) {

  @NonNls protected def buildCmd(@NonNls projectId: String, @NonNls key: String): String

  override def perform(project: Project, projectSystemId: ProjectSystemId, externalData: T, e: AnActionEvent): Unit = {
     //noinspection ScalaUnusedSymbol (unused `n` is necessary for compile to succeed)
     val projectScope = for {
       selected <- ExternalSystemDataKeys.SELECTED_NODES.getData(e.getDataContext).asScala.headOption
       groupNode <- Option(selected.getParent)
       moduleNode@(_n: ModuleNode) <- Option(groupNode.getParent)
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
  @NonNls def scopedKey(@NonNls project:String, @NonNls key: String): String = if (project.nonEmpty) s"$project/$key" else key
}

abstract class SbtTaskAction extends SbtNodeAction[SbtTaskData](classOf[SbtTaskData])
abstract class SbtSettingAction extends SbtNodeAction[SbtSettingData](classOf[SbtSettingData])
abstract class SbtCommandAction extends SbtNodeAction[SbtCommandData](classOf[SbtCommandData])

/**
  * Created by jast on 2017-02-13.
  */
class RunTaskAction extends SbtTaskAction {
  setText(SbtBundle.message("sbt.shell.action.run.task"))
  setDescription(SbtBundle.message("sbt.shell.action.run.task.description"))
  override def buildCmd(project: String, key: String): String = scopedKey(project,key)
}

class ShowTaskAction extends SbtTaskAction {
  setText(SbtBundle.message("sbt.shell.action.show.task"))
  setDescription(SbtBundle.message("sbt.shell.action.show.task.description"))
  override def buildCmd(project: String, key: String): String = s"show ${scopedKey(project,key)}"
}

class InspectTaskAction extends SbtTaskAction {
  setText(SbtBundle.message("sbt.shell.action.inspect.task"))
  setDescription(SbtBundle.message("sbt.shell.action.inspect.task.description"))
  override def buildCmd(project: String, key: String): String = s"inspect ${scopedKey(project,key)}"
}

class ShowSettingAction extends SbtSettingAction {
  setText(SbtBundle.message("sbt.shell.action.show.setting"))
  setDescription(SbtBundle.message("sbt.shell.action.show.setting.description"))
  override def buildCmd(project: String, key: String): String = scopedKey(project,key)
}

class InspectSettingAction extends SbtSettingAction {
  setText(SbtBundle.message("sbt.shell.action.inspect.setting"))
  setDescription(SbtBundle.message("sbt.shell.action.inspect.setting.description"))
  override def buildCmd(project: String, key: String): String = s"inspect ${scopedKey(project,key)}"
}

class RunCommandAction extends SbtCommandAction {
  setText(SbtBundle.message("sbt.shell.action.run.command"))
  setDescription(SbtBundle.message("sbt.shell.action.run.command.description"))
  override def buildCmd(project: String, key: String): String = s";project $project; $key"
}

class SbtHelpAction extends SbtNodeAction[SbtNamedKey](classOf[SbtNamedKey]) {
  setText(SbtBundle.message("sbt.shell.action.help"))
  setDescription(SbtBundle.message("sbt.shell.action.help.description"))
  override def buildCmd(project: String, key: String): String = s"help $key"
}
