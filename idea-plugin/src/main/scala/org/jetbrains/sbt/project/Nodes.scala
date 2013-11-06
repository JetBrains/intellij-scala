package org.jetbrains.sbt
package project

import com.intellij.openapi.externalSystem.model.{ProjectSystemId, DataNode, ProjectKeys, Key}
import com.intellij.openapi.externalSystem.model.project._
import java.io.File

/**
 * @author Pavel Fatin
 */
class ProjectNode(val data: ProjectData)
  extends Node[ProjectData] {
  def this(owner: ProjectSystemId, ideProjectFileDirectoryPath: String, linkedExternalProjectPath: String) {
    this(new ProjectData(owner, ideProjectFileDirectoryPath, linkedExternalProjectPath))
  }

  protected def key = ProjectKeys.PROJECT
}

class ModuleNode(val data: ModuleData)
  extends Node[ModuleData] {
  def this(owner: ProjectSystemId, typeId: String, name: String, moduleFileDirectoryPath: String, externalConfigPath: String) {
    this(new ModuleData(owner, typeId, name, moduleFileDirectoryPath, externalConfigPath))
  }

  protected def key = ProjectKeys.MODULE
}

class LibraryNode(val data: LibraryData)
  extends Node[LibraryData] {
  def this(owner: ProjectSystemId, name: String) {
    this(new LibraryData(owner, name))
  }

  def addPaths(pathType: LibraryPathType, paths: Seq[String]) {
    paths.foreach(data.addPath(pathType, _))
  }

  protected def key = ProjectKeys.LIBRARY
}

class ContentRootNode(val data: ContentRootData)
  extends Node[ContentRootData] {
  def this(owner: ProjectSystemId, name: String) {
   this(new ContentRootData(owner, name))
  }

  def storePaths(sourceType: ExternalSystemSourceType, paths: Seq[String]) {
    paths.foreach(data.storePath(sourceType, _))
  }

  protected def key = ProjectKeys.CONTENT_ROOT
}

class ModuleDependencyNode(val data: ModuleDependencyData)
  extends Node[ModuleDependencyData] {
  def this(ownerModule: ModuleData, module: ModuleData) {
    this(new ModuleDependencyData(ownerModule, module))
  }

  protected def key = ProjectKeys.MODULE_DEPENDENCY
}

class LibraryDependencyNode(val data: LibraryDependencyData)
  extends Node[LibraryDependencyData] {
  def this(ownerModule: ModuleData, library: LibraryData, level: LibraryLevel) {
    this(new LibraryDependencyData(ownerModule, library, level))
  }

  protected def key = ProjectKeys.LIBRARY_DEPENDENCY
}

class ScalaProjectNode(val data: ScalaProjectData)
  extends Node[ScalaProjectData] {
  def this(owner: ProjectSystemId, javaHome: File) {
    this(new ScalaProjectData(owner, javaHome))
  }

  protected def key = ScalaProjectData.Key
}

class ScalaFacetNode(val data: ScalaFacetData)
  extends Node[ScalaFacetData] {
  def this(owner: ProjectSystemId, scalaVersion: String, basePackage: String, compilerLibraryName: String, compilerOptions: Seq[String]) {
    this(new ScalaFacetData(owner, scalaVersion, basePackage, compilerLibraryName, compilerOptions))
  }

  protected def key = ScalaFacetData.Key
}

abstract class Node[T] {
  private var children = Vector.empty[Node[_]]

  protected def key: Key[T]

  protected def data: T

  def add(node: Node[_]) {
    children :+= node
  }

  def addAll(nodes: Seq[Node[_]]) {
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
  implicit def node2data[T](node: Node[T]): T = node.data
}