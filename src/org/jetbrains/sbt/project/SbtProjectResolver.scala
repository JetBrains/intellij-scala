package org.jetbrains.sbt
package project

import com.intellij.openapi.externalSystem.service.project.ExternalSystemProjectResolver
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.project._
import com.intellij.openapi.module.StdModuleTypes
import com.intellij.openapi.externalSystem.model.{ExternalSystemException, ProjectKeys, DataNode}
import java.io.File
import settings._
import org.jetbrains.sbt.project.model._
import org.jetbrains.sbt.project.model.Structure

/**
 * @author Pavel Fatin
 */
class SbtProjectResolver extends ExternalSystemProjectResolver[SbtExecutionSettings] {
  def resolveProjectInfo(id: ExternalSystemTaskId, projectPath: String, downloadLibraries: Boolean, settings: SbtExecutionSettings) = {
    val path = {
      val file = new File(projectPath)
      if (file.isDirectory) file.getPath else file.getParent
    }

    val xml = {
      val (output, node) = PluginRunner.read(new File(path))

      node.getOrElse {
        val message = s"Exit code: ${output.code}\nStdout:\n${output.stdout}\nStderr:\n${output.stderr}"
        throw new ExternalSystemException(message)
      }
    }

    val data = Parser.parse(xml, new File(System.getProperty("user.home")))

    convert(data)
  }

  private def convert(data: Structure): DataNode[ProjectData] = {
    val project = data.project

    val projectNode = new DataNode[ProjectData](ProjectKeys.PROJECT, createProject(project), null)

    val libraries = data.repository.modules.map(createLibrary)

    libraries.foreach { library =>
      projectNode.createChild(ProjectKeys.LIBRARY, library)
    }

    project.scala.foreach { scala =>
      projectNode.createChild(ProjectKeys.LIBRARY, createCompilerLibrary(scala))
    }

    val moduleData = createModule(project)

    val moduleNode = projectNode.createChild(ProjectKeys.MODULE, moduleData)

    moduleNode.createChild(ProjectKeys.CONTENT_ROOT, createContentRoot(project))

    createDependencies(project)(moduleData, libraries).foreach { dependency =>
      moduleNode.createChild(ProjectKeys.LIBRARY_DEPENDENCY, dependency)
    }

    projectNode
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
    project.configurations.flatMap { configuration =>
      configuration.modules.map { module =>
        val name = nameFor(module)
        val library = libraries.find(_.getName == name).getOrElse(
          throw new ExternalSystemException("Library not found: " + name))
        new LibraryDependencyData(moduleData, library)
      }
    }
  }
}
