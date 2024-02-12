package org.jetbrains.bsp.project

import com.intellij.compiler.impl.{CompileContextImpl, ProjectCompileScope}
import com.intellij.compiler.progress.CompilerTask
import com.intellij.openapi.compiler.CompilerTopics
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.{DataNode, ProjectKeys}
import com.intellij.openapi.externalSystem.util.{ExternalSystemApiUtil => ES}
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.progress.{EmptyProgressIndicator, ProgressManager}
import com.intellij.openapi.project.Project
import com.intellij.task._
import org.jetbrains.bsp.data.BspMetadata
import org.jetbrains.bsp.project.BspTask.BspTarget
import org.jetbrains.bsp.project.test.BspTestRunConfiguration
import org.jetbrains.bsp.{BSP, BspBundle, BspUtil}
import org.jetbrains.concurrency.{AsyncPromise, Promise}
import org.jetbrains.plugins.scala.build.BuildMessages
import org.jetbrains.plugins.scala.extensions

import java.nio.file.Paths
import scala.concurrent.ExecutionContext.Implicits.global
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success}

class BspProjectTaskRunner(arguments: Option[CustomTaskArguments]) extends ProjectTaskRunner {

  def this() = this(None)

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

    if (arguments.isEmpty) {
      // TODO save only documents in affected targets?
      extensions.invokeAndWait {
        FileDocumentManager.getInstance().saveAllDocuments()
      }
    }
    val promiseResult = new AsyncPromise[ProjectTaskRunner.Result]

    val bspTask = new BspTask(project, targets, targetsToClean, arguments)

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

    val progressManager = ProgressManager.getInstance()
    if (arguments.nonEmpty) {
      progressManager.runProcessWithProgressAsynchronously(bspTask, new EmptyProgressIndicator())
    } else {
      progressManager.run(bspTask)
    }

    promiseResult
  }
}
