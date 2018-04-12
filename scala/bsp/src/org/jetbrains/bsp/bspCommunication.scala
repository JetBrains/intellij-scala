package org.jetbrains.bsp

import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

import ch.epfl.scala.bsp.endpoints
import ch.epfl.scala.bsp.schema.{BuildClientCapabilities, InitializeBuildParams, InitializedBuildParams}
import com.intellij.openapi.components.AbstractProjectComponent
import com.intellij.openapi.project.Project
import com.typesafe.scalalogging.Logger
import monix.eval.Task
import monix.execution.Scheduler
import monix.reactive.Observable
import org.langmeta.jsonrpc.{BaseProtocolMessage, Services}
import org.langmeta.lsp.{LanguageClient, LanguageServer}
import org.scalasbt.ipcsocket.UnixDomainSocket
import org.slf4j.LoggerFactory

import scala.concurrent.duration.FiniteDuration
import scala.sys.process.Process
import scala.util.Random

class BspCommunication(project: Project) extends AbstractProjectComponent(project) {

}

class BspSession(messages: Observable[BaseProtocolMessage],
                 private implicit val client: LanguageClient,
                 initializedBuildParams: InitializeBuildParams,
                 cleanup: Task[Unit]
                ) {

  // TODO should use IDEA logging
  private val logger = Logger(LoggerFactory.getLogger(classOf[BspCommunication]))

  /** Task starts client-server connection and connects message stream.
    * @param services services that are used to listen to build notifications.
    */
  def run[T](services: Services, task: LanguageClient => Task[T])(implicit scheduler: Scheduler): Task[T] = {
    val runningClientServer = startClientServer(services)

    val whenDone = Task {
      logger.info("closing bsp connection")
      runningClientServer.cancel()
    }

    val resultTask = for {
      initResult <- initRequest
      _ = endpoints.Build.initialized.notify(InitializedBuildParams())
      result <- task(client)
    } yield {
      result
    }

    resultTask
      .doOnCancel(whenDone)
      .doOnFinish {
        case Some(err) =>
          logger.error("bsp connection error", err)
          whenDone
        case None => whenDone
      }
  }

  private val initRequest =
    endpoints.Build.initialize.request(initializedBuildParams)

  private def startClientServer(services: Services)(implicit scheduler: Scheduler) = {
    val server = new LanguageServer(messages, client, services, scheduler, logger)
    server.startTask
      .doOnFinish { errOpt =>
        for {
          cleaned <- cleanup
        } yield {
          logger.info("client/server closed")
          errOpt.foreach { err =>
            logger.info(s"client/server closed with error: $err")
          }
        }
      }
      .doOnCancel(cleanup)
      .runAsync
  }

}

object BspCommunication {


  def prepareSession(base: File)(implicit scheduler: Scheduler): Task[BspSession] = {

    // TODO should use IDEA logging
    val logger = Logger(LoggerFactory.getLogger(classOf[BspCommunication]))

    val id = java.lang.Long.toString(Random.nextLong(), Character.MAX_RADIX)

    // TODO support windows pipes and tcp as well as sockets
    val sockdir = Files.createTempDirectory("bsp-")
    val sockfile = sockdir.resolve(s"$id.socket")
    sockfile.toFile.deleteOnExit()
    logger.info(s"The socket file is ${sockfile.toAbsolutePath}")

    // TODO abstract build tool specific logic
    val bloopConfigDir = new File(base, ".bloop").getCanonicalFile
    assert(bloopConfigDir.exists())
    val bspCommand = s"bloop bsp --protocol local --socket $sockfile --verbose"

    // TODO kill bloop process on cancel / error?
    def runBloop = Process(bspCommand, base).run()

    val initParams = InitializeBuildParams(
      rootUri = base.toString,
      Some(BuildClientCapabilities(List("scala")))
    )

    def initSession = {
      val socket = new UnixDomainSocket(sockfile.toString)
      val client: LanguageClient = new LanguageClient(socket.getOutputStream, logger)
      val messages = BaseProtocolMessage.fromInputStream(socket.getInputStream)
      new BspSession(messages, client, initParams, cleanup(socket, sockfile.toFile))
    }

    def cleanup(socket: UnixDomainSocket, socketFile: File): Task[Unit] =
      Task.eval {
        socket.close()
        socket.shutdownInput()
        socket.shutdownOutput()
        if (socketFile.isFile) socketFile.delete()
      }

    Task(runBloop).flatMap(_ =>
      Task(initSession).delayExecution(FiniteDuration(250, TimeUnit.MILLISECONDS)))
  }

}
