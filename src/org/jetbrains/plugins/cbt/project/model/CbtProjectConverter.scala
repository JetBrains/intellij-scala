package org.jetbrains.plugins.cbt.project.model

import java.io.File
import java.nio.file.Files

import com.intellij.openapi.externalSystem.model.project._
import com.intellij.openapi.externalSystem.model.{DataNode, ProjectKeys}
import org.jetbrains.plugins.cbt.project.CbtProjectSystem
import org.jetbrains.plugins.cbt.project.model.CbtProjectInfo._
import org.jetbrains.plugins.cbt.project.settings.CbtExecutionSettings
import org.jetbrains.plugins.cbt.structure.{CbtModuleExtData, CbtProjectData}
import org.jetbrains.plugins.scala.project.Version

object CbtProjectConverter {
  def apply(project: Project, settings: CbtExecutionSettings): DataNode[ProjectData] =
    if (settings.isCbt)
      new CbtSourceProjectConverter(project).convert()
    else
      new CbtProjectConverter(project).convert()
}

class CbtProjectConverter(project: Project) {
  private[model] val librariesMap = (project.libraries ++ project.cbtLibraries).map(l => l.name -> l).toMap
  private[model] val modulesMap = project.modules.map(m => m.name -> m).toMap
  private[model] val compilerLibrariesMap = project.scalaCompilers.map(c => s"scala-library-${c.version}" -> c).toMap

  def convert(): DataNode[ProjectData] =
    convertProject(project: Project)

  private[model] def convertProject(project: Project): DataNode[ProjectData] = {
    val projectData = new ProjectData(CbtProjectSystem.Id,
      project.name,
      project.root.getPath,
      project.root.getPath)
    val projectNode = new DataNode[ProjectData](ProjectKeys.PROJECT, projectData, null)
    project.libraries
      .map(createLibraryNode(projectNode))
      .foreach(projectNode.addChild)
    project.modules
      .map(createModule(projectNode))
      .foreach(projectNode.addChild)
    projectNode.addChild(createProjectData(projectNode))
    projectNode
  }

  private[model] def createLibraryNode(parent: DataNode[_])(library: Library) = {
    val libraryData = createLibraryData(library)
    new DataNode(ProjectKeys.LIBRARY, libraryData, parent)
  }

  private[model] def createLibraryData(library: Library) = {
    val libraryData = new LibraryData(CbtProjectSystem.Id, library.name)
    library.jars
      .foreach { j =>
        val libraryType = j.jarType match {
          case JarType.Binary => LibraryPathType.BINARY
          case JarType.Source => LibraryPathType.SOURCE
        }
        libraryData.addPath(libraryType, j.jar.getPath)
      }
    libraryData
  }

  private[model] def createModule(parent: DataNode[_])(module: Module) = {
    val moduleData = createModuleData(module)
    val moduleNode = new DataNode(ProjectKeys.MODULE, moduleData, parent)
    moduleNode.addChild(createContentRoot(module, moduleNode))
    module.binaryDependencies
      .map(_.name)
      .map(createBinaryDependencyNode(moduleNode))
      .foreach(moduleNode.addChild)
    (module.moduleDependencies ++ module.parentBuild.map(ModuleDependency))
      .map(createModuleDependency(moduleNode))
      .foreach(moduleNode.addChild)
    moduleNode.addChild(createExtModuleData(module, moduleNode))
    createCbtDependencies(moduleNode)
      .foreach(moduleNode.addChild)
    moduleNode
  }

  private[model] def createContentRoot(module: Module, moduleNode: DataNode[ModuleData]) = {
    val rootPath = module.root.toPath.toAbsolutePath
    val contentRootData = new ContentRootData(CbtProjectSystem.Id, rootPath.toString)
    module.sourceDirs
      .map(_.toPath)
      .filter(s => Files.isDirectory(s))
      .filter(s => s.toAbsolutePath.startsWith(rootPath))
      .foreach(s => contentRootData.storePath(ExternalSystemSourceType.SOURCE, s.toString))
    contentRootData.storePath(ExternalSystemSourceType.EXCLUDED, module.target.getPath)
    new DataNode(ProjectKeys.CONTENT_ROOT, contentRootData, moduleNode)
  }

  private[model] def createExtModuleData(module: Module, moduleNode: DataNode[ModuleData]) = {
    val classpath = replaceClasspathScalaLibsWithCompilers(module.classpath)
    val moduleExtData = new CbtModuleExtData(Version(module.scalaVersion), classpath, module.scalacOptions)
    new DataNode(CbtModuleExtData.Key, moduleExtData, moduleNode)
  }

  private[model] def replaceClasspathScalaLibsWithCompilers(classpath: Seq[File]) = {
    classpath.flatMap { f =>
      compilerLibrariesMap
        .get(f.getName.stripSuffix(".jar"))
        .map(_.jars)
        .getOrElse(Seq(f))
    }
      .distinct
  }

  private[model] def createModuleDependency(parent: DataNode[ModuleData])(dependency: ModuleDependency) = {
    val moduleData = createModuleData(modulesMap(dependency.name))
    val dependencyData = new ModuleDependencyData(parent.getData, moduleData)
    new DataNode(ProjectKeys.MODULE_DEPENDENCY, dependencyData, parent)
  }

  private[model] def createModuleData(module: Module) = {
    new ModuleData(module.name,
      CbtProjectSystem.Id,
      "JAVA_MODULE",
      module.name,
      module.root.getPath,
      module.root.getPath)
  }

  private[model] def createCbtDependencies(moduleNode: DataNode[ModuleData]) =
    project.cbtLibraries.map(_.name)
      .map(createBinaryDependencyNode(moduleNode))

  private[model] def createBinaryDependencyNode(parent: DataNode[ModuleData])(dependencyName: String): DataNode[LibraryDependencyData] = {
    val libraryData = createLibraryData(librariesMap(dependencyName))
    val dependencyData = new LibraryDependencyData(parent.getData, libraryData, LibraryLevel.PROJECT)
    new DataNode(ProjectKeys.LIBRARY_DEPENDENCY, dependencyData, parent)
  }

  private[model] def createProjectData(projectNode: DataNode[ProjectData]) =
    new DataNode(CbtProjectData.Key, new CbtProjectData(), projectNode)
}