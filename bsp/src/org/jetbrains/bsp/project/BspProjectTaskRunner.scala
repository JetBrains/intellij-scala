package org.jetbrains.bsp.project

import java.util

import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.{DataNode, ProjectKeys}
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.util.{ExternalSystemApiUtil => ES}
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.task._
import org.jetbrains.bsp.BSP
import org.jetbrains.bsp.data.BspMetadata
import org.jetbrains.bsp.settings.BspExecutionSettings
import org.jetbrains.ide.PooledThreadExecutor

import scala.collection.JavaConverters._


class BspProjectTaskRunner extends ProjectTaskRunner {

  override def canRun(projectTask: ProjectTask): Boolean = projectTask match {
    case task: ModuleBuildTask =>
      val module = task.getModule
      val moduleType = ModuleType.get(module)
      moduleType match {
        case _ : BspSyntheticModuleType => false
        case _ => ES.isExternalSystemAwareModule(BSP.ProjectSystemId, module)
      }
    case _: ExecuteRunConfigurationTask => false // TODO support bsp run configs
    case _ => false
  }

  override def run(project: Project,
                   projectTaskContext: ProjectTaskContext,
                   projectTaskNotification: ProjectTaskNotification,
                   tasks: util.Collection[_ <: ProjectTask]): Unit = {

    val validTasks = tasks.asScala.collect {
      case task: ModuleBuildTask => task
    }

    val dataManager = ProjectDataManager.getInstance()

    val targets = validTasks.flatMap { task =>
      val moduleId = ES.getExternalProjectId(task.getModule)

      def predicate(node: DataNode[ModuleData]) = node.getData.getId == moduleId
      // TODO all these options fail silently. collect errors and report something
      val targetIds = for {
        projectInfo <- Option(dataManager.getExternalProjectData(project, BSP.ProjectSystemId, project.getBasePath))
        projectStructure <- Option(projectInfo.getExternalProjectStructure)
        moduleDataNode <- Option(ES.find(projectStructure, ProjectKeys.MODULE, predicate))
        metadata <- Option(ES.find(moduleDataNode, BspMetadata.Key))
      } yield {
        metadata.getData.targetIds.asScala.toList
      }

      targetIds.getOrElse(List.empty)
    }

    // TODO save only documents in affected targets?
    FileDocumentManager.getInstance().saveAllDocuments()
    val executionSettings = BspExecutionSettings.executionSettingsFor(project, project.getBasePath)
    val bspTask = new BspTask(project, executionSettings, targets, Option(projectTaskNotification))
    ProgressManager.getInstance().run(bspTask)
  }
}
