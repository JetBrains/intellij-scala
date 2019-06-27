package org.jetbrains.bsp.project

import java.io.File
import java.util

import com.intellij.compiler.impl.CompilerUtil
import com.intellij.openapi.compiler.CompilerPaths
import com.intellij.openapi.externalSystem.model.project.{ExternalSystemSourceType, ModuleData}
import com.intellij.openapi.externalSystem.model.{DataNode, ProjectKeys}
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.util.{ExternalSystemApiUtil => ES}
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.task._
import org.jetbrains.bsp.BSP
import org.jetbrains.bsp.data.BspMetadata
import org.jetbrains.bsp.settings.BspExecutionSettings
import org.jetbrains.plugins.scala.extensions

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

    def onComplete(): Unit = {
      val modules = validTasks.map(_.getModule).toArray
      val outputRoots = CompilerPaths.getOutputPaths(modules)
      refreshRoots(project, outputRoots)
    }

    // TODO save only documents in affected targets?
    extensions.invokeAndWait {
      FileDocumentManager.getInstance().saveAllDocuments()
    }
    val bspTask = new BspTask(project, targets, Option(projectTaskNotification), onComplete)
    ProgressManager.getInstance().run(bspTask)
  }

  // remove this if/when external system handles this refresh on its own
  private def refreshRoots(project: Project, outputRoots: Array[String]): Unit = {

    // simply refresh all the source roots to catch any generated files
    val info = ProjectDataManager.getInstance().getExternalProjectData(project, BSP.ProjectSystemId, project.getBasePath)
    val allSourceRoots = ES.findAllRecursively(info.getExternalProjectStructure, ProjectKeys.CONTENT_ROOT)
    val generatedSourceRoots = allSourceRoots.asScala.flatMap { node =>
      val data = node.getData
      // bsp-side generated sources are still imported as regular sources
      val generated = data.getPaths(ExternalSystemSourceType.SOURCE_GENERATED).asScala
      val regular = data.getPaths(ExternalSystemSourceType.SOURCE).asScala
      generated ++ regular
    }.map(_.getPath).toSeq.distinct

    val toRefresh = generatedSourceRoots ++ outputRoots

    CompilerUtil.refreshOutputRoots(toRefresh.asJavaCollection)
    val toRefreshFiles = toRefresh.map(new File(_)).asJava
    LocalFileSystem.getInstance().refreshIoFiles(toRefreshFiles, true, true, null)
  }
}
