package org.jetbrains.sbt
package project.data

import com.intellij.openapi.externalSystem.model.{DataNode, ProjectKeys, Key}
import com.intellij.openapi.externalSystem.model.project._
import org.jetbrains.sbt.project.SbtProjectSystem
import java.io.File

/**
 * @author Pavel Fatin
 */
class ProjectNode(val data: ProjectData)
  extends Node[ProjectData] {
  def this(name: String, ideProjectFileDirectoryPath: String, linkedExternalProjectPath: String) {
    this(new ProjectData(SbtProjectSystem.Id, name, ideProjectFileDirectoryPath, linkedExternalProjectPath))
  }

  protected def key = ProjectKeys.PROJECT
}

class ModuleNode(val data: ModuleData)
  extends Node[ModuleData] {
  def this(typeId: String, id: String, name: String, moduleFileDirectoryPath: String, externalConfigPath: String) {
    this(new ModuleData(id, SbtProjectSystem.Id, typeId, name, moduleFileDirectoryPath, externalConfigPath))
  }

  protected def key = ProjectKeys.MODULE
}

class LibraryNode(val data: LibraryData)
  extends Node[LibraryData] {
  def this(name: String, resolved: Boolean) {
    this(new LibraryData(SbtProjectSystem.Id, name, !resolved))
  }

  def addPaths(pathType: LibraryPathType, paths: Seq[String]) {
    paths.foreach(data.addPath(pathType, _))
  }

  protected def key = ProjectKeys.LIBRARY
}

class ContentRootNode(val data: ContentRootData)
  extends Node[ContentRootData] {
  def this(path: String) {
   this(new ContentRootData(SbtProjectSystem.Id, path))
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
  def this(javaHome: File, javacOptions: Seq[String]) {
    this(new ScalaProjectData(SbtProjectSystem.Id, javaHome, javacOptions))
  }

  protected def key = ScalaProjectData.Key
}

class ScalaFacetNode(val data: ScalaFacetData)
  extends Node[ScalaFacetData] {
  def this(scalaVersion: String, basePackage: String, compilerLibraryName: String, compilerOptions: Seq[String]) {
    this(new ScalaFacetData(SbtProjectSystem.Id, scalaVersion, basePackage, compilerLibraryName, compilerOptions))
  }

  protected def key = ScalaFacetData.Key
}

class SbtModuleNode(val data: SbtModuleData)
        extends Node[SbtModuleData] {
  def this(imports: Seq[String]) {
    this(new SbtModuleData(SbtProjectSystem.Id, imports))
  }

  protected def key = SbtModuleData.Key
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