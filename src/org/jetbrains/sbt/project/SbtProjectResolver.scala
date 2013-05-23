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

    convert(data)
  }

  private def convert(data: Structure): DataNode[ProjectData] = {
    val project = data.project

    val projectNode = new DataNode[ProjectData](ProjectKeys.PROJECT, createProject(project), null)

    val javaHome = project.java.map(_.home).getOrElse(new File(System.getProperty("java.home")))
    projectNode.createChild(ScalaProjectData.Key, ScalaProjectData(SbtProjectSystemId, javaHome))

    val libraries = {
      val moduleLibraries = data.repository.modules.map(createLibrary)
      val compilerLibraries = scalaInstancesIn(project).distinct.map(createCompilerLibrary)
      moduleLibraries ++ compilerLibraries
    }

    libraries.foreach { library =>
      projectNode.createChild(ProjectKeys.LIBRARY, library)
    }

    projectsIn(project).foreach { project =>
      createModuleNodeIn(projectNode)(project, libraries)
    }

    projectNode
  }

  private def scalaInstancesIn(project: Project): Seq[Scala] =
    project.scala.toSeq ++ project.projects.flatMap(it => scalaInstancesIn(it))

  private def projectsIn(project: Project): Seq[Project] =
    project +: project.projects.flatMap(projectsIn)

  private def createModuleNodeIn(root: DataNode[ProjectData])(project: Project, libraries: Seq[LibraryData]) {
    val moduleData = createModule(project)

    val moduleNode = root.createChild(ProjectKeys.MODULE, moduleData)

    moduleNode.createChild(ProjectKeys.CONTENT_ROOT, createContentRoot(project))

    createDependencies(project)(moduleData, libraries).foreach { dependency =>
      moduleNode.createChild(ProjectKeys.LIBRARY_DEPENDENCY, dependency)
    }

    project.scala.foreach { scala =>
      moduleNode.createChild(ScalaFacetData.Key, createFacet(project, scala))
    }
  }

  private def createFacet(project: Project, scala: Scala): ScalaFacetData = {
    val basePackage = Some(project.organization).filter(_.contains(".")).mkString

    new ScalaFacetData(SbtProjectSystemId, scala.version, basePackage, nameFor(scala), scala.options)
  }

  private def createProject(project: Project): ProjectData = {
    val data = new ProjectData(SbtProjectSystemId, project.base.path, project.base.path)
    data.setName(project.name)
    data
  }

  private def createLibrary(module: Module): LibraryData = {
    val data = new LibraryData(SbtProjectSystemId, nameFor(module.id))
    module.binaries.foreach(file => data.addPath(LibraryPathType.BINARY, file.path))
    module.docs.foreach(file => data.addPath(LibraryPathType.DOC, file.path))
    module.sources.foreach(file => data.addPath(LibraryPathType.SOURCE, file.path))
    data
  }

  private def nameFor(id: ModuleId) = s"SBT: ${id.organization}:${id.name}:${id.revision}"

  private def createCompilerLibrary(scala: Scala): LibraryData = {
    val data = new LibraryData(SbtProjectSystemId, nameFor(scala))
    data.addPath(LibraryPathType.BINARY, scala.compilerJar.path)
    data.addPath(LibraryPathType.BINARY, scala.libraryJar.path)
    scala.extraJars.foreach(file => data.addPath(LibraryPathType.BINARY, file.path))
    data
  }

  private def nameFor(scala: Scala) = s"SBT: scala-compiler:${scala.version}"

  private def createModule(project: Project): ModuleData = {
    val data = new ModuleData(SbtProjectSystemId, StdModuleTypes.JAVA.getId, project.name, project.base.path)

    data.setInheritProjectCompileOutputPath(false)

    project.configurations.find(_.id == "compile").foreach { configuration =>
      data.setCompileOutputPath(ExternalSystemSourceType.SOURCE, configuration.classes.path)
    }

    project.configurations.find(_.id == "test").foreach { configuration =>
      data.setCompileOutputPath(ExternalSystemSourceType.TEST, configuration.classes.path)
    }

    data
  }

  private def createContentRoot(project: Project): ContentRootData = {
    val data = new ContentRootData(SbtProjectSystemId, project.base.path)

    project.configurations.find(_.id == "compile").foreach { configuration =>
      configuration.sources.foreach { directory =>
        data.storePath(ExternalSystemSourceType.SOURCE, directory.path)
      }
      configuration.resources.foreach { directory =>
        data.storePath(ExternalSystemSourceType.SOURCE, directory.path)
      }
    }

    project.configurations.find(_.id == "test").foreach { configuration =>
      configuration.sources.foreach { directory =>
        data.storePath(ExternalSystemSourceType.TEST, directory.path)
      }
      configuration.resources.foreach { directory =>
        data.storePath(ExternalSystemSourceType.TEST, directory.path)
      }
    }

    data
  }

  private def createDependencies(project: Project)(moduleData: ModuleData, libraries: Seq[LibraryData]): Seq[LibraryDependencyData] = {
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
      val data = new LibraryDependencyData(moduleData, library)
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
