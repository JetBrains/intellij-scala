package org.jetbrains.sbt.project

import java.util

import com.intellij.ide.projectView.PresentationData
import com.intellij.openapi.externalSystem.model.{DataNode, Key, ProjectSystemId}
import com.intellij.openapi.externalSystem.view.{ExternalProjectsView, ExternalSystemNode, ExternalSystemViewContributor}
import com.intellij.util.containers.MultiMap
import org.jetbrains.sbt.project.data.{SbtSettingData, SbtTaskData}

import scala.collection.JavaConverters._

/**
  * Created by jast on 2017-02-07.
  */
class SbtViewContributor extends ExternalSystemViewContributor {

  val keys: List[Key[_]] = List(SbtTaskData.Key, SbtSettingData.Key)

  override def getSystemId: ProjectSystemId = SbtProjectSystem.Id

  override def getKeys: util.List[Key[_]] = keys.asJava

  override def createNodes(externalProjectsView: ExternalProjectsView,
                           dataNodes: MultiMap[Key[_], DataNode[_]]): util.List[ExternalSystemNode[_]] = {

    val taskNodes = dataNodes.get(SbtTaskData.Key).asScala
    val settingNodes = dataNodes.get(SbtSettingData.Key).asScala

    val taskViewNodes = taskNodes.map { dataNode =>
      val typedNode = dataNode.asInstanceOf[DataNode[SbtTaskData]]
      new SbtTaskViewNode(externalProjectsView, typedNode)
    }

    val settingViewNodes = settingNodes.map { dataNode =>
      val typedNode = dataNode.asInstanceOf[DataNode[SbtSettingData]]
      new SbtSettingViewNode(externalProjectsView, typedNode)
    }

    val tasksNode = new SbtTasksNode(externalProjectsView)
    tasksNode.addAll(taskViewNodes.asJavaCollection)
    val settingsNode = new SbtSettingsNode(externalProjectsView)
    settingsNode.addAll(settingViewNodes.asJavaCollection)

    val result = new util.ArrayList[ExternalSystemNode[_]](2)
    result.add(settingsNode)
    result.add(tasksNode)

    result
  }

}

// dummy objects to satisfy compiler
case object SbtTasks
case object SbtSettings

class SbtTasksNode(view: ExternalProjectsView) extends ExternalSystemNode[SbtTasks.type](view, null) {
  override def update(presentation: PresentationData): Unit = {
    super.update(presentation)
    // presentation.setIcon(sbtIcon) TODO
    setNameAndTooltip("SBT Tasks", "SBT tasks defined in project")
  }
}

class SbtSettingsNode(view: ExternalProjectsView) extends ExternalSystemNode[SbtSettings.type](view, null) {
  override def update(presentation: PresentationData): Unit = {
    super.update(presentation)
    // presentation.setIcon(sbtIcon) TODO
    setNameAndTooltip("SBT Settings", "SBT settings defined in project")
  }
}

class SbtTaskViewNode(view: ExternalProjectsView, dataNode: DataNode[SbtTaskData])
  extends ExternalSystemNode[SbtTaskData](view, null, dataNode) {

  override def update(presentation: PresentationData): Unit = {
    super.update(presentation)
    // presentation.setIcon(sbtIcon) TODO

    val data = dataNode.getData
    setNameAndTooltip(data.label, data.description)
  }
}

class SbtSettingViewNode(view: ExternalProjectsView, dataNode: DataNode[SbtSettingData])
  extends ExternalSystemNode[SbtSettingData](view, null, dataNode) {

  override def update(presentation: PresentationData): Unit = {
    super.update(presentation)
    // presentation.setIcon(sbtIcon) TODO

    val data = dataNode.getData
    setNameAndTooltip(data.label, data.description)
  }
}