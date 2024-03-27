package org.jetbrains.sbt
package project.data

import com.intellij.openapi.externalSystem.model.project._
import com.intellij.openapi.externalSystem.model.{DataNode, Key, ProjectKeys}
import org.jetbrains.sbt.project.SbtProjectSystem
import org.jetbrains.sbt.project.module.{SbtNestedModuleData, SbtSourceSetData}

import java.net.URI

class ProjectNode(override val data: ProjectData)
  extends Node[ProjectData] {
  def this(name: String, ideProjectFileDirectoryPath: String, linkedExternalProjectPath: String) = {
    this(new ProjectData(SbtProjectSystem.Id, name, ideProjectFileDirectoryPath, linkedExternalProjectPath))
  }

  override protected def key: Key[ProjectData] = ProjectKeys.PROJECT
}

class ModuleNode(override val data: ModuleData)
  extends Node[ModuleData] {
  def this(typeId: String, projectId: String, name: String, moduleFileDirectoryPath: String, externalConfigPath: String) = {
    this(new ModuleData(projectId, SbtProjectSystem.Id, typeId, name, moduleFileDirectoryPath, externalConfigPath))
  }

  override protected def key: Key[ModuleData] = ProjectKeys.MODULE
}

object ModuleNode {
  /**
    * Generate a formatted ID with project id and URI.
    * This prevent ID conflicts on multi module projects where different modules have same value as ID
    * @param projectId project ID
    * @param projectURI project root path or repository url
    * @return
    */
  def combinedId(projectId: String, projectURI: Option[URI]): String =
    projectURI match {
      case Some(uri) => f"$projectId [$uri]"
      case None => projectId
    }
}

class NestedModuleNode(override val data: SbtNestedModuleData)
  extends Node[SbtNestedModuleData] {

  def this(typeId: String, projectId: String, externalName: String, moduleFileDirectoryPath: String, externalConfigPath: String) = {
    this(SbtNestedModuleData(projectId, externalName, moduleFileDirectoryPath, externalConfigPath, typeId))
  }

  override protected def key: Key[SbtNestedModuleData] = SbtNestedModuleData.Key
}

class SbtSourceSetModuleNode(override val data: SbtSourceSetData)
  extends Node[SbtSourceSetData] {

  def this(typeId: String, projectId: String, externalName: String, moduleFileDirectoryPath: String, externalConfigPath: String) = {
    this(SbtSourceSetData(projectId, externalName, moduleFileDirectoryPath, externalConfigPath, typeId))
  }

  override protected def key: Key[SbtSourceSetData] = SbtSourceSetData.Key
}

class LibraryNode(override val data: LibraryData)
  extends Node[LibraryData] {
  def this(name: String, resolved: Boolean) = {
    this(new LibraryData(SbtProjectSystem.Id, name, !resolved))
  }

  def addPaths(pathType: LibraryPathType, paths: Seq[String]): Unit = {
    paths.foreach(data.addPath(pathType, _))
  }

  override protected def key: Key[LibraryData] = ProjectKeys.LIBRARY
}

class ModuleSdkNode(override val data: ModuleSdkData)
  extends Node[ModuleSdkData] {

  override protected def key: Key[ModuleSdkData] = ModuleSdkData.KEY
}

object ModuleSdkNode {
  def inheritFromProject: ModuleSdkNode = new ModuleSdkNode(new ModuleSdkData(null))
}

class ContentRootNode(override val data: ContentRootData)
  extends Node[ContentRootData] {
  def this(path: String) = {
    this(new ContentRootData(SbtProjectSystem.Id, path))
  }

  def storePaths(sourceType: ExternalSystemSourceType, paths: Seq[String]): Unit = {
    paths.foreach(data.storePath(sourceType, _))
  }

  override protected def key: Key[ContentRootData] = ProjectKeys.CONTENT_ROOT
}

class ModuleDependencyNode(override val data: ModuleDependencyData)
  extends Node[ModuleDependencyData] {
  def this(ownerModule: ModuleData, module: ModuleData) = {
    this(new ModuleDependencyData(ownerModule, module))
  }

  override protected def key: Key[ModuleDependencyData] = ProjectKeys.MODULE_DEPENDENCY
}

class LibraryDependencyNode(override val data: LibraryDependencyData)
  extends Node[LibraryDependencyData] {
  def this(ownerModule: ModuleData, library: LibraryData, level: LibraryLevel) = {
    this(new LibraryDependencyData(ownerModule, library, level))
  }

  override protected def key: Key[LibraryDependencyData] = ProjectKeys.LIBRARY_DEPENDENCY
}

class SbtProjectNode(override val data: SbtProjectData) extends Node[SbtProjectData] {
  override protected def key: Key[SbtProjectData] = SbtProjectData.Key
}

class SbtModuleNode(override val data: SbtModuleData) extends Node[SbtModuleData] {
  override protected def key: Key[SbtModuleData] = SbtModuleData.Key
}

class SbtSettingNode(override val data: SbtSettingData) extends Node[SbtSettingData] {
  override protected def key: Key[SbtSettingData] = SbtSettingData.Key
}

class SbtTaskNode(override val data: SbtTaskData) extends Node[SbtTaskData] {
  override protected def key: Key[SbtTaskData] = SbtTaskData.Key
}

class SbtCommandNode(override val data: SbtCommandData) extends Node[SbtCommandData] {
  override protected def key: Key[SbtCommandData] = SbtCommandData.Key
}

class ScalaSdkNode(override val data: SbtScalaSdkData) extends Node[SbtScalaSdkData] {
  override protected def key: Key[SbtScalaSdkData] = SbtScalaSdkData.Key
}

class ModuleExtNode(override val data: SbtModuleExtData) extends Node[SbtModuleExtData] {
  override protected def key: Key[SbtModuleExtData] = SbtModuleExtData.Key
}

class Play2ProjectNode(override val data: SbtPlay2ProjectData) extends Node[SbtPlay2ProjectData] {
  override def key: Key[SbtPlay2ProjectData] = SbtPlay2ProjectData.Key
}

class SbtBuildModuleNode(override val data: SbtBuildModuleData) extends Node[SbtBuildModuleData] {
  override protected def key: Key[SbtBuildModuleData] = SbtBuildModuleData.Key
}


abstract class Node[T] {
  private var children = Vector.empty[Node[_]]

  protected def key: Key[T]

  protected def data: T

  def add(node: Node[_]): Unit = {
    children :+= node
  }

  def addAll(nodes: Iterable[Node[_]]): Unit = {
    children ++= nodes
  }

  def toDataNode: DataNode[T] = toDataNode(None)

  private def toDataNode(parent: Option[DataNode[_]]): DataNode[T] = {
    val node = new DataNode[T](key, data, parent.orNull)
    children.map(_.toDataNode(Some(node))).foreach(node.addChild)
    node
  }
}

object Node {

  import scala.language.implicitConversions

  implicit def node2data[T](node: Node[T]): T = node.data
}
