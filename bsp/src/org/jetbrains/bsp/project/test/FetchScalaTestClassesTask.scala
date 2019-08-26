package org.jetbrains.bsp.project.test

import java.util.UUID
import java.util.concurrent.TimeUnit

import ch.epfl.scala.bsp4j.{BuildTargetIdentifier, ScalaTestClassesParams, ScalaTestClassesResult}
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.{ProcessCanceledException, ProgressIndicator, Task}
import com.intellij.openapi.project.Project
import org.jetbrains.bsp.BspUtil._
import org.jetbrains.bsp.data.BspMetadata
import org.jetbrains.bsp.protocol.{BspCommunicationService, BspJob}

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, TimeoutException}
import scala.util.{Failure, Success}


class FetchScalaTestClassesTask(proj: Project,
                                onOK: ScalaTestClassesResult => Unit,
                                onErr: Throwable => Unit
                               ) extends Task.Modal(proj, "Loading", true) {

  override def run(indicator: ProgressIndicator): Unit = {
    indicator.setText("Fetching Scala test classes from BSP server");
    val targets = ModuleManager.getInstance(proj).getModules.toList
      .flatMap(BspMetadata.get(proj, _))
      .flatMap(x => x.targetIds.asScala)
      .map(uri => new BuildTargetIdentifier(uri.toString))
    val testClassesParams = {
      val p = new ScalaTestClassesParams(targets.asJava)
      p.setOriginId(UUID.randomUUID().toString)
      p
    }
    val task = BspCommunicationService.getInstance
      .communicate(proj)
      .run(bsp => bsp.buildTargetScalaTestClasses(testClassesParams).catchBspErrors, _ => {}, _ => {})
    val result = waitForJobCancelable(task, indicator)
    result match {
      case Success(value) => onOK(value)
      case Failure(exception) => onErr(exception)
    }
  }

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
