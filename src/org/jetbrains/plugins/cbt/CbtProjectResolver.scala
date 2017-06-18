package org.jetbrains.plugins.cbt

import java.io.File

import com.intellij.openapi.externalSystem.model.project._
import com.intellij.openapi.externalSystem.model.task.{ExternalSystemTaskId, ExternalSystemTaskNotificationListener}
import com.intellij.openapi.externalSystem.model.{DataNode, ProjectKeys}
import com.intellij.openapi.externalSystem.service.project.ExternalSystemProjectResolver
import org.jetbrains.plugins.cbt.project.CbtProjectSystem
import org.jetbrains.plugins.cbt.project.settings.CbtExecutionSettings
import org.jetbrains.plugins.cbt.structure.{CbtModuleExtData, CbtProjectData}
import org.jetbrains.plugins.scala.project.Version

import scala.xml.{Node, XML}
import org.jetbrains.sbt.RichFile

class CbtProjectResolver extends ExternalSystemProjectResolver[CbtExecutionSettings] {

  private case class ProjectInfo(libraries: Map[String, LibraryData],
                                 modules: Map[String, ModuleData])

  override def resolveProjectInfo(id: ExternalSystemTaskId,
                                  projectPath: String,
                                  isPreviewMode: Boolean,
                                  settings: CbtExecutionSettings,
                                  listener: ExternalSystemTaskNotificationListener): DataNode[ProjectData] = {
    val projectPath = settings.realProjectPath
    val root = new File(projectPath)
    println("Cbt resolver called")

    val xml = XML.loadString(CBT.runAction(Seq("buildInfoXml"), root, Some((id, listener))))
    println(xml.toString)
    val r = convert(xml)
    r
  }


  private def convert(project: Node) =
    convertProject(project)

  private def convertProject(project: Node) = {
    val projectData = new ProjectData(CbtProjectSystem.Id,
      (project \ "name").text,
      (project \ "root").text,
      (project \ "root").text)

    val projectNode = new DataNode[ProjectData](ProjectKeys.PROJECT, projectData, null)

    val libraries = (project \ "libraries" \ "library")
      .map(createLibraryData)
      .map(l => l.getExternalName -> l)
      .toMap
    libraries.values
      .map(createLibraryNode(projectNode))
      .foreach(projectNode.addChild)

    val modules = (project \ "modules" \ "module")
      .map(createModuleData)
      .map(m => m.getExternalName -> m)
      .toMap
    (project \ "modules" \ "module")
      .map(m => createModuleNode(projectNode, libraries, modules, modules((m \ "name").text.trim), m))
      .foreach(projectNode.addChild)

    projectNode.addChild(createProjectData(projectNode, project))
    projectNode
  }

  private def createProjectData(projectDateNode: DataNode[ProjectData], node: Node) =
    new DataNode(CbtProjectData.Key, new CbtProjectData(), projectDateNode)

  private def createExtModuleData(moduleDataNode: DataNode[ModuleData], node: Node) = {
    val scalacClasspath = (node \ "classpaths" \ "classpathItem")
      .map(t => new File(t.text.trim))
    new DataNode(CbtModuleExtData.Key,
      new CbtModuleExtData(Version((node \ "scalaVersion").text.trim), scalacClasspath), moduleDataNode)
  }

  private def createModuleData(module: Node) =
    new ModuleData((module \ "name").text,
      CbtProjectSystem.Id,
      "JAVA_MODULE",
      (module \ "name").text,
      (module \ "root").text,
      (module \ "root").text)

  private def createModuleNode(parent: DataNode[_], libraries: Map[String, LibraryData],
                               modules: Map[String, ModuleData], moduleData: ModuleData, module: Node) = {


    val moduleNode = new DataNode(ProjectKeys.MODULE, moduleData, parent)
    moduleNode.addChild(createContentRoot(module, moduleNode))
    (module \ "libraryDependencies" \ "libraryDependency")
      .map(d => createLibraryDependencyNode(moduleNode, libraries(d.text.trim)))
      .foreach(moduleNode.addChild)
    Seq(module \ "moduleDependencies" \ "moduleDependency", module \ "parentBuild")
      .flatten
      .map(m => modules(m.text.trim))
      .map(createModuleDependency(moduleNode))
      .foreach(moduleNode.addChild)
    moduleNode.addChild(createExtModuleData(moduleNode, module))
    moduleNode
  }

  private def createModuleDependency(parent:DataNode[ModuleData])(moduleData: ModuleData) = {
    val moduleDependencyData = new ModuleDependencyData(parent.getData, moduleData)
    new DataNode(ProjectKeys.MODULE_DEPENDENCY, moduleDependencyData, parent)
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

  private def createLibraryDependencyNode(parent: DataNode[ModuleData], libraryData: LibraryData) = {
    val dependencyData = new LibraryDependencyData(parent.getData, libraryData, LibraryLevel.PROJECT)
    new DataNode(ProjectKeys.LIBRARY_DEPENDENCY, dependencyData, parent)
  }

  private def createLibraryData(library: Node) = {
    val libraryData = new LibraryData(CbtProjectSystem.Id, (library \ "name").text.trim)
    (library \ "jars" \ "jar")
      .map(_.text.trim)
      .foreach(libraryData.addPath(LibraryPathType.BINARY, _))
    libraryData
  }

  private def createLibraryNode(parent: DataNode[_])(libraryData: LibraryData) = {
    new DataNode(ProjectKeys.LIBRARY, libraryData, parent)
  }

  override def cancelTask(taskId: ExternalSystemTaskId, listener: ExternalSystemTaskNotificationListener): Boolean = true
}

