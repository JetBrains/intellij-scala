package org.jetbrains.sbt.project

import com.intellij.ide.projectView.PresentationData
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.{DataNode, Key, ProjectSystemId}
import com.intellij.openapi.externalSystem.view.{ExternalProjectsView, ExternalSystemNode, ExternalSystemViewContributor, ModuleNode}
import com.intellij.util.SmartList
import com.intellij.util.containers.MultiMap
import org.jetbrains.sbt.SbtBundle
import org.jetbrains.sbt.project.SbtViewContributor._
import org.jetbrains.sbt.project.data.{SbtCommandData, SbtSettingData, SbtTaskData}
import org.jetbrains.sbt.project.module.SbtNestedModuleData

import java.util
import scala.jdk.CollectionConverters._

class SbtViewContributor extends ExternalSystemViewContributor {

  private val keys: List[Key[_]] = List(SbtTaskData.Key, SbtSettingData.Key, SbtCommandData.Key, SbtNestedModuleData.key)

  override def getSystemId: ProjectSystemId = SbtProjectSystem.Id

  override def getKeys: util.List[Key[_]] = keys.asJava

  override def createNodes(externalProjectsView: ExternalProjectsView,
                           dataNodes: MultiMap[Key[_], DataNode[_]]): util.List[ExternalSystemNode[_]] = {

    val taskNodes = dataNodes.get(SbtTaskData.Key).asScala
    val settingNodes = dataNodes.get(SbtSettingData.Key).asScala
    val commandNodes = dataNodes.get(SbtCommandData.Key).asScala
    val sbtNestedModuleNodes = dataNodes.get(SbtNestedModuleData.key).asScala

    val taskViewNodes = taskNodes.map { dataNode =>
      val typedNode = dataNode.asInstanceOf[DataNode[SbtTaskData]]
      new SbtTaskViewNode(externalProjectsView, typedNode)
    }

    val settingViewNodes = settingNodes.map { dataNode =>
      val typedNode = dataNode.asInstanceOf[DataNode[SbtSettingData]]
      new SbtSettingViewNode(externalProjectsView, typedNode)
    }

    val commandViewNodes = commandNodes.map { dataNode =>
      val typedNode = dataNode.asInstanceOf[DataNode[SbtCommandData]]
      new SbtCommandViewNode(externalProjectsView, typedNode)
    }

    val tasksNode = new SbtTasksGroupNode(externalProjectsView)
    tasksNode.addAll(taskViewNodes.asJavaCollection)
    val settingsNode = new SbtSettingsGroupNode(externalProjectsView)
    settingsNode.addAll(settingViewNodes.asJavaCollection)
    val commandsNode = new SbtCommandsGroupNode(externalProjectsView)
    commandsNode.addAll(commandViewNodes.asJavaCollection)

    val nestedModuleNodes = sbtNestedModuleNodes.map { node =>
      val moduleDataNode = node.asInstanceOf[DataNode[ModuleData]]
      new ModuleNode(externalProjectsView, moduleDataNode, null, false)
    }.toSeq

    val allNodes = Seq(settingsNode, tasksNode, commandsNode) ++ nestedModuleNodes
    new SmartList[ExternalSystemNode[_]](allNodes: _*)
  }
}

private object SbtViewContributor {

  // data nodes for grouping nodes. These are required for correct processing by external system
  class GroupDataNode[T](data: T) extends DataNode[T](new Key[T](data.getClass.getName, 0), data, null)
  case object SbtTasks
  case object SbtSettings
  case object SbtCommands

  class SbtTasksGroupNode(view: ExternalProjectsView) extends ExternalSystemNode(view, null, new GroupDataNode(SbtTasks)) {
    override def update(presentation: PresentationData): Unit = {
      super.update(presentation)
      setNameAndTooltip(presentation, getName, SbtBundle.message("sbt.tasks.defined.in.this.project"))
      // presentation.setIcon(sbtIcon) TODO
    }

    override def getName: String = SbtBundle.message("sbt.tasks")
  }

  class SbtSettingsGroupNode(view: ExternalProjectsView) extends ExternalSystemNode(view, null, new GroupDataNode(SbtSettings)) {
    override def update(presentation: PresentationData): Unit = {
      super.update(presentation)
      setNameAndTooltip(presentation, getName, SbtBundle.message("sbt.settings.defined.in.this.project"))
      // presentation.setIcon(sbtIcon) TODO
    }

    override def getName: String = SbtBundle.message("sbt.settings")
  }

  class SbtCommandsGroupNode(view: ExternalProjectsView) extends ExternalSystemNode(view, null, new GroupDataNode(SbtCommands)) {
    override def update(presentation: PresentationData): Unit = {
      super.update(presentation)
      setNameAndTooltip(presentation, getName, SbtBundle.message("sbt.named.sbt.commands.defined.in.this.project"))
      // presentation.setIcon(sbtIcon) TODO
    }

    override def getName: String = SbtBundle.message("sbt.commands")
  }

  class SbtTaskViewNode(view: ExternalProjectsView, dataNode: DataNode[SbtTaskData])
    extends ExternalSystemNode[SbtTaskData](view, null, dataNode) {

    override def update(presentation: PresentationData): Unit = {
      super.update(presentation)
      // presentation.setIcon(sbtIcon) TODOsetNameAndTooltip(dataNode.getData.name, dataNode.getData.description)
      setNameAndTooltip(presentation, dataNode.getData.name, dataNode.getData.description)
    }

    override def getName: String = dataNode.getData.name
    override def getMenuId: String = "Scala.Sbt.TaskMenu"
    override def getActionId: String = "Scala.Sbt.RunTask"
    override def isAlwaysLeaf: Boolean = true
  }

  class SbtSettingViewNode(view: ExternalProjectsView, dataNode: DataNode[SbtSettingData])
    extends ExternalSystemNode[SbtSettingData](view, null, dataNode) {

    override def update(presentation: PresentationData): Unit = {
      super.update(presentation)
      // presentation.setIcon(sbtIcon) TODO
      setNameAndTooltip(presentation, dataNode.getData.name, dataNode.getData.description)
    }

    override def getName: String = dataNode.getData.name
    override def getMenuId: String = "Scala.Sbt.SettingMenu"
    override def getActionId: String = "Scala.Sbt.ShowSetting"
    override def isAlwaysLeaf: Boolean = true
  }

  class SbtCommandViewNode(view: ExternalProjectsView, dataNode: DataNode[SbtCommandData])
    extends ExternalSystemNode[SbtCommandData](view, null, dataNode) {
    private lazy val helpString = {
      val data = dataNode.getData()
      data.help.asScala.map { case (name, description) =>
        s"$name : $description"
      }.mkString("\n")
    }

    override def update(presentation: PresentationData): Unit = {
      super.update(presentation)
      // presentation.setIcon(sbtIcon) TODO
      setNameAndTooltip(presentation, getName, helpString)
    }

    override def getName: String = dataNode.getData.name
    override def getMenuId: String = "Scala.Sbt.CommandMenu"
    override def getActionId: String = "Scala.Sbt.RunCommand"
    override def isAlwaysLeaf: Boolean = true
  }
}