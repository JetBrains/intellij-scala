package org.jetbrains.sbt
package project.data

import java.io.File

import com.intellij.openapi.externalSystem.model.project._
import com.intellij.openapi.externalSystem.model.{DataNode, Key, ProjectKeys}
import org.jetbrains.plugins.scala.project.Version
import org.jetbrains.sbt.project.SbtProjectSystem
import org.jetbrains.sbt.project.structure.Play2Keys.AllKeys.ParsedValue
import org.jetbrains.sbt.resolvers.SbtResolver

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
  def this(jdk: Option[ScalaProjectData.Sdk], javacOptions: Seq[String], sbtVersion: String) {
    this(new ScalaProjectData(SbtProjectSystem.Id, jdk, javacOptions, sbtVersion))
  }

  protected def key = ScalaProjectData.Key
}

class ScalaSdkNode(val data: ScalaSdkData)
  extends Node[ScalaSdkData] {
  def this(scalaVersion: Version, basePackage: String, compilerClasspath: Seq[File], compilerOptions: Seq[String]) {
    this(new ScalaSdkData(SbtProjectSystem.Id, scalaVersion, basePackage, compilerClasspath, compilerOptions))
  }

  protected def key = ScalaSdkData.Key
}

class AndroidFacetNode(val data: AndroidFacetData)
  extends Node[AndroidFacetData] {
  def this(version: String, manifest: File, apk: File, res: File, assets: File, gen: File, libs: File, isLibrary: Boolean, proguardConfig: Seq[String]) {
    this(new AndroidFacetData(SbtProjectSystem.Id, version, manifest, apk, res, assets, gen, libs, isLibrary, proguardConfig))
  }

  protected def key = AndroidFacetData.Key
}

class Play2ProjectNode(val data: Play2ProjectData) extends Node[Play2ProjectData] {
  def this(projectKeys: Map[String, Map[String, ParsedValue[_]]]) {
    this(new Play2ProjectData(SbtProjectSystem.Id, projectKeys))
  }

  def key = Play2ProjectData.Key
}

class SbtModuleNode(val data: SbtModuleData)
        extends Node[SbtModuleData] {
  def this(imports: Seq[String], resolvers: Set[SbtResolver]) {
    this(new SbtModuleData(SbtProjectSystem.Id, imports, resolvers))
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
  import scala.language.implicitConversions

  implicit def node2data[T](node: Node[T]): T = node.data
}