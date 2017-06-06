package org.jetbrains.plugins.cbt

import java.io.File

import com.intellij.openapi.externalSystem.model.project._
import com.intellij.openapi.externalSystem.model.task.{ExternalSystemTaskId, ExternalSystemTaskNotificationListener}
import com.intellij.openapi.externalSystem.model.{DataNode, ProjectKeys}
import com.intellij.openapi.externalSystem.service.project.ExternalSystemProjectResolver
import org.jetbrains.plugins.cbt.project.CbtProjectSystem
import org.jetbrains.plugins.cbt.project.settings.CbtExecutionSettings

class CbtProjectResolver extends ExternalSystemProjectResolver[CbtExecutionSettings] {


  override def resolveProjectInfo(id: ExternalSystemTaskId,
                                  projectPath: String,
                                  isPreviewMode: Boolean,
                                  settings: CbtExecutionSettings,
                                  listener: ExternalSystemTaskNotificationListener): DataNode[ProjectData] = {
    val projectPath = settings.realProjectPath

    println("Cbt resolver called")

    val projectName = new File(settings.realProjectPath).getName
    val projectData = new ProjectData(CbtProjectSystem.Id, projectName, projectPath, projectPath)
    val projectDataNode = new DataNode[ProjectData](ProjectKeys.PROJECT, projectData, null)


    projectDataNode
      .createChild(ProjectKeys.CONTENT_ROOT, new ContentRootData(CbtProjectSystem.Id, projectPath))

    createModules(projectPath, projectDataNode)
      .foreach(projectDataNode.addChild)
    projectDataNode
  }

  private def createModules(projectPath: String, parent: DataNode[ProjectData]) = {
    def createModuleNode(path: String, name: String, parent: DataNode[_]) = {
      val moduleData = new DataNode(ProjectKeys.MODULE,
        new ModuleData(name, CbtProjectSystem.Id, "JAVA_MODULE", name, path, path), parent)
      moduleData.createChild(ProjectKeys.CONTENT_ROOT, new ContentRootData(CbtProjectSystem.Id, path))
      moduleData
    }

    val projectName = new File(projectPath).getName
    val buildPath = new File(projectPath, "build").getPath
    val rootModule = createModuleNode(projectPath, projectName, parent)
    rootModule.addChild(createModuleNode(buildPath, "build", rootModule))
    rootModule.createChild(ProjectKeys.LIBRARY_DEPENDENCY, new LibraryDependencyData(rootModule.getData,
      new LibraryData(CbtProjectSystem.Id, "org.scala-lang:scala-library:2.12.2"), LibraryLevel.PROJECT))
    //    rootModule.createChild(ProjectKeys.)

    Seq(rootModule)
  }

  override def cancelTask(taskId: ExternalSystemTaskId, listener: ExternalSystemTaskNotificationListener): Boolean = true
}

