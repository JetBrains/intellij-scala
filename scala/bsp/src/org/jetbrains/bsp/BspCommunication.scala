package org.jetbrains.bsp

import java.io.File
import java.nio.file.Files

import ch.epfl.scala.bsp.endpoints
import ch.epfl.scala.bsp.schema.{BuildClientCapabilities, InitializeBuildParams, InitializedBuildParams}
import com.intellij.openapi.components.AbstractProjectComponent
import com.intellij.openapi.project.Project
import com.typesafe.scalalogging.Logger
import monix.eval.Task
import monix.execution.{Cancelable, Scheduler}
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
class BspSession(runningClientServer: Cancelable, val client: LanguageClient) {
  def close(): Unit = runningClientServer.cancel()
}

object BspCommunication {


  def initialize(base: File)(implicit scheduler: Scheduler): Task[BspSession] = {

    // TODO should use IDEA logging
    val logger = Logger(LoggerFactory.getLogger(classOf[BspCommunication]))

    val id = java.lang.Long.toString(Random.nextLong(), Character.MAX_RADIX)

    // TODO support windows pipes and tcp as well as sockets
    val sockdir = Files.createTempDirectory("bsp-")
    val sockfile = sockdir.resolve(s"$id.socket")
    sockfile.toFile.deleteOnExit()

    // TODO abstract build tool specific logic
    val bloopConfigDir = new File(base, ".bloop-config").getCanonicalFile
    assert(bloopConfigDir.exists())
    val bspCommand = s"bloop bsp --protocol local --socket $sockfile --verbose"

    val bspReady = Promise[Unit]()
    val proclog = ProcessLogger.apply { msg =>
      logger.info(s"§ bloop: $msg")
      if (!bspReady.isCompleted && msg.contains(id)) bspReady.complete(Success(()))
    }
    // TODO kill bloop process on cancel / error
    val runBloop = Task.eval { Process(bspCommand, base).run(proclog) }
    val bspReadyTask = Task.fromFuture(bspReady.future)

    val initSocket = Task { new UnixDomainSocket(sockfile.toString) }

    def initServer(socket: UnixDomainSocket) = Task {
      val client: LanguageClient = new LanguageClient(socket.getOutputStream, logger)
      val messages = BaseProtocolMessage.fromInputStream(socket.getInputStream)
      val services = Services.empty
      val server = new LanguageServer(messages, client, services, scheduler, logger)
      (client, server)
    }

    def initializeServerReq(implicit client: LanguageClient) = {
      endpoints.Build.initialize.request(
        InitializeBuildParams(
          rootUri = bloopConfigDir.toString,
          Some(BuildClientCapabilities(List("scala")))
        )
      )
    }

    def sendInitializedNotification(implicit client: LanguageClient): Unit =
      endpoints.Build.initialized.notify(InitializedBuildParams())

    def cleanup(socket: UnixDomainSocket): Task[Unit] = Task.eval {
      logger.warn("cleaning up socket!!")
      socket.close()
      socket.shutdownInput()
      socket.shutdownOutput()
      sockfile.toFile.delete()
    }

    def startClientServer(server: LanguageServer, socket: UnixDomainSocket) =
      server.startTask
        .doOnFinish { errOpt => Task {
          cleanup(socket)
          logger.info("client/server closed")
          errOpt.foreach { err =>
            logger.info(s"client/server closed with error: $err")
          }
        }}
        .doOnCancel(cleanup(socket))
        .runAsync

    val initializeSequence = for {
      bloopProcess <- runBloop
      _ <- bspReadyTask
      socket <- initSocket
      clientServer <- initServer(socket)
      client = clientServer._1
      server = clientServer._2
      runningClientServer = startClientServer(server, socket)
      init <- initializeServerReq(client)
    } yield {
      // TODO handle init error response
      sendInitializedNotification(client)
      new BspSession(runningClientServer, client)
    }

    initializeSequence
  }

}
