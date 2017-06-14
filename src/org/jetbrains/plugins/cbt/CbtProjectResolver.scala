package org.jetbrains.plugins.cbt

import java.io.File

import com.intellij.openapi.externalSystem.model.project._
import com.intellij.openapi.externalSystem.model.task.{ExternalSystemTaskId, ExternalSystemTaskNotificationListener}
import com.intellij.openapi.externalSystem.model.{DataNode, ProjectKeys}
import com.intellij.openapi.externalSystem.service.project.ExternalSystemProjectResolver
import org.jetbrains.plugins.cbt.project.CbtProjectSystem
import org.jetbrains.plugins.cbt.project.settings.CbtExecutionSettings
import org.jetbrains.plugins.cbt.structure.{CbtModuleData, CbtProjectData}

import scala.xml.Node

class CbtProjectResolver extends ExternalSystemProjectResolver[CbtExecutionSettings] {


  override def resolveProjectInfo(id: ExternalSystemTaskId,
                                  projectPath: String,
                                  isPreviewMode: Boolean,
                                  settings: CbtExecutionSettings,
                                  listener: ExternalSystemTaskNotificationListener): DataNode[ProjectData] = {
    val projectPath = settings.realProjectPath
    val root = new File(projectPath)
    println("Cbt resolver called")

    val xml = CBT.projectBuidInfo(root)
    convert(xml)
  }


  private def convert(project: Node) =
    convertProject(project)

  private def convertProject(project: Node) = {
    val projectData = new ProjectData(CbtProjectSystem.Id,
      (project \ "name").text,
      (project \ "root").text,
      (project \ "root").text)
    val projectNode = new DataNode[ProjectData](ProjectKeys.PROJECT, projectData, null)
    (project \ "modules" \ "module")
      .map(convertModule(projectNode))
      .foreach(projectNode.addChild)
    (project \ "libraries" \ "library")
      .map(convertLibrary(projectNode))
      .foreach(projectNode.addChild)
    projectNode.addChild(createCbtLibrary(projectNode))
    projectNode.addChild(createProjectData(projectNode, project))
    projectNode
  }

  private def createProjectData(projectDateNode: DataNode[ProjectData], node: Node) =
    new DataNode(CbtProjectData.Key, new CbtProjectData(), projectDateNode)


  private def createModuleData(moduleDataNode: DataNode[ModuleData], node: Node) = {
    val scalacClasspath = (node \ "classpaths" \ "classpathItem")
      .map(t => new File(t.text.trim))
    new DataNode(CbtModuleData.Key, new CbtModuleData(scalacClasspath), moduleDataNode)
  }

  private def convertModule(parent: DataNode[_])(module: Node) = {
    val moduleDependencies = //TODO
      Seq(module \ "moduleDependencies" \ "moduleDependency", module \ "parentBuild")
        .flatten
        .map(d => d.text.trim)
    val moduleData = new ModuleData((module \ "name").text,
      CbtProjectSystem.Id,
      "JAVA_MODULE",
      (module \ "name").text,
      (module \ "root").text,
      (module \ "root").text)
    val moduleNode = new DataNode(ProjectKeys.MODULE, moduleData, parent)
    moduleNode.addChild(createContentRoot(module, moduleNode))
    (module \ "mavenDependencies" \ "mavenDependency")
      .map(convertLibraryDependency(moduleNode))
      .foreach(moduleNode.addChild)
    moduleNode.addChild(createCbtLibraryDependency(moduleNode))
    moduleNode.addChild(createModuleData(moduleNode, module))
    moduleNode
  }

  private def createContentRoot(module: Node, parent: DataNode[_]) = {
    val contentRootData = new ContentRootData(CbtProjectSystem.Id, (module \ "root").text)
    (module \ "sources" \ "source")
      .map(s => new File(s.text.trim))
      .filter(_.isDirectory)
      .foreach(d => contentRootData.storePath(ExternalSystemSourceType.SOURCE, d.getPath))
    contentRootData.storePath(ExternalSystemSourceType.EXCLUDED, (module \ "target").text.trim)
    new DataNode(ProjectKeys.CONTENT_ROOT, contentRootData, parent)
  }

  private def convertLibraryDependency(parent: DataNode[ModuleData])(dependency: Node) = {
    val dependencyData = new LibraryDependencyData(parent.getData,
      new LibraryData(CbtProjectSystem.Id, dependency.text.trim), LibraryLevel.PROJECT)
    new DataNode(ProjectKeys.LIBRARY_DEPENDENCY, dependencyData, parent)
  }

  private def createCbtLibraryDependency(parent: DataNode[ModuleData])= {
    val dependencyData = new LibraryDependencyData(parent.getData,createCbtLibraryData, LibraryLevel.PROJECT)
    new DataNode(ProjectKeys.LIBRARY_DEPENDENCY, dependencyData, parent)
  }

  def createCbtLibrary(parent: DataNode[_]) = {
    val libraryData = createCbtLibraryData
    val libraryNode = new DataNode(ProjectKeys.LIBRARY, libraryData, parent)
    libraryNode
  }

  private def createCbtLibraryData = {
    val libraryData = new LibraryData(CbtProjectSystem.Id, "CBT")
    libraryData.addPath(LibraryPathType.SOURCE, "/home/ilya/apps/cbt")
    libraryData
  }

  private def convertLibrary(parent: DataNode[_])(library: Node) = {
    val libraryData = new LibraryData(CbtProjectSystem.Id, (library \ "name").text.trim)
    (library \ "jars" \ "jar")
      .map(_.text.trim)
      .foreach(libraryData.addPath(LibraryPathType.BINARY, _))
    val libraryNode = new DataNode(ProjectKeys.LIBRARY, libraryData, parent)
    libraryNode
  }

  override def cancelTask(taskId: ExternalSystemTaskId, listener: ExternalSystemTaskNotificationListener): Boolean = true
}

