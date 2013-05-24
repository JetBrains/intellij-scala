package org.jetbrains.sbt
package project

import com.intellij.openapi.externalSystem.service.project.ExternalSystemProjectResolver
import com.intellij.openapi.externalSystem.model.task.{ExternalSystemTaskType, ExternalSystemTaskNotificationEvent, ExternalSystemTaskId}
import com.intellij.openapi.externalSystem.model.project._
import com.intellij.openapi.module.StdModuleTypes
import com.intellij.openapi.externalSystem.model.{ExternalSystemException, ProjectKeys, DataNode}
import java.io.File
import settings._
import org.jetbrains.sbt.project.model._
import org.jetbrains.sbt.project.model.Structure
import com.intellij.openapi.roots.DependencyScope

/**
 * @author Pavel Fatin
 */
class SbtProjectResolver extends ExternalSystemProjectResolver[SbtExecutionSettings] {
  def resolveProjectInfo(id: ExternalSystemTaskId, projectPath: String, downloadLibraries: Boolean, settings: SbtExecutionSettings): DataNode[ProjectData] = {
    if (downloadLibraries) return null

    val path = {
      val file = new File(projectPath)
      if (file.isDirectory) file.getPath else file.getParent
    }

    val xml = {
      val listener = settings.getNotificationListener

      val task = ExternalSystemTaskId.create(ExternalSystemTaskType.RESOLVE_PROJECT)

      listener.onStart(task)
      val result = PluginRunner.read(new File(path)) { message =>
        listener.onStatusChange(new ExternalSystemTaskNotificationEvent(task, message))
      }
      listener.onEnd(task)

      result match {
        case Left(errors) => throw new ExternalSystemException(errors)
        case Right(node) => node
      }
    }

    val data = Parser.parse(xml, new File(System.getProperty("user.home")))

    convert(data).toDataNode
  }

  private def convert(data: Structure): Node[ProjectData] = {
    val project = data.project

    val projectNode = createProject(project)

    val javaHome = project.java.map(_.home).getOrElse(new File(System.getProperty("java.home")))
    projectNode.add(new ScalaProjectNode(SbtProjectSystemId, javaHome))

    val libraries =
      data.repository.modules.map(createLibrary) ++
        projectsIn(project).flatMap(_.scala).distinct.map(createCompilerLibrary)

    projectNode.addAll(libraries)

    val projects = projectsIn(project)

    val moduleNodes: Seq[ModuleNode] = projects.map { project =>
      val moduleNode = createModule(project)
      moduleNode.add(createContentRoot(project))
      moduleNode.addAll(createLibraryDependencies(project)(moduleNode, libraries))
      moduleNode.addAll(project.scala.map(createFacet(project, _)).toSeq)
      moduleNode
    }

    projectNode.addAll(moduleNodes)

    projects.zip(moduleNodes).foreach { case (moduleProject, moduleNode) =>
      moduleProject.configurations.flatMap(_.dependencies).foreach { dependencyName =>
        val dependency = moduleNodes.find(_.getName == dependencyName).getOrElse(
          throw new ExternalSystemException("Cannot find module dependency: " + dependencyName))
        moduleNode.add(new ModuleDependencyNode(moduleNode, dependency))
      }
    }

    projectNode
  }

  private def projectsIn(project: Project): Seq[Project] =
    project +: project.projects.flatMap(projectsIn)

  private def createFacet(project: Project, scala: Scala): ScalaFacetNode = {
    val basePackage = Some(project.organization).filter(_.contains(".")).mkString

    new ScalaFacetNode(SbtProjectSystemId, scala.version, basePackage, nameFor(scala), scala.options)
  }

  private def createProject(project: Project): ProjectNode = {
    val result = new ProjectNode(SbtProjectSystemId, project.base.path, project.base.path)
    result.setName(project.name)
    result
  }

  private def createLibrary(module: Module): LibraryNode = {
    val result = new LibraryNode(SbtProjectSystemId, nameFor(module.id))
    result.addPaths(LibraryPathType.BINARY, module.binaries.map(_.path))
    result.addPaths(LibraryPathType.DOC, module.docs.map(_.path))
    result.addPaths(LibraryPathType.SOURCE, module.sources.map(_.path))
    result
  }

  private def nameFor(id: ModuleId) = s"SBT: ${id.organization}:${id.name}:${id.revision}"

  private def createCompilerLibrary(scala: Scala): LibraryNode = {
    val result = new LibraryNode(SbtProjectSystemId, nameFor(scala))
    val jars = scala.compilerJar +: scala.libraryJar +: scala.extraJars
    result.addPaths(LibraryPathType.BINARY, jars.map(_.path))
    result
  }

  private def nameFor(scala: Scala) = s"SBT: scala-compiler:${scala.version}"

  private def createModule(project: Project): ModuleNode = {
    val result = new ModuleNode(SbtProjectSystemId, StdModuleTypes.JAVA.getId, project.name, project.base.path)

    result.setInheritProjectCompileOutputPath(false)

    project.configurations.find(_.id == "compile").foreach { configuration =>
      result.setCompileOutputPath(ExternalSystemSourceType.SOURCE, configuration.classes.path)
    }

    project.configurations.find(_.id == "test").foreach { configuration =>
      result.setCompileOutputPath(ExternalSystemSourceType.TEST, configuration.classes.path)
    }

    result
  }

  private def createContentRoot(project: Project): ContentRootNode = {
    val result = new ContentRootNode(SbtProjectSystemId, project.base.path)

    result.storePaths(ExternalSystemSourceType.SOURCE, rootPathsIn(project, "compile"))
    result.storePaths(ExternalSystemSourceType.TEST, rootPathsIn(project, "test"))

    result
  }

  private def rootPathsIn(project: Project, scope: String): Seq[String] = {
    project.configurations.find(_.id == scope)
      .map(configuration => configuration.sources ++ configuration.resources)
      .getOrElse(Seq.empty)
      .map(_.path)
  }

  private def createLibraryDependencies(project: Project)(moduleData: ModuleData, libraries: Seq[LibraryData]): Seq[LibraryDependencyNode] = {
    val moduleToConfigurations =
      project.configurations
        .flatMap(configuration => configuration.modules.map(module => (module, configuration)))
        .groupBy(_._1)
        .mapValues(_.unzip._2.toSet)
        .toSeq

    moduleToConfigurations.map { case (module, configurations) =>
      val name = nameFor(module)
      val library = libraries.find(_.getName == name).getOrElse(
        throw new ExternalSystemException("Library not found: " + name))
      val data = new LibraryDependencyNode(moduleData, library)
      data.setScope(scopeFor(configurations))
      data
    }
  }

  private def scopeFor(configurations: Set[Configuration]): DependencyScope = {
    val ids = configurations.map(_.id)

    if (ids.contains("compile"))
      DependencyScope.COMPILE
    else if (ids.contains("test"))
      DependencyScope.TEST
    else if (ids.contains("runtime"))
      DependencyScope.RUNTIME
    else
      DependencyScope.PROVIDED
  }
}
