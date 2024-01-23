package org.jetbrains.plugins.scala.util

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.model.project.{ModuleData, ProjectData}
import com.intellij.openapi.externalSystem.model.{DataNode, ExternalProjectInfo, Key, ProjectKeys, ProjectSystemId}
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.project.Project

import scala.jdk.CollectionConverters._

/**
 * See also [[com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil]]
 */
object ExternalSystemUtil {

  def getProjectData[K](
    projectSystemId: ProjectSystemId,
    project: Project,
    key: Key[K],
  ): Either[String, Iterable[K]] = {
    val nodes = getProjectDataNodes(projectSystemId, project, key)
    nodes.map(_.map(_.getData))
  }

  private def getProjectDataNodes[K](
    projectSystemId: ProjectSystemId,
    project: Project,
    key: Key[K],
  ): Either[String, Iterable[DataNode[K]]] = {
    val dataManager = ProjectDataManager.getInstance()
    val (_, projectDataNode: DataNode[ProjectData]) = getExternalProjectInfoAndData(dataManager, projectSystemId, project) match {
      case Right(value) => value
      case Left(error) =>
        return Left(error)
    }

    val result = findAllNodesEnsuring(dataManager, projectDataNode, key)
    Right(result)
  }

  def getModuleData[K](
    projectSystemId: ProjectSystemId,
    project: Project,
    moduleId: String,
    key: Key[K],
  ): Either[String, Iterable[K]] = {
    val nodes = getModuleDataNodes(projectSystemId, project, moduleId, key)
    nodes.map(_.map(_.getData))
  }

  private def getModuleDataNodes[K](
    projectSystemId: ProjectSystemId,
    project: Project,
    moduleId: String,
    key: Key[K],
  ): Either[String, Iterable[DataNode[K]]] = {
    val dataManager = ProjectDataManager.getInstance()
    val moduleDataNode = getModuleDataNode(dataManager, projectSystemId, project, moduleId)
    moduleDataNode.map(findAllNodesEnsuring(dataManager, _, key))
  }

  def getModuleDataNode(
    projectSystemId: ProjectSystemId,
    project: Project,
    moduleId: String,
  ): Either[String, DataNode[ModuleData]] = {
    val dataManager = ProjectDataManager.getInstance()
    getModuleDataNode(dataManager, projectSystemId, project, moduleId)
  }

  private def getModuleDataNode(
    dataManager: ProjectDataManager,
    projectSystemId: ProjectSystemId,
    project: Project,
    moduleId: String,
  ): Either[String, DataNode[ModuleData]] = {
    val (projectInfo: ExternalProjectInfo, projectDataNode: DataNode[ProjectData]) = getExternalProjectInfoAndData(dataManager, projectSystemId, project) match {
      case Right(value) => value
      case Left(error) =>
        return Left(error)
    }

    val moduleDataNode: DataNode[ModuleData] = ExternalSystemApiUtil.findChild(projectDataNode, ProjectKeys.MODULE, (node: DataNode[ModuleData]) => {
      // seems hacky. but apparently there isn't yet any better way to get the data for selected module?
      node.getData.getId == moduleId
    })
    if (moduleDataNode != null)
      Right(moduleDataNode)
    else
      Left(s"can't find module data node with id `$moduleId` for project $project, $projectInfo")
  }

  private def findAllNodesEnsuring[K](dataManager: ProjectDataManager, parent: DataNode[_], key: Key[K]): Iterable[DataNode[K]] = {
    val dataNodes = ExternalSystemApiUtil.findAll(parent, key).asScala
    dataNodes.foreach(dataManager.ensureTheDataIsReadyToUse)
    dataNodes
  }

  private def getExternalProjectInfoAndData(
    dataManager: ProjectDataManager,
    projectSystemId: ProjectSystemId,
    project: Project,
  ): Either[String, (ExternalProjectInfo, DataNode[ProjectData])] = {
    //TODO: consider using `com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.findProjectInfo` and see how it works?
    val projectInfoOpt1 = Option(dataManager.getExternalProjectData(project, projectSystemId, project.getBasePath))
    val projectInfoOpt2 = projectInfoOpt1.orElse {
      // in tests org.jetbrains.sbt.project.SbtProjectImportingTest `project.getBasePath` doesn't equal to actual external project data
      if (ApplicationManager.getApplication.isUnitTestMode) {
        val externalProjectsData = dataManager.getExternalProjectsData(project, projectSystemId).asScala
        externalProjectsData.find(_.getExternalProjectStructure.getData.getInternalName == project.getName)
      }
      else None
    }

    val projectInfo: ExternalProjectInfo = projectInfoOpt2 match {
      case Some(value) => value
      case _ =>
        return Left(s"can't find $projectSystemId external project data for project $project)")
    }
    val projectDataNode: DataNode[ProjectData] = projectInfo.getExternalProjectStructure
    if (projectDataNode == null) {
      return Left(s"can't find external project structure for project $project, $projectInfo")
    }
    Right((projectInfo, projectDataNode))
  }
}
