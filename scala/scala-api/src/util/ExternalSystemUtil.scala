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
    // TODO - instead of project.getBasePath, proper rootProjectPath should be passed to #getExternalProjectInfoAndData.
    //  Otherwise, for multiple separate projects imported via e.g. with "Module from existing sources" or "Link project"
    //  this will lead to incorrect values (see how it is done in #getModuleData). See more info in #SCL-22087
    val (_, projectDataNode: DataNode[ProjectData]) = getExternalProjectInfoAndData(dataManager, projectSystemId, project, Option(project.getBasePath)) match {
      case Right(value) => value
      case Left(error) =>
        return Left(error)
    }

    val result = findAllNodesEnsuring(dataManager, projectDataNode, key)
    Right(result)
  }

  /**
   * @param sbtModuleChildKey see [[org.jetbrains.plugins.scala.util.ExternalSystemUtil.SbtModuleChildKey]] docs for more details. In BSP import the value for this parameter should be None
   */
  def getModuleData[K](
    projectSystemId: ProjectSystemId,
    project: Project,
    moduleId: String,
    key: Key[K],
    rootProjectPath: Option[String],
    sbtModuleChildKey: Option[SbtModuleChildKey]
  ): Either[String, Iterable[K]] = {
    val nodes = getModuleDataNodes(projectSystemId, project, moduleId, key, rootProjectPath, sbtModuleChildKey)
    nodes.map(_.map(_.getData))
  }

  /**
   * @param sbtModuleChildKey see [[org.jetbrains.plugins.scala.util.ExternalSystemUtil.SbtModuleChildKey]] docs for more details. In BSP import the value for this parameter should be None
   */
  private def getModuleDataNodes[K](
    projectSystemId: ProjectSystemId,
    project: Project,
    moduleId: String,
    key: Key[K],
    rootProjectPath: Option[String],
    sbtModuleChildKey: Option[SbtModuleChildKey]
  ): Either[String, Iterable[DataNode[K]]] = {
    val dataManager = ProjectDataManager.getInstance()
    val moduleDataNode = getModuleDataNode(dataManager, projectSystemId, project, moduleId, rootProjectPath, sbtModuleChildKey)
    moduleDataNode.map(findAllNodesEnsuring(dataManager, _, key))
  }

  /**
   * @param sbtModuleChildKey see [[org.jetbrains.plugins.scala.util.ExternalSystemUtil.SbtModuleChildKey]] docs for more details. In BSP import the value for this parameter should be None
   */
  def getModuleDataNode(
    projectSystemId: ProjectSystemId,
    project: Project,
    moduleId: String,
    rootProjectPath: Option[String],
    sbtModuleChildKey: Option[SbtModuleChildKey]
  ): Option[DataNode[_ <: ModuleData]] = {
    val dataManager = ProjectDataManager.getInstance()
    getModuleDataNode(dataManager, projectSystemId, project, moduleId, rootProjectPath, sbtModuleChildKey).toOption
  }

  /**
   * Class that stores the keys to the type of modules that are placed as children to the ModuleData during the SBT import
   */
  case class SbtModuleChildKey(sbtNestedModuleKey: Key[_ <: ModuleData], sbtSourceSetModuleKey: Key[_ <: ModuleData])

  private def getModuleDataNode(
    dataManager: ProjectDataManager,
    projectSystemId: ProjectSystemId,
    project: Project,
    moduleId: String,
    rootProjectPath: Option[String],
    sbtModuleChildKey: Option[SbtModuleChildKey],
  ): Either[String, DataNode[_ <: ModuleData]] = {
    val (projectInfo: ExternalProjectInfo, projectDataNode: DataNode[ProjectData]) = getExternalProjectInfoAndData(dataManager, projectSystemId, project, rootProjectPath) match {
      case Right(value) => value
      case Left(error) =>
        return Left(error)
    }

    def findDataNodeWithModuleId(parentNode: DataNode[_], key: Key[_<:ModuleData]): DataNode[_<:ModuleData] = {
      ExternalSystemApiUtil.findChild(parentNode, key, (node: DataNode[_ <: ModuleData]) => {
        // seems hacky. but apparently there isn't yet any better way to get the data for selected module?
        node.getData.getId == moduleId
      })
    }

    val moduleDataNode = sbtModuleChildKey match {
      case Some(x) => processModuleDataChildNodesSbt(projectDataNode, moduleId, x).orNull
      case _ => findDataNodeWithModuleId(projectDataNode, ProjectKeys.MODULE)
    }
    if (moduleDataNode != null) {
      Right(moduleDataNode)
    } else {
      Left(s"can't find module data node with id `$moduleId` for project $project, $projectInfo")
    }
  }

  private def processModuleDataChildNodesSbt(
    projectDataNode: DataNode[ProjectData],
    moduleId: String,
    moduleChildKey: SbtModuleChildKey
  ): Option[DataNode[_<:ModuleData]] = {

    def findModuleDataInChildren(key: Key[_ <: ModuleData], parents: Seq[DataNode[_]]): Either[Seq[DataNode[_<:ModuleData]], DataNode[_<:ModuleData]] = {
      val children = parents.flatMap(ExternalSystemApiUtil.findAll(_, key).asScala.toSeq)
      children.find(_.getData.getId == moduleId).toRight(children)
    }

    val moduleDataEither = findModuleDataInChildren(ProjectKeys.MODULE, Seq(projectDataNode))
    val moduleDataChildren = moduleDataEither match {
      case Right(r) => return Some(r)
      case Left(children) => children
    }

    val sbtNestedModuleDataEither = findModuleDataInChildren(moduleChildKey.sbtNestedModuleKey, moduleDataChildren)
    val sbtNestedModuleDataChildren = sbtNestedModuleDataEither match {
      case Right(r) => return Some(r)
      // it is required to add moduleDataChildren to sbtNestedModuleChildren, because sbt source set modules may be found in both
      case Left(children) => children ++ moduleDataChildren
    }

    val sbtSourceSetModuleDataEither = findModuleDataInChildren(moduleChildKey.sbtSourceSetModuleKey, sbtNestedModuleDataChildren)
    sbtSourceSetModuleDataEither.toOption
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
    rootProjectPath: Option[String]
  ): Either[String, (ExternalProjectInfo, DataNode[ProjectData])] = {
    //TODO: consider using `com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.findProjectInfo` and see how it works?
    val projectPath = rootProjectPath.getOrElse(project.getBasePath)
    val projectInfoOpt1 = Option(dataManager.getExternalProjectData(project, projectSystemId, projectPath))
    val projectInfoOpt2 = projectInfoOpt1.orElse {
      // in tests org.jetbrains.sbt.project.SbtProjectImportingTest `project.getBasePath` doesn't equal to actual external project data
      if (ApplicationManager.getApplication.isUnitTestMode) {
        val externalProjectsData = dataManager.getExternalProjectsData(project, projectSystemId).asScala
        // note: if there is more than one ExternalProjectInfo, finding the right one by comparing ProjectData internalName and project name will not be the correct.
        // If there is more than one linked project, the project name is not changed if it is different from ProjectData internalName
        // (see com.intellij.openapi.externalSystem.service.project.manage.ProjectDataServiceImpl#importData)
        if (externalProjectsData.size > 1) {
          externalProjectsData.find(_.getExternalProjectStructure.getData.getLinkedExternalProjectPath == projectPath)
        } else {
          externalProjectsData.find(_.getExternalProjectStructure.getData.getInternalName == project.getName)
        }
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
