package org.jetbrains.bsp.project.test

import ch.epfl.scala.bsp4j._
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.{ProgressIndicator, Task}
import com.intellij.openapi.project.Project
import org.jetbrains.bsp.data.BspMetadata
import org.jetbrains.bsp.protocol.BspJob.CancelCheck
import org.jetbrains.bsp.protocol.session.BspSession.{BspServer, BuildServerInfo}
import org.jetbrains.bsp.protocol.{BspCommunication, BspJob}
import org.jetbrains.bsp.{BspBundle, BspErrorMessage}
import org.jetbrains.plugins.scala.build.BuildToolWindowReporter.CancelBuildAction
import org.jetbrains.plugins.scala.build.{BuildMessages, BuildReporter, BuildToolWindowReporter}

import java.nio.file.Paths
import java.util.UUID
import java.util.concurrent.CompletableFuture
import scala.concurrent.Promise
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}


class FetchScalaTestClassesTask(project: Project,
                                onOK: java.util.List[ScalaTestClassesItem] => Unit,
                                onErr: Throwable => Unit
                               ) extends Task.Modal(project, BspBundle.message("bsp.test.loading"), true) {

  override def run(indicator: ProgressIndicator): Unit = {
    val text = BspBundle.message("bsp.test.fetching.scala.test.classes")
    indicator.setText(text)
    val cancelPromise: Promise[Unit] = Promise()
    val cancelCheck = new CancelCheck(cancelPromise, indicator)
    val cancelAction = new CancelBuildAction(cancelPromise)
    implicit val reporter: BuildReporter = new BuildToolWindowReporter(project, BuildMessages.randomEventId, text, cancelAction)
    reporter.start()

    val targetsByWorkspace = ModuleManager.getInstance(project).getModules.toList
      .flatMap { module =>
        val targets = BspMetadata.get(project, module).toOption.toList.flatMap(_.targetIds.asScala)
        val modulePath = ExternalSystemApiUtil.getExternalProjectPath(module)
        val workspacePath = Paths.get(modulePath)
        targets.map(t => (workspacePath, new BuildTargetIdentifier(t.toString)))
      }
      .groupBy(_._1)
      .view
      .mapValues(_.map(_._2))
      .mapValues { targets =>
        val p = new ScalaTestClassesParams(targets.asJava)
        p.setOriginId(UUID.randomUUID().toString)
        p
      }

    val jobs = targetsByWorkspace.map { case (workspace, params) =>
      BspCommunication.forWorkspace(workspace.toFile, project)
        .run(
          requestTestClasses(params)(_,_),
          _ => {},
          _ => {}
        )
    }

    // blocking wait
    val results = jobs.map(BspJob.waitForJobCancelable(_, cancelCheck))

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

  private def requestTestClasses(
    params: ScalaTestClassesParams
  )(bsp: BspServer, serverInfo: BuildServerInfo): CompletableFuture[ScalaTestClassesResult] =
    if (!serverInfo.capabilities.getTestProvider.getLanguageIds.isEmpty) bsp.buildTargetScalaTestClasses(params)
    else {
      val result = new CompletableFuture[ScalaTestClassesResult]()
      result.completeExceptionally(BspErrorMessage(BspBundle.message("bsp.test.server.does.not.support.testing")))
      result
    }

}
