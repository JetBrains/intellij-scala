package org.jetbrains.bsp.project

import com.intellij.compiler.impl.{CompileContextImpl, CompilerUtil, ProjectCompileScope}
import com.intellij.compiler.progress.CompilerTask
import com.intellij.openapi.compiler.{CompilerPaths, CompilerTopics}
import com.intellij.openapi.externalSystem.model.project.{ExternalSystemSourceType, ModuleData}
import com.intellij.openapi.externalSystem.model.{DataNode, ProjectKeys}
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.util.{ExternalSystemApiUtil => ES}
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.{ModuleManager, ModuleType}
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.task._
import org.jetbrains.bsp.data.BspMetadata
import org.jetbrains.bsp.project.BspTask.BspTarget
import org.jetbrains.bsp.project.test.BspTestRunConfiguration
import org.jetbrains.bsp.{BSP, BspBundle, BspUtil}
import org.jetbrains.concurrency.{AsyncPromise, Promise}
import org.jetbrains.plugins.scala.build.BuildMessages
import org.jetbrains.plugins.scala.extensions
import org.jetbrains.plugins.scala.project.ModuleExt

import java.io.File
import java.nio.file.Paths
import java.util.Collections
import scala.concurrent.ExecutionContext.Implicits.global
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success}


class BspProjectTaskRunner extends ProjectTaskRunner {

  override def canRun(projectTask: ProjectTask): Boolean = projectTask match {
    case task: ModuleBuildTask =>
      val module = task.getModule
      val moduleType = ModuleType.get(module)
      moduleType match {
        case _ => BspUtil.isBspModule(module)
      }
    case t: ExecuteRunConfigurationTask => t.getRunProfile match {
      case _: BspTestRunConfiguration => true
      case _ => false
    }
    case _ => false
  }


  override def run(project: Project,
                   projectTaskContext: ProjectTaskContext,
                   tasks: ProjectTask*): Promise[ProjectTaskRunner.Result] = {

    val validTasks = tasks.collect {
      case task: ModuleBuildTask => task
    }

    val targetsAndRebuild = validTasks.flatMap { task =>
      val moduleId = ES.getExternalProjectId(task.getModule)

      // TODO all these Options fail silently. collect errors and report something
      val targetIds = for {
        projectPath <- Option(ES.getExternalProjectPath(task.getModule))
        projectData <- Option(ES.findProjectNode(project, BSP.ProjectSystemId, projectPath))
        moduleDataNode <- Option(ES.findChild(
          projectData, ProjectKeys.MODULE,
          (node: DataNode[ModuleData]) => node.getData.getId == moduleId))
        metadata <- Option(ES.find(moduleDataNode, BspMetadata.Key))
      } yield {
        val data = metadata.getData
        val workspaceUri = Paths.get(projectPath).toUri
        data.targetIds.asScala.map(id => BspTarget(workspaceUri, id.uri)).toList
      }

      targetIds.getOrElse(List.empty)
        .map(id => (id, ! task.isIncrementalBuild))
    }

    val targets = targetsAndRebuild.map(_._1)
    val targetsToClean = targetsAndRebuild.filter(_._2).map(_._1)

    // TODO save only documents in affected targets?
    extensions.invokeAndWait {
      FileDocumentManager.getInstance().saveAllDocuments()
    }
    val promiseResult = new AsyncPromise[ProjectTaskRunner.Result]

    val bspTask = new BspTask(project, targets, targetsToClean)

    bspTask.resultFuture.foreach { _ =>
      refreshRoots(project)
    }

    bspTask.resultFuture.onComplete { messages =>

      val session = new CompilerTask(project, BspBundle.message("bsp.runner.hack.notify.completed.bsp.build"), false, false, false, false)
      val scope = new ProjectCompileScope(project)
      val context = new CompileContextImpl(project, session, scope, false, false)
      val pub = project.getMessageBus.syncPublisher(CompilerTopics.COMPILATION_STATUS)

      messages match {
        case Success(messages) =>
          promiseResult.setResult(messages.toTaskRunnerResult)

          // Auto-test needs checks if at least one path was process (and this can be any path)
          pub.fileGenerated("", "")
          pub.compilationFinished(
            messages.status == BuildMessages.Canceled,
            messages.errors.size, messages.warnings.size, context)
        case Failure(exception) =>
          promiseResult.setError(exception)
          pub.automakeCompilationFinished(1, 0, context)
      }
    }


    ProgressManager.getInstance().run(bspTask)

    promiseResult
  }

  // remove this if/when external system handles this refresh on its own
  private def refreshRoots(project: Project): Unit = {

    // simply refresh all the source roots to catch any generated files
    val info = Option(ProjectDataManager.getInstance().getExternalProjectData(project, BSP.ProjectSystemId, project.getBasePath))
    val allSourceRoots = info
      .map { info => ES.findAllRecursively(info.getExternalProjectStructure, ProjectKeys.CONTENT_ROOT) }
      .getOrElse(Collections.emptyList())
    val generatedSourceRoots = allSourceRoots.asScala.flatMap { node =>
      val data = node.getData
      // bsp-side generated sources are still imported as regular sources
      val generated = data.getPaths(ExternalSystemSourceType.SOURCE_GENERATED).asScala
      val regular = data.getPaths(ExternalSystemSourceType.SOURCE).asScala
      generated ++ regular
    }.map(_.getPath).toSeq.distinct

    // Because we don't have an exact way of knowing which modules have been affected, we need to refresh the output
    // directories of all modules in the project. Otherwise, we run the risk that the Run Configuration order
    // enumerator will not see all output directories in the VFS and will not put them on the runtime classpath.
    // In Gradle, affected modules are collected using an injected Gradle script, which tracks which modules are
    // affected by a build command.
    // https://github.com/JetBrains/intellij-community/blob/bf3083ca66771e038eb1c64128b4e508f52acfad/plugins/gradle/java/src/execution/build/GradleProjectTaskRunner.java#L60
    val outputRoots = {
      val allModules = ModuleManager.getInstance(project).getModules.filterNot(_.hasBuildModuleType)
      CompilerPaths.getOutputPaths(allModules)
    }

    val toRefresh = generatedSourceRoots ++ outputRoots

    CompilerUtil.refreshOutputRoots(toRefresh.asJavaCollection)

    // This is most likely not necessary. Gradle only invokes `CompilerUtil.refreshOutputRoots`.
    // Recursively refreshing the output directories is comparatively expensive. It is enough for the Run Configuration
    // order enumerator to just refresh the output directories without their children, but we don't have tests in place
    // in order to be more confident in this change.
    // https://github.com/JetBrains/intellij-community/blob/bf3083ca66771e038eb1c64128b4e508f52acfad/plugins/gradle/java/src/execution/build/GradleProjectTaskRunner.java#L174-L176
    val toRefreshFiles = toRefresh.map(new File(_)).asJava
    LocalFileSystem.getInstance().refreshIoFiles(toRefreshFiles, true, true, null)
  }
}
