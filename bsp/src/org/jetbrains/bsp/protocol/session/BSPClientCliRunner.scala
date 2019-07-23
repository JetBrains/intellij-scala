package org.jetbrains.bsp.protocol.session

import java.io.File
import java.util.UUID
import java.util.concurrent.CompletableFuture

import ch.epfl.scala.bsp4j
import ch.epfl.scala.bsp4j._
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.model.task.{ExternalSystemTaskId, ExternalSystemTaskNotificationEvent, ExternalSystemTaskNotificationListener, ExternalSystemTaskType}
import org.jetbrains.bsp.project.resolver.BspProjectResolver
import org.jetbrains.bsp.protocol.BspCommunication
import org.jetbrains.bsp.protocol.BspNotifications.BspNotification
import org.jetbrains.bsp.protocol.session.BspSession.BspServer
import org.jetbrains.bsp.settings.BspExecutionSettings

import scala.collection.JavaConverters._
import scala.concurrent.Await
import scala.concurrent.duration._


class DummyListener extends ExternalSystemTaskNotificationListener {
  override def onQueued(externalSystemTaskId: ExternalSystemTaskId, s: String): Unit = {}

  override def onStart(externalSystemTaskId: ExternalSystemTaskId): Unit = {}

  override def onStatusChange(externalSystemTaskNotificationEvent: ExternalSystemTaskNotificationEvent): Unit = {}

  override def onTaskOutput(externalSystemTaskId: ExternalSystemTaskId, s: String, b: Boolean): Unit = {}

  override def onEnd(externalSystemTaskId: ExternalSystemTaskId): Unit = {}

  override def onSuccess(externalSystemTaskId: ExternalSystemTaskId): Unit = {}

  override def onFailure(externalSystemTaskId: ExternalSystemTaskId, e: Exception): Unit = {}

  override def beforeCancel(externalSystemTaskId: ExternalSystemTaskId): Unit = {}

  override def onCancel(externalSystemTaskId: ExternalSystemTaskId): Unit = {}
}

object BSPClientCliRunner {

  val path = "/Users/sme/bloopmill/"

  def logProcess(str: String): Unit = {
    if (str.endsWith(System.lineSeparator()))
      print(str)
    else println(str)
  }

  def logNotification(not: BspNotification): Unit = {
    println(not)
  }

  private def compileRequest(targets: List[BuildTargetIdentifier])(server: BuildServer): CompletableFuture[CompileResult] = {
    val params = new bsp4j.CompileParams(targets.asJava)
    params.setOriginId(UUID.randomUUID().toString)
    server.buildTargetCompile(params)
  }

  private def buildTargets(server: BspServer): CompletableFuture[WorkspaceBuildTargetsResult] = {
    server.workspaceBuildTargets()
  }

  def main(args: Array[java.lang.String]): Unit = {
    val bspExecSettings = new BspExecutionSettings(new File(path), {
      import sys.process._
      new File("which bloop" !!)
    })
    val bspComm = BspCommunication.forBaseDir(path, bspExecSettings)
    val taks = ExternalSystemTaskId.create(new ProjectSystemId("BSP", "bsp"), ExternalSystemTaskType.RESOLVE_PROJECT, path)
    new BspProjectResolver().resolveProjectInfo(taks, path, isPreviewMode = false, bspExecSettings, new DummyListener)
    val targets = Await.result(bspComm.run(buildTargets, logNotification, logProcess).future, 10 seconds)
    bspComm.run(compileRequest(targets.getTargets.asScala.map(_.getId).toList), logNotification, logProcess)
  }
}
