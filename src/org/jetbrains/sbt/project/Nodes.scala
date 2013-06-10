package org.jetbrains.sbt
package project

import com.intellij.openapi.externalSystem.model.{DataNode, ProjectKeys, ProjectSystemId, Key}
import com.intellij.openapi.externalSystem.model.project._
import scala.Some
import java.io.File

/**
 * @author Pavel Fatin
 */
class ProjectNode(owner: ProjectSystemId, ideProjectFileDirectoryPath: String, linkedExternalProjectPath: String)
  extends ProjectData(owner, ideProjectFileDirectoryPath, linkedExternalProjectPath) with Node[ProjectData] {

  protected def key = ProjectKeys.PROJECT
}

class ModuleNode(owner: ProjectSystemId, typeId: String, name: String, moduleFileDirectoryPath: String, externalConfigPath: String)
  extends ModuleData(owner, typeId, name, moduleFileDirectoryPath, externalConfigPath) with Node[ModuleData] {

  protected def key = ProjectKeys.MODULE
}

class LibraryNode(owner: ProjectSystemId, name: String)
  extends LibraryData(owner, name) with Node[LibraryData] {

  def addPaths(pathType: LibraryPathType, paths: Seq[String]) {
    paths.foreach(addPath(pathType, _))
  }

  protected def key = ProjectKeys.LIBRARY
}

class ContentRootNode(owner: ProjectSystemId, name: String)
  extends ContentRootData(owner, name) with Node[ContentRootData] {

  def storePaths(sourceType: ExternalSystemSourceType, paths: Seq[String]) {
    paths.foreach(storePath(sourceType, _))
  }

  protected def key = ProjectKeys.CONTENT_ROOT
}

class ModuleDependencyNode(ownerModule: ModuleData, module: ModuleData)
  extends ModuleDependencyData(ownerModule, module) with Node[ModuleDependencyData] {

  protected def key = ProjectKeys.MODULE_DEPENDENCY
}

class LibraryDependencyNode(ownerModule: ModuleData, library: LibraryData)
  extends LibraryDependencyData(ownerModule, library) with Node[LibraryDependencyData] {

  protected def key = ProjectKeys.LIBRARY_DEPENDENCY
}

class ScalaProjectNode(owner: ProjectSystemId, javaHome: File)
  extends ScalaProjectData(owner, javaHome) with Node[ScalaProjectData] {

  protected def key = ScalaProjectData.Key
}

class ModuleLibraryNode(owner: ProjectSystemId, name: String, classes: Seq[File])
  extends ModuleLibraryData(owner, name, classes) with Node[ModuleLibraryData] {

  protected def key = ModuleLibraryData.Key
}

class ScalaFacetNode(owner: ProjectSystemId, scalaVersion: String, basePackage: String, compilerLibraryName: String, compilerOptions: Seq[String])
  extends ScalaFacetData(owner, scalaVersion, basePackage, compilerLibraryName, compilerOptions) with Node[ScalaFacetData] {

  protected def key = ScalaFacetData.Key
}

trait Node[T] { self: T =>
  private var children = Vector.empty[Node[_]]

  protected def key: Key[T]

  def add(node: Node[_]) {
    children :+= node
  }

  def addAll(nodes: Seq[Node[_]]) {
    children ++= nodes
  }

  def toDataNode: DataNode[T] = toDataNode(None)

  private def toDataNode(parent: Option[DataNode[_]]): DataNode[T] = {
    val node = new DataNode[T](key, this, parent.orNull)
    children.map(_.toDataNode(Some(node))).foreach(node.addChild)
    node
  }
}