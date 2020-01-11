package org.jetbrains.bsp.project.test

import java.nio.file.Paths
import java.util.UUID
import java.util.concurrent.{CompletableFuture, TimeUnit}

import ch.epfl.scala.bsp4j.{BuildServerCapabilities, BuildTargetIdentifier, ScalaTestClassesItem, ScalaTestClassesParams, ScalaTestClassesResult}
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.{ProcessCanceledException, ProgressIndicator, Task}
import com.intellij.openapi.project.Project
import org.jetbrains.bsp.BspErrorMessage
import org.jetbrains.bsp.BspUtil._
import org.jetbrains.bsp.data.BspMetadata
import org.jetbrains.bsp.protocol.session.BspSession.BspServer
import org.jetbrains.bsp.protocol.{BspCommunication, BspCommunicationService, BspJob}
import org.jetbrains.plugins.scala.build.{BuildMessages, BuildToolWindowReporter}

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, TimeoutException}
import scala.util.{Failure, Success, Try}


class FetchScalaTestClassesTask(project: Project,
                                onOK: java.util.List[ScalaTestClassesItem] => Unit,
                                onErr: Throwable => Unit
                               ) extends Task.Modal(project, "Loading", true) {

  override def run(indicator: ProgressIndicator): Unit = {
    val text = "Fetching Scala test classes from BSP server"
    indicator.setText(text)
    val reporter = new BuildToolWindowReporter(project, BuildMessages.randomEventId, text)
    reporter.start()

    val targetsByWorkspace = ModuleManager.getInstance(project).getModules.toList
      .flatMap { module =>
        val targets = BspMetadata.get(project, module).toList.flatMap(_.targetIds.asScala)
        val modulePath = ExternalSystemApiUtil.getExternalProjectPath(module)
        val workspacePath = Paths.get(modulePath)
        targets.map(t => (workspacePath, new BuildTargetIdentifier(t.toString)))
      }
      .groupBy(_._1)
      .mapValues(_.map(_._2))
      .mapValues { targets =>
        val p = new ScalaTestClassesParams(targets.asJava)
        p.setOriginId(UUID.randomUUID().toString)
        p
      }

    val jobs = targetsByWorkspace.map { case (workspace, params) =>
      BspCommunication.forWorkspace(workspace.toFile)
        .run(
          requestTestClasses(params)(_,_),
          _ => {},
          reporter,
          _ => {}
        )
    }

    // blocking wait
    val results = jobs.map(waitForJobCancelable(_, indicator))

    val items = results
      .foldLeft(Try(List.empty[ScalaTestClassesItem])) {
        case (results, Success(res)) =>
          results.map(_ ++ res.getItems.asScala)
        case (_, Failure(x)) =>
          Failure(x)
      }

    items match {
      case Success(res) =>
        reporter.finish(BuildMessages.empty.status(BuildMessages.OK))
        onOK(res.asJava)
      case Failure(x) =>
        reporter.finishWithFailure(x)
        onErr(x)
    }
  }

  private def requestTestClasses(params: ScalaTestClassesParams)(bsp: BspServer, capabilities: BuildServerCapabilities) =
  if (! capabilities.getTestProvider.getLanguageIds.isEmpty)
    bsp.buildTargetScalaTestClasses(params).catchBspErrors
  else
    CompletableFuture.completedFuture[Try[ScalaTestClassesResult]](
      Failure[ScalaTestClassesResult](BspErrorMessage("server does not support testing"))
    )

  // TODO get rid of this duplicated code
  @tailrec private def waitForJobCancelable[R](job: BspJob[R], indicator: ProgressIndicator): R = {
    try {
      indicator.checkCanceled()
      Await.result(job.future, Duration(300, TimeUnit.MILLISECONDS))
    } catch {
      case _: TimeoutException => waitForJobCancelable(job, indicator)
      case cancel: ProcessCanceledException =>
        job.cancel()
        throw cancel
    }
  }

}
