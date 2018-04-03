package org.jetbrains.bsp

import java.io.File
import java.nio.file.Files

import ch.epfl.scala.bsp.endpoints
import ch.epfl.scala.bsp.schema.{BuildClientCapabilities, InitializeBuildParams, InitializedBuildParams}
import com.intellij.openapi.components.AbstractProjectComponent
import com.intellij.openapi.project.Project
import com.typesafe.scalalogging.Logger
import monix.eval.Task
import monix.execution.{CancelableFuture, ExecutionModel, Scheduler}
import org.jetbrains.ide.PooledThreadExecutor
import org.langmeta.jsonrpc.{BaseProtocolMessage, Services}
import org.langmeta.lsp.{LanguageClient, LanguageServer}
import org.scalasbt.ipcsocket.UnixDomainSocket
import org.slf4j.LoggerFactory

import scala.concurrent.Promise
import scala.sys.process.{Process, ProcessLogger}
import scala.util.{Random, Success}

class BspCommunication(project: Project) extends AbstractProjectComponent(project) {

}

// TODO session should be aware of connected state somehow
class BspSession(runningClientServer: CancelableFuture[_], val client: LanguageClient) {
  def close(): Unit = runningClientServer.cancel()
}

object BspCommunication {


  def initialize(base: File): BspSession = {

    val logger = Logger(LoggerFactory.getLogger(classOf[BspCommunication]))

    val id = java.lang.Long.toString(Random.nextLong(), Character.MAX_RADIX)

    // TODO support windows pipes and tcp as well as sockets
    val sockdir = Files.createTempDirectory("bsp-")
    val sockfile = sockdir.resolve(s"$id.socket")
    sockfile.toFile.deleteOnExit()

    // TODO abstract build tool specific logic
    val bloopConfigDir = new File(base, ".bloop-config").getCanonicalFile
    val bspCommand = s"bloop bsp --protocol local --socket $sockfile --verbose"

    val bspReady = Promise[Unit]()
    val proclog = ProcessLogger.apply { msg =>
      if (!bspReady.isCompleted && msg.contains(id)) bspReady.complete(Success(()))
    }
    // TODO kill bloop process on cancel / error
    val runBloop = Task.eval { Process(bspCommand).run(proclog) }
    val bspReadyTask = Task.fromFuture(bspReady.future)


    val socket = new UnixDomainSocket(sockfile.toString)
    implicit val client: LanguageClient = new LanguageClient(socket.getOutputStream, logger)
    val messages = BaseProtocolMessage.fromInputStream(socket.getInputStream)
    val services = Services.empty
    implicit val scheduler: Scheduler = Scheduler(PooledThreadExecutor.INSTANCE, ExecutionModel.AlwaysAsyncExecution)
    val server = new LanguageServer(messages, client, services, scheduler, logger)


    val initializeServerReq = endpoints.Build.initialize.request(
      InitializeBuildParams(
        rootUri = bloopConfigDir.toString,
        Some(BuildClientCapabilities(List("scala")))
      )
    )

    def sendInitializedNotification(): Unit = endpoints.Build.initialized.notify(InitializedBuildParams())

    val initializeSequence = for {
      bloopProcess <- runBloop
      _ <- bspReadyTask
      _ <- server.startTask
      init <- initializeServerReq
    } yield {
      // TODO handle init error response
      init.foreach(_ => sendInitializedNotification())
      init
    }

    def cleanup: Task[Unit] = Task.eval {
      socket.close()
      socket.shutdownInput()
      socket.shutdownOutput()
      sockfile.toFile.delete()
    }

    val initWithCleanup = initializeSequence
      .doOnCancel(cleanup)
      .doOnFinish(_ => cleanup)

    val runningClientServer = initWithCleanup.runAsync(scheduler)

    new BspSession(runningClientServer, client)
  }

}
