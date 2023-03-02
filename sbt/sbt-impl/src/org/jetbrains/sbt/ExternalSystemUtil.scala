package org.jetbrains.sbt

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.model.project.{ModuleData, ProjectData}
import com.intellij.openapi.externalSystem.model.{DataNode, ExternalProjectInfo, Key, ProjectKeys, ProjectSystemId}
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.project.Project

import scala.jdk.CollectionConverters._

object ExternalSystemUtil {

  def getProjectData[K](
    projectSystemId: ProjectSystemId,
    project: Project,
    key: Key[K],
  ): Either[String, Iterable[K]] = {
    val dataManager = ProjectDataManager.getInstance()
    val (_, projectDataNode: DataNode[ProjectData]) = getExternalProjectInfoAndData(dataManager, projectSystemId, project) match {
      case Right(value) => value
      case Left(error) =>
        return Left(error)
    }

    val result = findAllEnsuring(dataManager, projectDataNode, key)
    Right(result)
  }

  def getModuleData[K](
    projectSystemId: ProjectSystemId,
    project: Project,
    moduleId: String,
    key: Key[K],
  ): Either[String, Iterable[K]] = {
    val dataManager = ProjectDataManager.getInstance()

    val (
      projectInfo: ExternalProjectInfo,
      projectDataNode: DataNode[ProjectData]
    ) = getExternalProjectInfoAndData(dataManager, projectSystemId, project) match {
      case Right(value) => value
      case Left(error) =>
        return Left(error)
    }

    val moduleDataNode: DataNode[ModuleData] = ExternalSystemApiUtil.findChild(projectDataNode, ProjectKeys.MODULE, (node: DataNode[ModuleData]) => {
      // seems hacky. but apparently there isn't yet any better way to get the data for selected module?
      node.getData.getId == moduleId
    })
    if (moduleDataNode == null) {
      return Left(s"can't find module data node with id `$moduleId` for project $project, $projectInfo")
    }

    val result = findAllEnsuring(dataManager, moduleDataNode, key)
    Right(result)
  }

  private def findAllEnsuring[K](dataManager: ProjectDataManager, parent: DataNode[_], key: Key[K]): Iterable[K] = {
    val dataNodes = ExternalSystemApiUtil.findAll(parent, key).asScala
    val result = dataNodes.map { node =>
      dataManager.ensureTheDataIsReadyToUse(node)
      node.getData
    }
    result
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
