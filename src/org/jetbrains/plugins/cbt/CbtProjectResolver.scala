package org.jetbrains.plugins.cbt

import java.io.File

import com.intellij.openapi.externalSystem.model.project._
import com.intellij.openapi.externalSystem.model.task.{ExternalSystemTaskId, ExternalSystemTaskNotificationListener}
import com.intellij.openapi.externalSystem.model.{DataNode, ProjectKeys}
import com.intellij.openapi.externalSystem.service.project.ExternalSystemProjectResolver
import org.jetbrains.plugins.cbt.project.CbtProjectSystem
import org.jetbrains.plugins.cbt.project.settings.CbtExecutionSettings
import org.jetbrains.plugins.cbt.structure.CbtProjectData

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
    projectNode.addChild(createProjectData(projectNode))
    projectNode
  }

  private def createProjectData(projectNode: DataNode[ProjectData]) =
    new DataNode(CbtProjectData.Key, new CbtProjectData(), projectNode)


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
    moduleNode.createChild(ProjectKeys.CONTENT_ROOT,
      new ContentRootData(CbtProjectSystem.Id, (module \ "sourcesRoot").text))
    (module \ "mavenDependencies" \ "mavenDependency")
      .map(convertLibraryDependency(moduleNode))
      .foreach(moduleNode.addChild)
    moduleNode
  }

  private def convertLibraryDependency(parent: DataNode[ModuleData])(dependency: Node) = {
    val dependencyData = new LibraryDependencyData(parent.getData,
      new LibraryData(CbtProjectSystem.Id, dependency.text.trim), LibraryLevel.PROJECT)
    new DataNode(ProjectKeys.LIBRARY_DEPENDENCY, dependencyData, parent)
  }

  private def convertLibrary(parent: DataNode[_])(library: Node) = {
    val libraryData = new LibraryData(CbtProjectSystem.Id, library.text.trim)
    new DataNode(ProjectKeys.LIBRARY, libraryData, parent)
  }

  override def cancelTask(taskId: ExternalSystemTaskId, listener: ExternalSystemTaskNotificationListener): Boolean = true
}

