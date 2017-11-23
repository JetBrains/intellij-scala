package org.jetbrains.sbt.project

import java.util

import com.intellij.ide.projectView.PresentationData
import com.intellij.openapi.externalSystem.model.{DataNode, Key, ProjectSystemId}
import com.intellij.openapi.externalSystem.view.{ExternalProjectsView, ExternalSystemNode, ExternalSystemViewContributor}
import com.intellij.util.containers.MultiMap
import org.jetbrains.sbt.project.SbtViewContributor._
import org.jetbrains.sbt.project.data.{SbtCommandData, SbtSettingData, SbtTaskData}

import scala.collection.JavaConverters._

/**
  * Created by jast on 2017-02-07.
  */
class SbtViewContributor extends ExternalSystemViewContributor {

  private val keys: List[Key[_]] = List(SbtTaskData.Key, SbtSettingData.Key, SbtCommandData.Key)

  override def getSystemId: ProjectSystemId = SbtProjectSystem.Id

  override def getKeys: util.List[Key[_]] = keys.asJava

  override def createNodes(externalProjectsView: ExternalProjectsView,
                           dataNodes: MultiMap[Key[_], DataNode[_]]): util.List[ExternalSystemNode[_]] = {

    val taskNodes = dataNodes.get(SbtTaskData.Key).asScala
    val settingNodes = dataNodes.get(SbtSettingData.Key).asScala
    val commandNodes = dataNodes.get(SbtCommandData.Key).asScala

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

    List[ExternalSystemNode[_]](settingsNode, tasksNode, commandsNode).asJava
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
      setNameAndTooltip(getName, "sbt tasks defined in this project")
      // presentation.setIcon(sbtIcon) TODO
    }

    override def getName: String = "sbt tasks"
  }

  class SbtSettingsGroupNode(view: ExternalProjectsView) extends ExternalSystemNode(view, null, new GroupDataNode(SbtSettings)) {
    override def update(presentation: PresentationData): Unit = {
      super.update(presentation)
      setNameAndTooltip(getName, "sbt settings defined in this project")
      // presentation.setIcon(sbtIcon) TODO
    }

    override def getName: String = "sbt settings"
  }

  class SbtCommandsGroupNode(view: ExternalProjectsView) extends ExternalSystemNode(view, null, new GroupDataNode(SbtCommands)) {
    override def update(presentation: PresentationData): Unit = {
      super.update(presentation)
      setNameAndTooltip(getName, "Named sbt commands defined in this project")
      // presentation.setIcon(sbtIcon) TODO
    }

    override def getName: String = "sbt commands"
  }

  class SbtTaskViewNode(view: ExternalProjectsView, dataNode: DataNode[SbtTaskData])
    extends ExternalSystemNode[SbtTaskData](view, null, dataNode) {

    override def update(presentation: PresentationData): Unit = {
      super.update(presentation)
      // presentation.setIcon(sbtIcon) TODOsetNameAndTooltip(dataNode.getData.name, dataNode.getData.description)
      setNameAndTooltip(dataNode.getData.name, dataNode.getData.description)
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
      setNameAndTooltip(dataNode.getData.name, dataNode.getData.description)
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
      data.help.map { case (name, description) =>
        s"$name : $description"
      }.mkString("\n")
    }

    override def update(presentation: PresentationData): Unit = {
      super.update(presentation)
      // presentation.setIcon(sbtIcon) TODO
      setNameAndTooltip(getName, helpString)
    }

    override def getName: String = dataNode.getData.name
    override def getMenuId: String = "Scala.Sbt.CommandMenu"
    override def getActionId: String = "Scala.Sbt.RunCommand"
    override def isAlwaysLeaf: Boolean = true
  }
}